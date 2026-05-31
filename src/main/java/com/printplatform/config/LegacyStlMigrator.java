package com.printplatform.config;

import com.printplatform.model.Listing;
import com.printplatform.model.StlFile;
import com.printplatform.repository.ListingRepository;
import com.printplatform.repository.StlFileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time migration: moves a listing's legacy single uploaded STL
 * (Listing.stlFileData) into the new multi-file stl_files table so existing
 * models keep displaying after the switch to multiple files.
 */
@Component
@Order(1)
public class LegacyStlMigrator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyStlMigrator.class);

    private final ListingRepository listingRepository;
    private final StlFileRepository stlFileRepository;

    public LegacyStlMigrator(ListingRepository listingRepository, StlFileRepository stlFileRepository) {
        this.listingRepository = listingRepository;
        this.stlFileRepository = stlFileRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        int migrated = 0;
        for (Listing listing : listingRepository.findAll()) {
            byte[] legacy = listing.getStlFileData();
            if (legacy == null || legacy.length == 0) {
                continue;
            }
            if (stlFileRepository.countByListingId(listing.getId()) > 0) {
                continue; // already has files in the new table
            }
            StlFile file = new StlFile();
            file.setListing(listing);
            file.setFileName(listing.getStlFileName() != null ? listing.getStlFileName() : "model.stl");
            file.setFileData(legacy);
            file.setFileSize((long) legacy.length);
            stlFileRepository.save(file);

            // Clear the legacy single-file fields so there is one source of truth.
            listing.setStlFileData(null);
            listing.setStlFileName(null);
            listingRepository.save(listing);
            migrated++;
        }
        if (migrated > 0) {
            log.info("Migrated {} legacy STL file(s) into stl_files table", migrated);
        }
    }
}
