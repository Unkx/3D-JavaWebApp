package com.printplatform.controller;

import com.printplatform.dto.CreateListingRequest;
import com.printplatform.dto.PageResponse;
import com.printplatform.dto.UpdateListingRequest;
import com.printplatform.model.Listing;
import com.printplatform.model.ListingStatus;
import com.printplatform.model.Role;
import com.printplatform.model.StlFile;
import com.printplatform.model.User;
import com.printplatform.repository.ListingRepository;
import com.printplatform.repository.OfferRepository;
import com.printplatform.repository.StlFileRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/listings")
public class ListingController {

    private final ListingRepository listingRepository;
    private final OfferRepository offerRepository;
    private final StlFileRepository stlFileRepository;

    public ListingController(ListingRepository listingRepository,
                             OfferRepository offerRepository,
                             StlFileRepository stlFileRepository) {
        this.listingRepository = listingRepository;
        this.offerRepository = offerRepository;
        this.stlFileRepository = stlFileRepository;
    }

    @GetMapping
    public PageResponse<Listing> getOpenListings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        int safeSize = Math.clamp(size, 1, 50);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return new PageResponse<>(listingRepository.findByStatus(ListingStatus.OPEN, pageable));
    }

    @GetMapping("/{id}")
    public Listing getListing(@PathVariable UUID id) {
        return listingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zlecenie nie istnieje"));
    }

    @GetMapping("/my")
    public List<Listing> getMyListings(@AuthenticationPrincipal User user) {
        return listingRepository.findByUserId(user.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Listing createListing(@Valid @RequestBody CreateListingRequest request,
                                 @AuthenticationPrincipal User user) {
        Listing listing = new Listing();
        listing.setTitle(request.getTitle());
        listing.setDescription(request.getDescription());
        listing.setRequiredMaterial(request.getRequiredMaterial());
        listing.setMaxBudget(request.getMaxBudget());
        listing.setEstimatorSize(request.getEstimatorSize());
        listing.setEstimatorQuality(request.getEstimatorQuality());
        listing.setUser(user);
        listing.setStatus(ListingStatus.OPEN);
        return listingRepository.save(listing);
    }

    @PatchMapping("/{id}")
    public Listing updateListing(@PathVariable UUID id,
                                  @RequestBody UpdateListingRequest request,
                                  @AuthenticationPrincipal User user) {
        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zlecenie nie istnieje"));
        requireOwnerOrAdmin(listing, user);
        if (request.getDescription() != null) listing.setDescription(request.getDescription());
        if (request.getRequiredMaterial() != null) listing.setRequiredMaterial(request.getRequiredMaterial());
        listing.setMaxBudget(request.getMaxBudget());
        if (request.getEstimatorSize() != null) listing.setEstimatorSize(request.getEstimatorSize());
        if (request.getEstimatorQuality() != null) listing.setEstimatorQuality(request.getEstimatorQuality());
        return listingRepository.save(listing);
    }

    @PutMapping("/{id}/close")
    public Listing closeListing(@PathVariable UUID id,
                                @AuthenticationPrincipal User user) {
        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zlecenie nie istnieje"));
        requireOwnerOrAdmin(listing, user);
        listing.setStatus(ListingStatus.CLOSED);
        return listingRepository.save(listing);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteListing(@PathVariable UUID id,
                              @AuthenticationPrincipal User user) {
        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zlecenie nie istnieje"));
        requireOwnerOrAdmin(listing, user);
        // Remove dependent rows first (listing_id FKs are non-nullable).
        offerRepository.deleteAll(offerRepository.findByListingId(id));
        stlFileRepository.deleteByListingId(id);
        listingRepository.delete(listing);
    }

    /** A listing may be modified/removed by its owner or by any administrator. */
    private void requireOwnerOrAdmin(Listing listing, User user) {
        boolean isOwner = listing.getUser().getId().equals(user.getId());
        boolean isAdmin = user.getRole() == Role.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Brak uprawnień");
        }
    }

    @GetMapping("/{id}/stl")
    public ResponseEntity<byte[]> downloadStl(@PathVariable UUID id) {
        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zlecenie nie istnieje"));

        // Priority 1: Serve from uploaded BLOB if present
        if (listing.getStlFileData() != null && listing.getStlFileData().length > 0) {
            String filename = listing.getStlFileName() != null ? listing.getStlFileName() : "model.stl";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
                    .body(listing.getStlFileData());
        }

        // Priority 2: Fall back to external URL if no uploaded file
        if (listing.getStlFileUrl() == null || listing.getStlFileUrl().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Brak pliku STL");
        }

        // A Thingiverse "thing:" link is a web PAGE, not a downloadable STL —
        // proxying it only ever returns HTML. Reject it with a clear message.
        String url = listing.getStlFileUrl();
        if (url.contains("thingiverse.com")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Link Thingiverse to strona, nie plik STL. Prześlij plik .stl, aby zobaczyć podgląd 3D.");
        }

        byte[] fileContent;
        try {
            RestTemplate restTemplate = new RestTemplate();
            fileContent = restTemplate.getForObject(url, byte[].class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Nie udało się pobrać pliku STL: " + e.getMessage());
        }

        // Guard: never serve an HTML page (login/error page) as if it were an STL.
        if (fileContent == null || looksLikeHtml(fileContent)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Adres URL nie wskazuje na plik STL. Prześlij plik .stl, aby zobaczyć podgląd 3D.");
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"model.stl\"")
                .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
                .body(fileContent);
    }

    /** Download all STL/image files for a listing as a ZIP (public).
     *  Structure: files/ for STL models, images/ for images, README.txt from description. */
    @GetMapping("/{id}/download-zip")
    public ResponseEntity<byte[]> downloadZip(@PathVariable UUID id) throws IOException {
        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zlecenie nie istnieje"));
        List<StlFile> files = stlFileRepository.findByListingIdOrderBySortOrderAscCreatedAtAsc(id);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            addReadme(zos, listing);
            Map<String, Integer> usedNames = new HashMap<>();
            for (StlFile file : files) {
                if (file.getFileData() == null) continue;
                boolean isImage = file.getContentType() != null && file.getContentType().startsWith("image/");
                String folder = isImage ? "images/" : "files/";
                String entryPath = uniqueZipEntry(folder + sanitizeZipName(file.getFileName()), usedNames);
                zos.putNextEntry(new ZipEntry(entryPath));
                zos.write(file.getFileData());
                zos.closeEntry();
            }
        }

        String zipName = sanitizeZipName(listing.getTitle()) + ".zip";
        ContentDisposition disposition = ContentDisposition.attachment().filename(zipName).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CONTENT_TYPE, "application/zip")
                .body(baos.toByteArray());
    }

    private void addReadme(ZipOutputStream zos, Listing listing) throws IOException {
        String desc = listing.getDescription();
        if (desc == null || desc.isBlank()) return;
        StringBuilder sb = new StringBuilder();
        sb.append(listing.getTitle()).append('\n');
        sb.append("=".repeat(Math.min(listing.getTitle().length(), 80))).append("\n\n");
        if (listing.getRequiredMaterial() != null)
            sb.append("Materiał: ").append(listing.getRequiredMaterial()).append('\n');
        if (listing.getMaxBudget() != null)
            sb.append("Budżet: do ").append(listing.getMaxBudget()).append(" zł\n");
        sb.append('\n').append(desc);
        zos.putNextEntry(new ZipEntry("README.txt"));
        zos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private String sanitizeZipName(String name) {
        if (name == null || name.isBlank()) return "file";
        return name.replaceAll("[/\\\\:*?\"<>|\\p{Cntrl}]", "_").strip();
    }

    private String uniqueZipEntry(String path, Map<String, Integer> counts) {
        int count = counts.merge(path, 1, Integer::sum);
        if (count == 1) return path;
        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        if (dot > slash) return path.substring(0, dot) + " (" + count + ")" + path.substring(dot);
        return path + " (" + count + ")";
    }

    private boolean looksLikeHtml(byte[] data) {
        int n = Math.min(data.length, 64);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            char c = (char) (data[i] & 0xFF);
            if (!Character.isWhitespace(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        String head = sb.toString();
        return head.startsWith("<!doctype") || head.startsWith("<html") || head.startsWith("<?xml");
    }
}
