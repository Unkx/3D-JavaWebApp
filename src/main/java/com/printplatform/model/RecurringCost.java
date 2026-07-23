package com.printplatform.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "recurring_costs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecurringCost {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "seller_id", nullable = false)
    @JsonIgnore
    private User seller;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyAmount;

    @Column(nullable = false)
    private LocalDate startDate = LocalDate.now();

    private LocalDate endDate;

    private LocalDateTime createdAt = LocalDateTime.now();
}
