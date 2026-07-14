package com.printplatform.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "api_request_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiRequestLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(nullable = false)
    private String path;

    @Column(nullable = false)
    private int status;

    @Column(nullable = false)
    private long durationMs;

    private UUID userId;

    private String ip;

    private LocalDateTime createdAt = LocalDateTime.now();
}
