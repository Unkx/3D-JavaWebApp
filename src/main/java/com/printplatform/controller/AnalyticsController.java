package com.printplatform.controller;

import com.printplatform.dto.PageViewRequest;
import com.printplatform.model.User;
import com.printplatform.security.PageViewRateLimiter;
import com.printplatform.service.AnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final PageViewRateLimiter rateLimiter;

    public AnalyticsController(AnalyticsService analyticsService, PageViewRateLimiter rateLimiter) {
        this.analyticsService = analyticsService;
        this.rateLimiter = rateLimiter;
    }

    /** Records one page-view event (public, unauthenticated, rate-limited). Best-effort — never errors out. */
    @PostMapping("/pageview")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void trackPageView(@Valid @RequestBody PageViewRequest request,
                              @AuthenticationPrincipal User user,
                              HttpServletRequest servletRequest) {
        if (!rateLimiter.allow(servletRequest)) {
            return;
        }
        analyticsService.recordPageView(
                request.getPath(),
                user != null ? user.getId() : null,
                request.getSessionId(),
                request.getReferrer());
    }
}
