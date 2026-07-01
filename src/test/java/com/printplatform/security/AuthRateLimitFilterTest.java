package com.printplatform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Plain Mockito-based unit tests for {@link AuthRateLimitFilter}'s fixed-window
 * per-IP throttling — doFilterInternal/shouldNotFilter are invoked directly,
 * bypassing OncePerRequestFilter's public doFilter dispatch machinery.
 */
class AuthRateLimitFilterTest {

    private AuthRateLimitFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new AuthRateLimitFilter();
        chain = mock(FilterChain.class);
    }

    private HttpServletRequest requestFor(String uri, String remoteAddr) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        return request;
    }

    private HttpServletResponse mockResponseCapturingBody(StringWriter bodySink) throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(bodySink));
        return response;
    }

    @Test
    void shouldNotFilterIsTrueForNonAuthPaths() {
        HttpServletRequest request = requestFor("/api/listings/123", "10.0.0.1");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilterIsFalseForAuthPaths() {
        HttpServletRequest request = requestFor("/api/auth/login", "10.0.0.1");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void firstTenRequestsFromSameIpWithinWindowAreAllowedThrough() throws Exception {
        String ip = "192.168.1.50";

        for (int i = 1; i <= 10; i++) {
            HttpServletRequest request = requestFor("/api/auth/login", ip);
            HttpServletResponse response = mock(HttpServletResponse.class);

            filter.doFilterInternal(request, response, chain);

            verify(chain, times(1)).doFilter(request, response);
            verify(response, never()).setStatus(429);
        }
    }

    @Test
    void eleventhRequestFromSameIpWithinWindowIsBlockedWith429() throws Exception {
        String ip = "192.168.1.51";

        for (int i = 1; i <= 10; i++) {
            HttpServletRequest request = requestFor("/api/auth/login", ip);
            HttpServletResponse response = mock(HttpServletResponse.class);
            filter.doFilterInternal(request, response, chain);
        }

        HttpServletRequest eleventhRequest = requestFor("/api/auth/login", ip);
        StringWriter body = new StringWriter();
        HttpServletResponse eleventhResponse = mockResponseCapturingBody(body);

        filter.doFilterInternal(eleventhRequest, eleventhResponse, chain);

        verify(eleventhResponse).setStatus(429);
        verify(eleventhResponse).setContentType("application/json;charset=UTF-8");
        verify(chain, never()).doFilter(eleventhRequest, eleventhResponse);
        assertThat(body.toString()).contains("Zbyt wiele prób logowania");
    }

    @Test
    void differentIpsAreTrackedIndependently() throws Exception {
        String throttledIp = "10.10.10.10";
        String freshIp = "10.10.10.11";

        // Exhaust the window for throttledIp.
        for (int i = 1; i <= 11; i++) {
            HttpServletRequest request = requestFor("/api/auth/login", throttledIp);
            HttpServletResponse response = mock(HttpServletResponse.class);
            when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            filter.doFilterInternal(request, response, chain);
        }

        // A brand new IP should still get its own fresh allowance.
        HttpServletRequest freshRequest = requestFor("/api/auth/login", freshIp);
        HttpServletResponse freshResponse = mock(HttpServletResponse.class);

        filter.doFilterInternal(freshRequest, freshResponse, chain);

        verify(freshResponse, never()).setStatus(429);
        verify(chain).doFilter(freshRequest, freshResponse);
    }

    @Test
    @SuppressWarnings("unchecked")
    void windowResetsOnceWindowMillisHasElapsedSinceFirstRequest() throws Exception {
        String ip = "172.16.0.5";

        // Exhaust the window.
        for (int i = 1; i <= 10; i++) {
            HttpServletRequest request = requestFor("/api/auth/login", ip);
            HttpServletResponse response = mock(HttpServletResponse.class);
            filter.doFilterInternal(request, response, chain);
        }

        // Reach into the filter's internal per-IP window and rewind its start
        // time to simulate WINDOW_MILLIS (60s) having elapsed.
        Map<String, Object> windows = (Map<String, Object>) ReflectionTestUtils.getField(filter, "windows");
        Object window = windows.get(ip);
        assertThat(window).isNotNull();
        ReflectionTestUtils.setField(window, "windowStart", System.currentTimeMillis() - 61_000L);

        HttpServletRequest afterResetRequest = requestFor("/api/auth/login", ip);
        HttpServletResponse afterResetResponse = mock(HttpServletResponse.class);

        filter.doFilterInternal(afterResetRequest, afterResetResponse, chain);

        verify(afterResetResponse, never()).setStatus(429);
        verify(chain).doFilter(afterResetRequest, afterResetResponse);
    }

    @Test
    @SuppressWarnings("unchecked")
    void staleWindowsAreEvictedAfterSweepThreshold() throws Exception {
        String staleIp = "10.20.30.40";

        HttpServletRequest firstRequest = requestFor("/api/auth/login", staleIp);
        filter.doFilterInternal(firstRequest, mock(HttpServletResponse.class), chain);

        Map<String, Object> windows = (Map<String, Object>) ReflectionTestUtils.getField(filter, "windows");
        Object window = windows.get(staleIp);
        assertThat(window).isNotNull();
        ReflectionTestUtils.setField(window, "windowStart", System.currentTimeMillis() - 61_000L);

        // Drive the internal request counter past the sweep threshold (500) using a fresh IP
        // per request, so none of them get throttled (which would short-circuit before the
        // counter increments) and an opportunistic eviction pass gets triggered.
        for (int i = 0; i < 500; i++) {
            HttpServletRequest request = requestFor("/api/auth/login", "10.20.31." + i);
            HttpServletResponse response = mock(HttpServletResponse.class);
            filter.doFilterInternal(request, response, chain);
        }

        assertThat(windows).doesNotContainKey(staleIp);
    }
}
