package com.printplatform.repository;

import com.printplatform.model.SellerCostSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SellerCostSettingsRepository extends JpaRepository<SellerCostSettings, UUID> {
    Optional<SellerCostSettings> findBySellerId(UUID sellerId);
}
