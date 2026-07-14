package com.printplatform.security;

import com.printplatform.model.ApiRequestLog;
import com.printplatform.model.User;
import com.printplatform.repository.ApiRequestLogRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Logs one row per /api/** call for the admin traffic dashboard. Runs after JwtAuthFilter
 * in the chain, so SecurityContextHolder already has the resolved principal, if any.
 * Logging failures are swallowed — they must never mask or break the real response.
 */
@Component
public class ApiRequestLoggingFilter extends OncePerRequestFilter {

    private final ApiRequestLogRepository apiRequestLogRepository;

    public ApiRequestLoggingFilter(ApiRequestLogRepository apiRequestLogRepository) {
        this.apiRequestLogRepository = apiRequestLogRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/") || uri.startsWith("/api/analytics/") || uri.equals("/api/admin/traffic");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            try {
                ApiRequestLog log = new ApiRequestLog();
                log.setMethod(request.getMethod());
                log.setPath(request.getRequestURI());
                log.setStatus(response.getStatus());
                log.setDurationMs(System.currentTimeMillis() - start);
                log.setUserId(resolveUserId());
                log.setIp(ClientIpResolver.resolve(request));
                apiRequestLogRepository.save(log);
            } catch (Exception ignored) {
                // Best-effort logging only.
            }
        }
    }

    private UUID resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return null;
    }
}
