package com.printplatform.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.util.UUID;
import java.time.LocalDateTime;

@Entity
@Table(name = "offers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Offer {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"password", "authorities", "enabled", "accountNonExpired", "accountNonLocked", "credentialsNonExpired"})
    private User user;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private Double printingTimeHours;

    @Column(nullable = false)
    private Integer filamentGrams;

    private String printerModel;

    @Enumerated(EnumType.STRING)
    private OfferStatus status = OfferStatus.PENDING;

    private LocalDateTime selectedAt;

    private LocalDateTime createdAt = LocalDateTime.now();
}