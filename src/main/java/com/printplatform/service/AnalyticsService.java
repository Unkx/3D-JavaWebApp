package com.printplatform.service;

import com.printplatform.dto.ApiStatsDto;
import com.printplatform.dto.DailyCountDto;
import com.printplatform.dto.PathCountDto;
import com.printplatform.dto.TrafficSummaryDto;
import com.printplatform.model.ApiRequestLog;
import com.printplatform.model.PageView;
import com.printplatform.repository.ApiRequestLogRepository;
import com.printplatform.repository.PageViewRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int TOP_PATHS_LIMIT = 10;

    private final PageViewRepository pageViewRepository;
    private final ApiRequestLogRepository apiRequestLogRepository;

    public AnalyticsService(PageViewRepository pageViewRepository, ApiRequestLogRepository apiRequestLogRepository) {
        this.pageViewRepository = pageViewRepository;
        this.apiRequestLogRepository = apiRequestLogRepository;
    }

    public void recordPageView(String path, UUID userId, String sessionId, String referrer) {
        PageView view = new PageView();
        view.setPath(path);
        view.setUserId(userId);
        view.setSessionId(sessionId);
        view.setReferrer(referrer);
        pageViewRepository.save(view);
    }

    /** Aggregates in Java rather than SQL date-grouping, so behavior is identical between H2 (tests) and Postgres (prod). */
    public TrafficSummaryDto getTrafficSummary(int days) {
        int safeDays = Math.clamp(days, 1, 90);
        LocalDateTime since = LocalDateTime.now().minusDays(safeDays);

        List<PageView> views = pageViewRepository.findByCreatedAtAfter(since);
        Map<String, Long> byDay = new TreeMap<>();
        for (PageView v : views) {
            byDay.merge(v.getCreatedAt().format(DAY_FORMAT), 1L, Long::sum);
        }
        List<DailyCountDto> pageViewsByDay = byDay.entrySet().stream()
                .map(e -> new DailyCountDto(e.getKey(), e.getValue()))
                .toList();

        Map<String, Long> byPath = views.stream()
                .collect(Collectors.groupingBy(PageView::getPath, Collectors.counting()));
        List<PathCountDto> topPaths = byPath.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP_PATHS_LIMIT)
                .map(e -> new PathCountDto(e.getKey(), e.getValue()))
                .toList();

        List<ApiRequestLog> apiLogs = apiRequestLogRepository.findByCreatedAtAfter(since);
        long totalRequests = apiLogs.size();
        long errorCount = apiLogs.stream().filter(l -> l.getStatus() >= 400).count();
        double avgDurationMs = apiLogs.stream().mapToLong(ApiRequestLog::getDurationMs).average().orElse(0.0);

        return new TrafficSummaryDto(pageViewsByDay, topPaths, new ApiStatsDto(totalRequests, errorCount, avgDurationMs));
    }
}
