package com.printplatform.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "offer_id", nullable = false, unique = true)
    @JsonIgnoreProperties({"listing", "user"})
    private Offer offer;

    @ManyToOne
    @JoinColumn(name = "buyer_id", nullable = false)
    @JsonIgnoreProperties({"password", "authorities", "enabled", "accountNonExpired", "accountNonLocked", "credentialsNonExpired"})
    private User buyer;

    @ManyToOne
    @JoinColumn(name = "seller_id", nullable = false)
    @JsonIgnoreProperties({"password", "authorities", "enabled", "accountNonExpired", "accountNonLocked", "credentialsNonExpired"})
    private User seller;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal contractorPrice;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal platformFeePercent;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal platformFee;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal shippingPrice;

    @Column(length = 1)
    private String parcelSize;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(length = 20)
    private String receiverPaczkomat;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status = PaymentStatus.PENDING;

    private LocalDateTime paidAt;
    private LocalDateTime releasedAt;
    private LocalDateTime createdAt = LocalDateTime.now();
}
