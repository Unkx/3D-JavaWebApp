package com.printplatform.controller;

import com.printplatform.dto.StlFileDto;
import com.printplatform.model.Listing;
import com.printplatform.model.Role;
import com.printplatform.model.StlFile;
import com.printplatform.model.User;
import com.printplatform.repository.ListingRepository;
import com.printplatform.repository.StlFileRepository;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/listings/{listingId}/stl-files")
public class StlFileController {

    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024; // 50MB per file
    private static final int MAX_FILES_PER_LISTING = 20;
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("stl", "png", "jpg", "jpeg", "gif", "webp");

    private final StlFileRepository stlFileRepository;
    private final ListingRepository listingRepository;

    public StlFileController(StlFileRepository stlFileRepository, ListingRepository listingRepository) {
        this.stlFileRepository = stlFileRepository;
        this.listingRepository = listingRepository;
    }

    /** List the STL files attached to a listing (public, metadata only). */
    @GetMapping
    public List<StlFileDto> list(@PathVariable UUID listingId) {
        return stlFileRepository.findByListingIdOrderByCreatedAtAsc(listingId)
                .stream().map(StlFileDto::new).toList();
    }

    /** Download/serve a single file's binary content (public). Images are served
     *  inline with their real MIME type so they render in an <img> tag. */
    @GetMapping("/{fileId}")
    public ResponseEntity<byte[]> download(@PathVariable UUID listingId, @PathVariable UUID fileId) {
        StlFile file = getFileOf(listingId, fileId);
        String name = file.getFileName() != null ? file.getFileName() : "model.stl";
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        boolean isImage = contentType.startsWith("image/");
        ContentDisposition disposition = ContentDisposition.builder(isImage ? "inline" : "attachment")
                .filename(name)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(file.getFileData());
    }

    /** Upload one or more STL files (owner or admin). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<StlFileDto> upload(@PathVariable UUID listingId,
                                   @RequestParam("files") MultipartFile[] files,
                                   @AuthenticationPrincipal User user) {
        Listing listing = requireListing(listingId);
        requireOwnerOrAdmin(listing, user);

        if (files == null || files.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nie wybrano żadnego pliku");
        }
        long existing = stlFileRepository.countByListingId(listingId);
        if (existing + files.length > MAX_FILES_PER_LISTING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Maksymalna liczba plików na zlecenie to " + MAX_FILES_PER_LISTING);
        }

        List<StlFileDto> saved = new ArrayList<>();
        for (MultipartFile file : files) {
            validate(file);
            try {
                StlFile entity = new StlFile();
                entity.setListing(listing);
                entity.setFileName(file.getOriginalFilename());
                entity.setContentType(resolveContentType(file));
                entity.setFileData(file.getBytes());
                entity.setFileSize(file.getSize());
                saved.add(new StlFileDto(stlFileRepository.save(entity)));
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Nie udało się przesłać pliku: " + e.getMessage());
            }
        }
        return saved;
    }

    /** Delete one STL file (owner or admin). */
    @DeleteMapping("/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID listingId, @PathVariable UUID fileId,
                       @AuthenticationPrincipal User user) {
        requireOwnerOrAdmin(requireListing(listingId), user);
        StlFile file = getFileOf(listingId, fileId);
        stlFileRepository.delete(file);
    }

    // --- helpers ---

    private Listing requireListing(UUID listingId) {
        return listingRepository.findById(listingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zlecenie nie istnieje"));
    }

    private StlFile getFileOf(UUID listingId, UUID fileId) {
        StlFile file = stlFileRepository.findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plik nie istnieje"));
        if (file.getListing() == null || !file.getListing().getId().equals(listingId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plik nie należy do tego zlecenia");
        }
        return file;
    }

    private void requireOwnerOrAdmin(Listing listing, User user) {
        boolean isOwner = listing.getUser().getId().equals(user.getId());
        boolean isAdmin = user.getRole() == Role.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Brak uprawnień do przesyłania plików");
        }
    }

    private void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Jeden z plików jest pusty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Plik jest zbyt duży. Maksymalny rozmiar to 50MB");
        }
        if (!ALLOWED_EXTENSIONS.contains(extension(file.getOriginalFilename()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Dozwolone są pliki .stl oraz obrazy (.png, .jpg, .jpeg, .gif, .webp)");
        }
    }

    private String extension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }

    private String resolveContentType(MultipartFile file) {
        String ext = extension(file.getOriginalFilename());
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "model/stl";
        };
    }
}
