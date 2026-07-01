package com.printplatform.controller;

import com.printplatform.controller.support.AbstractControllerTest;
import com.printplatform.model.Listing;
import com.printplatform.model.ListingStatus;
import com.printplatform.model.Role;
import com.printplatform.model.User;
import com.printplatform.repository.ListingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class FileUploadControllerTest extends AbstractControllerTest {

    @Autowired
    private ListingRepository listingRepository;

    private Listing persistListing(User owner) {
        Listing listing = new Listing();
        listing.setUser(owner);
        listing.setTitle("Listing " + UUID.randomUUID());
        listing.setRequiredMaterial("PLA");
        listing.setStatus(ListingStatus.OPEN);
        return listingRepository.save(listing);
    }

    @Test
    void uploadStlFile_owner_returns200AndStoresFile() throws Exception {
        User owner = persistUser();
        Listing listing = persistListing(owner);

        MockMultipartFile file = new MockMultipartFile(
                "file", "model.stl", "application/octet-stream", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/listings/{id}/upload-stl", listing.getId())
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stlFileName").value("model.stl"));
    }

    @Test
    void uploadStlFile_admin_returns200EvenForOthersListing() throws Exception {
        User owner = persistUser();
        User admin = persistUser(Role.ADMIN);
        Listing listing = persistListing(owner);

        MockMultipartFile file = new MockMultipartFile(
                "file", "model.stl", "application/octet-stream", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/listings/{id}/upload-stl", listing.getId())
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void uploadStlFile_nonOwnerNonAdmin_returns403() throws Exception {
        User owner = persistUser();
        User stranger = persistUser();
        Listing listing = persistListing(owner);

        MockMultipartFile file = new MockMultipartFile(
                "file", "model.stl", "application/octet-stream", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/listings/{id}/upload-stl", listing.getId())
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(stranger)))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadStlFile_noAuth_returns403() throws Exception {
        User owner = persistUser();
        Listing listing = persistListing(owner);

        MockMultipartFile file = new MockMultipartFile(
                "file", "model.stl", "application/octet-stream", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/listings/{id}/upload-stl", listing.getId())
                        .file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadStlFile_listingNotFound_returns404() throws Exception {
        User owner = persistUser();

        MockMultipartFile file = new MockMultipartFile(
                "file", "model.stl", "application/octet-stream", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/listings/{id}/upload-stl", UUID.randomUUID())
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadStlFile_wrongExtension_returns400() throws Exception {
        User owner = persistUser();
        Listing listing = persistListing(owner);

        MockMultipartFile file = new MockMultipartFile(
                "file", "model.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/listings/{id}/upload-stl", listing.getId())
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owner)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadStlFile_emptyFile_returns400() throws Exception {
        User owner = persistUser();
        Listing listing = persistListing(owner);

        MockMultipartFile file = new MockMultipartFile(
                "file", "model.stl", "application/octet-stream", new byte[0]);

        mockMvc.perform(multipart("/api/listings/{id}/upload-stl", listing.getId())
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owner)))
                .andExpect(status().isBadRequest());
    }
}
