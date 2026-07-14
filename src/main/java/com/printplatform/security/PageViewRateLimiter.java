package com.printplatform.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Caps unauthenticated calls to /api/analytics/pageview per client IP, so the endpoint can't
 * be used to spam-fill the page_view table. Window is generous (unlike AuthRateLimitFilter's
 * login throttle) since normal browsing legitimately fires this once per navigation.
 */
@Component
public class PageViewRateLimiter {

    private static final long WINDOW_MILLIS = 60_000;
    private static final int MAX_PER_WINDOW = 120;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public boolean allow(HttpServletRequest request) {
        String ip = ClientIpResolver.resolve(request);
        Window window = windows.computeIfAbsent(ip, k -> new Window());
        return !window.tooManyRequests();
    }

    private static final class Window {
        private long windowStart = System.currentTimeMillis();
        private int count = 0;

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
