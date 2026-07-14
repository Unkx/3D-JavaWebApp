package com.printplatform.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the real client IP behind Render's edge load balancer, which APPENDS the
 * connecting IP as the LAST entry of X-Forwarded-For rather than replacing the header —
 * the FIRST hop is attacker-controlled and must never be trusted for rate-limiting or logging.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {}

    public static String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            String last = hops[hops.length - 1].strip();
            if (!last.isEmpty()) {
                return last;
            }
        }
        return request.getRemoteAddr();
    }
}
