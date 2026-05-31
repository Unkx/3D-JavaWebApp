package com.printplatform.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stl_files")
@Getter
@Setter
@NoArgsConstructor
public class StlFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false)
    @JsonIgnore
    private Listing listing;

    @Column(nullable = false)
    private String fileName;

    /** MIME type, e.g. model/stl, image/png, image/jpeg. */
    private String contentType;

    @JsonIgnore
    @Column(columnDefinition = "bytea")
    private byte[] fileData;

    private Long fileSize;

    private LocalDateTime createdAt = LocalDateTime.now();
}
