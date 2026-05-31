package com.printplatform.repository;

import com.printplatform.model.AdminCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminCodeRepository extends JpaRepository<AdminCode, UUID> {
    Optional<AdminCode> findByCode(String code);
    List<AdminCode> findAllByOrderByCreatedAtDesc();
}
