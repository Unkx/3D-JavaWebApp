package com.printplatform.repository;

import com.printplatform.model.StlFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StlFileRepository extends JpaRepository<StlFile, UUID> {
    List<StlFile> findByListingIdOrderBySortOrderAscCreatedAtAsc(UUID listingId);
    long countByListingId(UUID listingId);
    void deleteByListingId(UUID listingId);

    /** Returns (listingId, fileId, contentType) tuples for all files belonging to the given listings,
     *  ordered so images come first within each listing. Used to populate card preview fields. */
    @Query("SELECT f.listing.id, f.id, f.contentType FROM StlFile f WHERE f.listing.id IN :ids ORDER BY f.listing.id ASC, f.sortOrder ASC, f.createdAt ASC")
    List<Object[]> findFilePreviewInfoByListingIds(@Param("ids") Collection<UUID> ids);
}
