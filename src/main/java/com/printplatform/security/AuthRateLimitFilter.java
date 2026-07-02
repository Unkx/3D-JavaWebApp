package com.printplatform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Throttles requests to /api/auth/** per client IP (fixed window) to blunt
 * credential-stuffing and brute-force attempts against login/register.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS_PER_WINDOW = 10;
    private static final long WINDOW_MILLIS = 60_000;
    // Every this many requests, opportunistically evict windows that have been idle
    // for a full cycle, so the map doesn't grow unbounded under sustained unique-IP traffic.
    private static final long SWEEP_EVERY_N_REQUESTS = 500;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong requestCount = new AtomicLong();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Window window = windows.computeIfAbsent(clientIp(request), k -> new Window());

        if (window.tooManyRequests()) {
            response.setStatus(429); // 429 Too Many Requests
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"Zbyt wiele prób logowania. Spróbuj ponownie za chwilę.\"}");
            return;
        }

        if (requestCount.incrementAndGet() % SWEEP_EVERY_N_REQUESTS == 0) {
            evictStaleWindows();
        }

        chain.doFilter(request, response);
    }

    /**
     * Best-effort per-client key. When the app sits behind the frontend/reverse proxy,
     * getRemoteAddr() is the proxy's IP (one shared bucket for everyone), so prefer the
     * first hop of X-Forwarded-For. Falls back to the socket address when absent.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].strip();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }

    private void evictStaleWindows() {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(entry -> entry.getValue().isStale(now));
    }

    /** Resets its counter once WINDOW_MILLIS has elapsed since the first request in the window. */
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
            return ++count > MAX_REQUESTS_PER_WINDOW;
        }
    }
}
