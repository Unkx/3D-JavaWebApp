package com.printplatform.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Caps unauthenticated calls to /api/analytics/pageview per client IP, so the endpoint can't
 * be used to spam-fill the page_view table. Window is generous (unlike AuthRateLimitFilter's
 * login throttle) since normal browsing legitimately fires this once per navigation.
 */
@Component
public class PageViewRateLimiter {

    private static final long WINDOW_MILLIS = 60_000;
    private static final int MAX_PER_WINDOW = 120;
    // Every this many requests, opportunistically evict windows that have been idle
    // for a full cycle, so the map doesn't grow unbounded under sustained unique-IP traffic.
    private static final long SWEEP_EVERY_N_REQUESTS = 500;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong requestCount = new AtomicLong();

    public boolean allow(HttpServletRequest request) {
        String ip = ClientIpResolver.resolve(request);
        Window window = windows.computeIfAbsent(ip, k -> new Window());
        boolean allowed = !window.tooManyRequests();

        if (requestCount.incrementAndGet() % SWEEP_EVERY_N_REQUESTS == 0) {
            evictStaleWindows();
        }

        return allowed;
    }

    private void evictStaleWindows() {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(entry -> entry.getValue().isStale(now));
    }

    private static final class Window {
        private long windowStart = System.currentTimeMillis();
        private int count = 0;

        synchronized boolean isStale(long now) {
            return now - windowStart > WINDOW_MILLIS;
        }

        synchronized boolean tooManyRequests() {
            long now = System.currentTimeMillis();
            if (now - windowStart > WINDOW_MILLIS) {
                windowStart = now;
                count = 0;
            }
            return ++count > MAX_PER_WINDOW;
        }
    }
}
