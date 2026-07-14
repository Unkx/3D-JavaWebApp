package com.printplatform.repository;

import com.printplatform.model.ApiRequestLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ApiRequestLogRepository extends JpaRepository<ApiRequestLog, UUID> {
    List<ApiRequestLog> findByCreatedAtAfter(LocalDateTime since);
}
