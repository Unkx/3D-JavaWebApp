package com.printplatform.controller;

import com.printplatform.controller.support.AbstractControllerTest;
import com.printplatform.dto.RedeemCodeRequest;
import com.printplatform.model.AdminCode;
import com.printplatform.model.Listing;
import com.printplatform.model.Role;
import com.printplatform.model.User;
import com.printplatform.repository.AdminCodeRepository;
import com.printplatform.repository.ListingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class AdminControllerTest extends AbstractControllerTest {

    @Autowired
    private AdminCodeRepository adminCodeRepository;
    @Autowired
    private ListingRepository listingRepository;

    @Test
    void listAllListings_admin_returns200() throws Exception {
        User admin = persistUser(Role.ADMIN);

        mockMvc.perform(get("/api/admin/listings")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void listAllListings_nonAdmin_returns403() throws Exception {
        User user = persistUser();

        mockMvc.perform(get("/api/admin/listings")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAllListings_noAuth_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/listings"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listUsers_admin_returns200() throws Exception {
        User admin = persistUser(Role.ADMIN);
        persistUser();

        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void generateCode_admin_returns201() throws Exception {
        User admin = persistUser(Role.ADMIN);

        mockMvc.perform(post("/api/admin/codes")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.createdByEmail").value(admin.getEmail()));
    }

    @Test
    void generateCode_nonAdmin_returns403() throws Exception {
        User user = persistUser();

        mockMvc.perform(post("/api/admin/codes")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listCodes_admin_returns200() throws Exception {
        User admin = persistUser(Role.ADMIN);

        mockMvc.perform(get("/api/admin/codes")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void redeem_validCode_returns200AndPromotesUserToAdmin() throws Exception {
        User user = persistUser();
        AdminCode code = new AdminCode();
        code.setCode("ABCD-2345-EFGH");
        code.setCreatedByEmail("someone-else@test.local");
        adminCodeRepository.save(code);

        RedeemCodeRequest request = new RedeemCodeRequest();
        request.setCode("ABCD-2345-EFGH");

        mockMvc.perform(post("/api/admin/redeem")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void redeem_sameCodeTwice_secondAttemptReturns400AndDoesNotPromote() throws Exception {
        User firstUser = persistUser();
        User secondUser = persistUser();
        AdminCode code = new AdminCode();
        code.setCode("SINGLE-USE-CODE");
        code.setCreatedByEmail("someone-else@test.local");
        adminCodeRepository.save(code);

        RedeemCodeRequest request = new RedeemCodeRequest();
        request.setCode("SINGLE-USE-CODE");

        mockMvc.perform(post("/api/admin/redeem")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(firstUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(post("/api/admin/redeem")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(secondUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        User reloadedSecondUser = userRepository.findById(secondUser.getId()).orElseThrow();
        assertThat(reloadedSecondUser.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void redeem_alreadyAdmin_returns400() throws Exception {
        User admin = persistUser(Role.ADMIN);

        RedeemCodeRequest request = new RedeemCodeRequest();
        request.setCode("WHATEVER-CODE");

        mockMvc.perform(post("/api/admin/redeem")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void redeem_invalidCode_returns400() throws Exception {
        User user = persistUser();

        RedeemCodeRequest request = new RedeemCodeRequest();
        request.setCode("DOES-NOT-EXIST");

        mockMvc.perform(post("/api/admin/redeem")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void redeem_noAuth_returns403() throws Exception {
        RedeemCodeRequest request = new RedeemCodeRequest();
        request.setCode("ANY-CODE-HERE");

        mockMvc.perform(post("/api/admin/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void suspendUser_admin_returns200AndMarksSuspended() throws Exception {
        User admin = persistUser(Role.ADMIN);
        User target = persistUser();

        mockMvc.perform(put("/api/admin/users/" + target.getId() + "/suspend")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspended").value(true));
    }

    @Test
    void suspendUser_nonAdmin_returns403() throws Exception {
        User user = persistUser();
        User target = persistUser();

        mockMvc.perform(put("/api/admin/users/" + target.getId() + "/suspend")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    void hideListing_admin_returns200AndMarksHidden() throws Exception {
        User admin = persistUser(Role.ADMIN);
        User owner = persistUser();
        Listing listing = new Listing();
        listing.setUser(owner);
        listing.setTitle("Test listing");
        Listing saved = listingRepository.save(listing);

        mockMvc.perform(put("/api/admin/listings/" + saved.getId() + "/hide")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moderationStatus").value("HIDDEN"));
    }

    @Test
    void getAuditLog_admin_returns200() throws Exception {
        User admin = persistUser(Role.ADMIN);

        mockMvc.perform(get("/api/admin/audit-log")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void getAuditLog_nonAdmin_returns403() throws Exception {
        User user = persistUser();

        mockMvc.perform(get("/api/admin/audit-log")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isForbidden());
    }
}
