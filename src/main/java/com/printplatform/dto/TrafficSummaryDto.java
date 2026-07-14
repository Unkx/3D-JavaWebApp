package com.printplatform.dto;

import java.util.List;

public class TrafficSummaryDto {
    private List<DailyCountDto> pageViewsByDay;
    private List<PathCountDto> topPaths;
    private ApiStatsDto apiStats;

    public TrafficSummaryDto(List<DailyCountDto> pageViewsByDay, List<PathCountDto> topPaths, ApiStatsDto apiStats) {
        this.pageViewsByDay = pageViewsByDay;
        this.topPaths = topPaths;
        this.apiStats = apiStats;
    }

    public List<DailyCountDto> getPageViewsByDay() { return pageViewsByDay; }
    public List<PathCountDto> getTopPaths() { return topPaths; }
    public ApiStatsDto getApiStats() { return apiStats; }
}
