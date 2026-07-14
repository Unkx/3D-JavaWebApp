package com.printplatform.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
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
    @JsonIgnoreProperties({"password", "authorities", "enabled", "accountNonExpired", "accountNonLocked", "credentialsNonExpired"})
    private User user; // osoba 1 - zleceniodawca

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    private String requiredMaterial; // PLA, PETG, ABS...

    private BigDecimal maxBudget; // opcjonalny budżet maksymalny

    @Enumerated(EnumType.STRING)
    private ListingStatus status = ListingStatus.OPEN;

    // Column-level default is required: Hibernate's ddl-auto=update issues a plain
    // ALTER TABLE ADD COLUMN with no default unless @ColumnDefault is present, which fails
    // against a Postgres table that already has rows (NOT NULL with no default).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'VISIBLE'")
    private ListingModerationStatus moderationStatus = ListingModerationStatus.VISIBLE;

    private String stlFileUrl; // URL do pliku (po wybraniu oferty)

    @JsonIgnore
    @Column(columnDefinition = "bytea")
    private byte[] stlFileData; // Uploaded STL file binary data (mapped to PostgreSQL bytea)

    private String stlFileName; // Original filename of uploaded file

    private String estimatorSize;    // "small" | "medium" | "large"
    private String estimatorQuality; // "fast" | "normal" | "ultra"

    private LocalDateTime createdAt = LocalDateTime.now();

    /** Populated at query time — URL of the first uploaded image for this listing, or null. */
    @Transient
    private String previewImageUrl;

    /** Populated at query time — true if any StlFile (image or model) is attached. */
    @Transient
    private boolean hasAttachments;

    public String getPreviewImageUrl() { return previewImageUrl; }
    public void setPreviewImageUrl(String previewImageUrl) { this.previewImageUrl = previewImageUrl; }
    public boolean isHasAttachments() { return hasAttachments; }
    public void setHasAttachments(boolean hasAttachments) { this.hasAttachments = hasAttachments; }
}