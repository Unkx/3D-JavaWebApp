package com.printplatform.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ratings", uniqueConstraints = @UniqueConstraint(columnNames = {"offer_id", "rater_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Rating {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "offer_id", nullable = false)
    private UUID offerId;

    @Column(name = "rater_id", nullable = false)
    private UUID raterId;

    @Column(name = "rated_user_id", nullable = false)
    private UUID ratedUserId;

    @Column(nullable = false)
    private int stars;

    @Column(length = 500)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'VISIBLE'")
    private RatingModerationStatus moderationStatus = RatingModerationStatus.VISIBLE;

    private LocalDateTime createdAt = LocalDateTime.now();
}
