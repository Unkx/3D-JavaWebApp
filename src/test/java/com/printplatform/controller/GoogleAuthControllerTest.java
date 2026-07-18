package com.printplatform.controller;

import com.printplatform.controller.support.AbstractControllerTest;
import com.printplatform.dto.GoogleLoginRequest;
import com.printplatform.model.User;
import com.printplatform.security.GoogleAuthClient;
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
class GoogleAuthControllerTest extends AbstractControllerTest {

    @MockBean
    private GoogleAuthClient googleAuthClient;

    private GoogleLoginRequest request(String token) {
        GoogleLoginRequest request = new GoogleLoginRequest();
        request.setIdToken(token);
        return request;
    }

    @Test
    void loginWithGoogle_newUser_returns200AndCreatesUser() throws Exception {
        String email = "gnew-" + UUID.randomUUID() + "@test.local";
        when(googleAuthClient.verify(anyString()))
                .thenReturn(new GoogleAuthClient.GoogleProfile("google-" + UUID.randomUUID(), email, "Jan", "Kowalski", null));

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("some-id-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email));

        assertThat(userRepository.findByEmail(email)).isPresent();
    }

    @Test
    void loginWithGoogle_emailAlreadyExists_returns409() throws Exception {
        User existing = persistUser();
        when(googleAuthClient.verify(anyString()))
                .thenReturn(new GoogleAuthClient.GoogleProfile("google-" + UUID.randomUUID(), existing.getEmail(), "Anna", "Nowak", null));

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("some-id-token"))))
                .andExpect(status().isConflict());
    }
}
