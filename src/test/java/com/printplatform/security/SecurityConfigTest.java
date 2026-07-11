package com.printplatform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.printplatform.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end permit/deny checks for {@link SecurityConfig}'s filter chain, run
 * against the real Spring context (H2 in-memory test profile). Verifies one
 * public rule, one "any authenticated user" rule, and the ADMIN-only rule.
 *
 * Uses the app.admin.email/app.admin.password seeded by DataInitializer
 * (admin@druk3d.pl / admin123 per src/test/resources/application.properties)
 * for role-based assertions, plus a freshly registered USER account.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    private static final String ADMIN_EMAIL = "admin@druk3d.pl";
    private static final String ADMIN_PASSWORD = "admin123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Test
    void publicGetListingsEndpointIsReachableWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/listings"))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedOnlyEndpointRejectsRequestWithNoToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/me")).andReturn();

        assertThat(result.getResponse().getStatus()).isIn(401, 403);
    }

    @Test
    void authenticatedOnlyEndpointAcceptsAnyLoggedInUser() throws Exception {
        String email = "sec-config-user-" + System.nanoTime() + "@example.com";
        registerAndVerifyUser(email, "Password123");
        String token = loginAndGetToken(email, "Password123");

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void adminOnlyEndpointRejectsARegularUser() throws Exception {
        String email = "sec-config-user2-" + System.nanoTime() + "@example.com";
        registerAndVerifyUser(email, "Password123");
        String token = loginAndGetToken(email, "Password123");

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminOnlyEndpointAcceptsTheSeededAdminAccount() throws Exception {
        String token = loginAndGetToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    /**
     * Registers via the real HTTP endpoint (so this test still exercises the actual
     * registration path), then marks the account verified directly through the
     * repository — new registrations start unverified and /api/auth/login now rejects
     * unverified accounts with 403, which this test isn't exercising.
     */
    private void registerAndVerifyUser(String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(new RegisterPayload(email, password, null));
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        userRepository.findByEmail(email).ifPresent(user -> {
            user.setEmailVerified(true);
            userRepository.save(user);
        });
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginPayload(email, password));
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("token").asText();
    }

    private record RegisterPayload(String email, String password, String adminCode) {}

    private record LoginPayload(String email, String password) {}
}
