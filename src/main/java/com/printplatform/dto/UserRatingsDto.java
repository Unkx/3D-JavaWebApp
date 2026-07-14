package com.printplatform.dto;

public class UserRatingsDto {
    private RatingSummaryDto summary;
    private PageResponse<RatingDto> ratings;

    public UserRatingsDto(RatingSummaryDto summary, PageResponse<RatingDto> ratings) {
        this.summary = summary;
        this.ratings = ratings;
    }

    public RatingSummaryDto getSummary()       { return summary; }
    public PageResponse<RatingDto> getRatings() { return ratings; }
}
