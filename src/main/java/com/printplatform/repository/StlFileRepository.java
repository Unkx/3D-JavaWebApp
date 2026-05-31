package com.printplatform.repository;

import com.printplatform.model.StlFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StlFileRepository extends JpaRepository<StlFile, UUID> {
    List<StlFile> findByListingIdOrderByCreatedAtAsc(UUID listingId);
    long countByListingId(UUID listingId);
    void deleteByListingId(UUID listingId);
}
