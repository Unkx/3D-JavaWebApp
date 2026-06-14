package com.printplatform.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.util.UUID;
import java.time.LocalDateTime;

@Entity
@Table(name = "listings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Listing {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // osoba 1 - zleceniodawca

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    private String requiredMaterial; // PLA, PETG, ABS...

    private BigDecimal maxBudget; // opcjonalny budżet maksymalny

    @Enumerated(EnumType.STRING)
    private ListingStatus status = ListingStatus.OPEN;

    private String stlFileUrl; // URL do pliku (po wybraniu oferty)

    @JsonIgnore
    @Column(columnDefinition = "bytea")
    private byte[] stlFileData; // Uploaded STL file binary data (mapped to PostgreSQL bytea)

    private String stlFileName; // Original filename of uploaded file

    private String estimatorSize;    // "small" | "medium" | "large"
    private String estimatorQuality; // "fast" | "normal" | "ultra"

    private LocalDateTime createdAt = LocalDateTime.now();
}