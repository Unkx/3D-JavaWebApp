package com.printplatform.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "seller_cost_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SellerCostSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "seller_id", nullable = false, unique = true)
    @JsonIgnore
    private User seller;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal filamentPricePerKg = new BigDecimal("120.00");

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal costPerPrintHour = new BigDecimal("1.50");
}
