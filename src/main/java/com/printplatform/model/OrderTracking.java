package com.printplatform.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "order_tracking")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderTracking {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "offer_id", nullable = false, unique = true)
    @JsonIgnoreProperties({"listing", "user"})
    private Offer offer;

    @Column(length = 100)
    private String carrierName;

    @Column(length = 100)
    private String trackingNumber;

    private LocalDateTime shippedAt;

    private LocalDateTime deliveredAt;

    private LocalDateTime createdAt = LocalDateTime.now();
}
