package com.printplatform.repository;

import com.printplatform.model.RecurringCost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecurringCostRepository extends JpaRepository<RecurringCost, UUID> {
    List<RecurringCost> findBySellerId(UUID sellerId);
}
