package com.printplatform.controller;

import com.printplatform.controller.support.AbstractControllerTest;
import com.printplatform.dto.FacebookLoginRequest;
import com.printplatform.model.Role;
import com.printplatform.model.User;
import com.printplatform.security.FacebookAuthClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class FacebookAuthControllerTest extends AbstractControllerTest {

    @MockBean
    private FacebookAuthClient facebookAuthClient;

    private FacebookLoginRequest request(String token) {
        FacebookLoginRequest request = new FacebookLoginRequest();
        request.setAccessToken(token);
        return request;
    }

    @Test
    void loginWithFacebook_newUser_returns200AndCreatesUser() throws Exception {
        String email = "fbnew-" + UUID.randomUUID() + "@test.local";
        when(facebookAuthClient.verify(anyString()))
                .thenReturn(new FacebookAuthClient.FacebookProfile("fb-" + UUID.randomUUID(), email, "Jan", "Kowalski"));

        mockMvc.perform(post("/api/auth/facebook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("some-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email));

        assertThat(userRepository.findByEmail(email)).isPresent();
    }

    @Test
    void loginWithFacebook_existingEmailAccount_autoLinksAndReturns200() throws Exception {
        User existing = persistUser();
        String facebookId = "fb-" + UUID.randomUUID();
        when(facebookAuthClient.verify(anyString()))
                .thenReturn(new FacebookAuthClient.FacebookProfile(facebookId, existing.getEmail(), "Anna", "Nowak"));

        mockMvc.perform(post("/api/auth/facebook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("some-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(existing.getId().toString()));

        User reloaded = userRepository.findById(existing.getId()).orElseThrow();
        assertThat(reloaded.getFacebookId()).isEqualTo(facebookId);
    }

    @Test
    void loginWithFacebook_missingEmail_returns400() throws Exception {
        when(facebookAuthClient.verify(anyString()))
                .thenReturn(new FacebookAuthClient.FacebookProfile("fb-x", null, "No", "Email"));

        mockMvc.perform(post("/api/auth/facebook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("some-token"))))
                .andExpect(status().isBadRequest());
    }
}
