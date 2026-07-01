package com.printplatform.repository;

import com.printplatform.model.AdminCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminCodeRepository extends JpaRepository<AdminCode, UUID> {
    Optional<AdminCode> findByCode(String code);
    List<AdminCode> findAllByOrderByCreatedAtDesc();

    /**
     * Atomically claims a code: only succeeds (returns 1) if it was still unused at the moment
     * of the update, so two concurrent redemptions of the same code can't both win the race.
     */
    @Modifying
    @Query("UPDATE AdminCode c SET c.used = true, c.usedByEmail = :usedByEmail, c.redeemedAt = :redeemedAt "
            + "WHERE c.id = :id AND c.used = false")
    int markUsedIfUnused(@Param("id") UUID id, @Param("usedByEmail") String usedByEmail,
                         @Param("redeemedAt") LocalDateTime redeemedAt);
}
