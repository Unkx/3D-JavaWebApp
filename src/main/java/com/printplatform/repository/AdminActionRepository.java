package com.printplatform.repository;

import com.printplatform.model.AdminAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AdminActionRepository extends JpaRepository<AdminAction, UUID> {
    Page<AdminAction> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
