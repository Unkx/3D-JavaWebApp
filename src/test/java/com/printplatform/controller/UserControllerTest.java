package com.printplatform.controller;

import com.printplatform.controller.support.AbstractControllerTest;
import com.printplatform.dto.UpdateProfileRequest;
import com.printplatform.dto.UpdateShippingRequest;
import com.printplatform.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class UserControllerTest extends AbstractControllerTest {

    @Test
    void getProfile_authenticated_returns200() throws Exception {
        User user = persistUser();

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void getProfile_noAuth_returns403() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateProfile_valid_returns200AndPersistsFields() throws Exception {
        User user = persistUser();

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFirstName("Jan");
        request.setLastName("Kowalski");
        request.setPhone("123456789");
        request.setBio("Hello there");
        request.setDateOfBirth("1990-05-10");

        mockMvc.perform(put("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Jan"))
                .andExpect(jsonPath("$.lastName").value("Kowalski"))
                .andExpect(jsonPath("$.dateOfBirth").value("1990-05-10"));
    }

    @Test
    void updateProfile_malformedDateOfBirth_returns400() throws Exception {
        User user = persistUser();

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setDateOfBirth("not-a-date");

        mockMvc.perform(put("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateShipping_valid_returns200AndPersistsFields() throws Exception {
        User user = persistUser();

        UpdateShippingRequest request = new UpdateShippingRequest();
        request.setStreet("Testowa");
        request.setHouseNumber("12A");
        request.setCity("Warszawa");
        request.setPostalCode("00-001");

        mockMvc.perform(put("/api/users/me/shipping")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.street").value("Testowa"))
                .andExpect(jsonPath("$.city").value("Warszawa"));
    }

    @Test
    void updateShipping_noAuth_returns403() throws Exception {
        UpdateShippingRequest request = new UpdateShippingRequest();
        request.setCity("Warszawa");

        mockMvc.perform(put("/api/users/me/shipping")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
