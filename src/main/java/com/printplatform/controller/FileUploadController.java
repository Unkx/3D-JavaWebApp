package com.printplatform.controller;

import com.printplatform.model.Listing;
import com.printplatform.model.Role;
import com.printplatform.model.User;
import com.printplatform.repository.ListingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/listings")
public class FileUploadController {

    private static final Logger log = LoggerFactory.getLogger(FileUploadController.class);
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024; // 50MB

    private final ListingRepository listingRepository;

    public FileUploadController(ListingRepository listingRepository) {
        this.listingRepository = listingRepository;
    }

    @PostMapping("/{id}/upload-stl")
    public Listing uploadStlFile(@PathVariable UUID id,
                                 @RequestParam("file") MultipartFile file,
                                 @AuthenticationPrincipal User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Musisz być zalogowany");
        }

        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zlecenie nie istnieje"));

        // Check authorization: user must be listing creator or admin
        boolean isOwner = listing.getUser().getId().equals(user.getId());
        boolean isAdmin = user.getRole() != null && user.getRole() == Role.ADMIN;

        if (!isOwner && !isAdmin) {
            log.warn("Upload denied: user {} is not owner of listing {}", user.getEmail(), id);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Brak uprawnień do przesyłania pliku");
        }

        // Validate file
        validateFile(file);

        try {
            byte[] fileData = file.getBytes();
            listing.setStlFileData(fileData);
            listing.setStlFileName(file.getOriginalFilename());
            Listing saved = listingRepository.save(listing);
            log.info("STL uploaded for listing {} by {} ({} bytes)", id, user.getEmail(), fileData.length);
            return saved;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nie udało się przesłać plik: " + e.getMessage());
        }
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<String> handleMaxSize(MaxUploadSizeExceededException e) {
        log.warn("Upload rejected: file exceeds max size", e);
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body("Plik jest zbyt duży (maksymalnie 50MB).");
    }

    private void validateFile(MultipartFile file) {
        // Check if file is empty
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plik jest pusty");
        }

        // Check file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("Plik jest zbyt duży. Maksymalny rozmiar: %dMB", MAX_FILE_SIZE / (1024L * 1024)));
        }

        // Check file extension
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".stl")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dozwolone są tylko pliki .stl");
        }

        // MIME type can vary by client, so we don't validate it strictly
        // Just check file extension is .stl which we already did above
    }
}
