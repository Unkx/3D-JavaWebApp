package com.printplatform.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "admin_codes")
@Data
@NoArgsConstructor
public class AdminCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String code;

    private String createdByEmail;

    private boolean used = false;

    private String usedByEmail;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime redeemedAt;
}
