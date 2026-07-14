package com.printplatform.security;

import com.printplatform.model.ApiRequestLog;
import com.printplatform.repository.ApiRequestLogRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiRequestLoggingFilterTest {

    @Mock private ApiRequestLogRepository apiRequestLogRepository;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain chain;

    private ApiRequestLoggingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiRequestLoggingFilter(apiRequestLogRepository);
    }

    @Test
    void doFilterInternal_logsMethodPathStatusAndDuration() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/listings");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(response.getStatus()).thenReturn(200);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        ArgumentCaptor<ApiRequestLog> captor = ArgumentCaptor.forClass(ApiRequestLog.class);
        verify(apiRequestLogRepository).save(captor.capture());
        ApiRequestLog saved = captor.getValue();
        assertThat(saved.getMethod()).isEqualTo("GET");
        assertThat(saved.getPath()).isEqualTo("/api/listings");
        assertThat(saved.getStatus()).isEqualTo(200);
        assertThat(saved.getIp()).isEqualTo("127.0.0.1");
    }

    @Test
    void shouldNotFilter_analyticsAndTrafficPathsAreExcluded() {
        when(request.getRequestURI()).thenReturn("/api/analytics/pageview");
        assertThat(filter.shouldNotFilter(request)).isTrue();

        when(request.getRequestURI()).thenReturn("/api/admin/traffic");
        assertThat(filter.shouldNotFilter(request)).isTrue();

        when(request.getRequestURI()).thenReturn("/api/listings");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }
}
