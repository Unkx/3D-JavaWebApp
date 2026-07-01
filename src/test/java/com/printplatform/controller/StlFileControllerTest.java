package com.printplatform.controller;

import com.printplatform.controller.support.AbstractControllerTest;
import com.printplatform.model.Listing;
import com.printplatform.model.ListingStatus;
import com.printplatform.model.StlFile;
import com.printplatform.model.User;
import com.printplatform.repository.ListingRepository;
import com.printplatform.repository.StlFileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class StlFileControllerTest extends AbstractControllerTest {

    @Autowired
    private ListingRepository listingRepository;
    @Autowired
    private StlFileRepository stlFileRepository;

    private Listing persistListing(User owner) {
        Listing listing = new Listing();
        listing.setUser(owner);
        listing.setTitle("Listing " + UUID.randomUUID());
        listing.setRequiredMaterial("PLA");
        listing.setStatus(ListingStatus.OPEN);
        return listingRepository.save(listing);
    }

    private StlFile persistFile(Listing listing) {
        StlFile file = new StlFile();
        file.setListing(listing);
        file.setFileName("model.stl");
        file.setContentType("model/stl");
        file.setFileData(new byte[]{1, 2, 3, 4});
        file.setFileSize(4L);
        return stlFileRepository.save(file);
    }

    @Test
    void list_returnsFilesForListing() throws Exception {
        User owner = persistUser();
        Listing listing = persistListing(owner);
        persistFile(listing);

        mockMvc.perform(get("/api/listings/{listingId}/stl-files", listing.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").value("model.stl"));
    }

    @Test
    void download_found_returns200WithBytes() throws Exception {
        User owner = persistUser();
        Listing listing = persistListing(owner);
        StlFile file = persistFile(listing);

        mockMvc.perform(get("/api/listings/{listingId}/stl-files/{fileId}", listing.getId(), file.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void download_notFound_returns404() throws Exception {
        User owner = persistUser();
        Listing listing = persistListing(owner);

        mockMvc.perform(get("/api/listings/{listingId}/stl-files/{fileId}", listing.getId(), UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void upload_owner_returns201() throws Exception {
        User owner = persistUser();
        Listing listing = persistListing(owner);

        MockMultipartFile part = new MockMultipartFile(
                "files", "model.stl", "application/octet-stream", new byte[]{9, 9, 9});

        mockMvc.perform(multipart("/api/listings/{listingId}/stl-files", listing.getId())
                        .file(part)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].fileName").value("model.stl"));
    }

    @Test
    void upload_nonOwner_returns403() throws Exception {
        User owner = persistUser();
        User stranger = persistUser();
        Listing listing = persistListing(owner);

        MockMultipartFile part = new MockMultipartFile(
                "files", "model.stl", "application/octet-stream", new byte[]{9, 9, 9});

        mockMvc.perform(multipart("/api/listings/{listingId}/stl-files", listing.getId())
                        .file(part)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(stranger)))
                .andExpect(status().isForbidden());
    }

    @Test
    void upload_disallowedExtension_returns400() throws Exception {
        User owner = persistUser();
        Listing listing = persistListing(owner);

        MockMultipartFile part = new MockMultipartFile(
                "files", "model.exe", "application/octet-stream", new byte[]{9, 9, 9});

        mockMvc.perform(multipart("/api/listings/{listingId}/stl-files", listing.getId())
                        .file(part)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owner)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_noAuth_returns403() throws Exception {
        User owner = persistUser();
        Listing listing = persistListing(owner);

        MockMultipartFile part = new MockMultipartFile(
                "files", "model.stl", "application/octet-stream", new byte[]{9, 9, 9});

        mockMvc.perform(multipart("/api/listings/{listingId}/stl-files", listing.getId())
                        .file(part))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_owner_returns204() throws Exception {
        User owner = persistUser();
        Listing listing = persistListing(owner);
        StlFile file = persistFile(listing);

        mockMvc.perform(delete("/api/listings/{listingId}/stl-files/{fileId}", listing.getId(), file.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owner)))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_nonOwner_returns403() throws Exception {
        User owner = persistUser();
        User stranger = persistUser();
        Listing listing = persistListing(owner);
        StlFile file = persistFile(listing);

        mockMvc.perform(delete("/api/listings/{listingId}/stl-files/{fileId}", listing.getId(), file.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(stranger)))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_fileNotFound_returns404() throws Exception {
        User owner = persistUser();
        Listing listing = persistListing(owner);

        mockMvc.perform(delete("/api/listings/{listingId}/stl-files/{fileId}", listing.getId(), UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owner)))
                .andExpect(status().isNotFound());
    }
}
