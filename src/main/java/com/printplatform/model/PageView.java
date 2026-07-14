package com.printplatform.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "page_view")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageView {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String path;

    private UUID userId;

    @Column(nullable = false)
    private String sessionId;

    private String referrer;

    private LocalDateTime createdAt = LocalDateTime.now();
}
