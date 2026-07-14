package com.printplatform.repository;

import com.printplatform.model.PageView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PageViewRepository extends JpaRepository<PageView, UUID> {
    List<PageView> findByCreatedAtAfter(LocalDateTime since);
}
