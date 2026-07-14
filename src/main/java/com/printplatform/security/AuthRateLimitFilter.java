package com.printplatform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Throttles requests to /api/auth/** and /api/admin/redeem per client IP (fixed
 * window) to blunt credential-stuffing/brute-force attempts against login/register
 * and admin-code guessing against the redeem endpoint.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000;
    // Every this many requests, opportunistically evict windows that have been idle
    // for a full cycle, so the map doesn't grow unbounded under sustained unique-IP traffic.
    private static final long SWEEP_EVERY_N_REQUESTS = 500;

    // Java default doubles as the fallback for tests that construct this filter directly
    // (new AuthRateLimitFilter()), bypassing Spring's @Value injection entirely.
    @Value("${app.rate-limit.auth.max-requests-per-window:10}")
    private int maxRequestsPerWindow = 10;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong requestCount = new AtomicLong();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/auth/") && !uri.equals("/api/admin/redeem");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Window window = windows.computeIfAbsent(clientIp(request), k -> new Window());

        if (window.tooManyRequests(maxRequestsPerWindow)) {
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
     * getRemoteAddr() is the proxy's IP (one shared bucket for everyone), so prefer
     * X-Forwarded-For instead. The *first* hop of that header is attacker-controlled
     * (any client can set it directly), so it must not be trusted for rate-limiting.
     * Render's edge load balancer APPENDS the real connecting IP as the LAST entry
     * rather than replacing the header, so the last hop is the one value in this
     * header the caller cannot forge. Falls back to the socket address when absent.
     */
    private String clientIp(HttpServletRequest request) {
        return ClientIpResolver.resolve(request);
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

        synchronized boolean tooManyRequests(int maxRequestsPerWindow) {
            long now = System.currentTimeMillis();
            if (now - windowStart > WINDOW_MILLIS) {
                windowStart = now;
                count = 0;
            }
            return ++count > maxRequestsPerWindow;
        }
    }
}
