package com.printplatform.dto;

import com.printplatform.model.Rating;
import java.time.LocalDateTime;

public class RatingDto {
    private String id;
    private String offerId;
    private String raterId;
    private String ratedUserId;
    private int stars;
    private String comment;
    private String moderationStatus;
    private LocalDateTime createdAt;

    public RatingDto(Rating r) {
        this.id               = r.getId().toString();
        this.offerId          = r.getOfferId().toString();
        this.raterId          = r.getRaterId().toString();
        this.ratedUserId      = r.getRatedUserId().toString();
        this.stars            = r.getStars();
        this.comment          = r.getComment();
        this.moderationStatus = r.getModerationStatus().name();
        this.createdAt        = r.getCreatedAt();
    }

    public String getId()               { return id; }
    public String getOfferId()          { return offerId; }
    public String getRaterId()          { return raterId; }
    public String getRatedUserId()      { return ratedUserId; }
    public int getStars()               { return stars; }
    public String getComment()          { return comment; }
    public String getModerationStatus() { return moderationStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
