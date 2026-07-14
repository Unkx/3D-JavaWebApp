package com.printplatform.service;

import com.printplatform.dto.TrafficSummaryDto;
import com.printplatform.model.ApiRequestLog;
import com.printplatform.model.PageView;
import com.printplatform.repository.ApiRequestLogRepository;
import com.printplatform.repository.PageViewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock private PageViewRepository pageViewRepository;
    @Mock private ApiRequestLogRepository apiRequestLogRepository;

    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(pageViewRepository, apiRequestLogRepository);
    }

    private PageView view(String path, LocalDateTime createdAt) {
        PageView v = new PageView();
        v.setPath(path);
        v.setSessionId("s1");
        v.setCreatedAt(createdAt);
        return v;
    }

    private ApiRequestLog apiLog(int status, long durationMs) {
        ApiRequestLog l = new ApiRequestLog();
        l.setMethod("GET");
        l.setPath("/api/listings");
        l.setStatus(status);
        l.setDurationMs(durationMs);
        l.setCreatedAt(LocalDateTime.now());
        return l;
    }

    @Test
    void getTrafficSummary_groupsPageViewsByDayAndTopPaths() {
        LocalDateTime today = LocalDateTime.now();
        when(pageViewRepository.findByCreatedAtAfter(any())).thenReturn(List.of(
                view("/zlecenia", today),
                view("/zlecenia", today),
                view("/", today)
        ));
        when(apiRequestLogRepository.findByCreatedAtAfter(any())).thenReturn(List.of(
                apiLog(200, 100),
                apiLog(500, 300)
        ));

        TrafficSummaryDto summary = analyticsService.getTrafficSummary(7);

        assertThat(summary.getPageViewsByDay()).hasSize(1);
        assertThat(summary.getPageViewsByDay().get(0).getCount()).isEqualTo(3);
        assertThat(summary.getTopPaths()).extracting("path").contains("/zlecenia", "/");
        assertThat(summary.getTopPaths().get(0).getPath()).isEqualTo("/zlecenia");
        assertThat(summary.getTopPaths().get(0).getCount()).isEqualTo(2);
        assertThat(summary.getApiStats().getTotalRequests()).isEqualTo(2);
        assertThat(summary.getApiStats().getErrorCount()).isEqualTo(1);
        assertThat(summary.getApiStats().getAvgDurationMs()).isEqualTo(200.0);
    }
}
