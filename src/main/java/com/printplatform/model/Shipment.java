package com.printplatform.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Shipment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "payment_id", nullable = false)
    @JsonIgnoreProperties({"offer", "buyer", "seller"})
    private Payment payment;

    @OneToOne
    @JoinColumn(name = "offer_id", nullable = false, unique = true)
    @JsonIgnoreProperties({"listing", "user"})
    private Offer offer;

    @Column(length = 50)
    private String trackingNumber;

    private String labelUrl;

    @Column(length = 20)
    private String senderPaczkomat;

    @Column(length = 20)
    private String receiverPaczkomat;

    @Column(length = 1)
    private String parcelSize;

    @Enumerated(EnumType.STRING)
    private ShipmentStatus status = ShipmentStatus.LABEL_CREATED;

    private LocalDateTime createdAt = LocalDateTime.now();
}
