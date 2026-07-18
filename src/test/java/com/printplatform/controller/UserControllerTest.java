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

    @Test
    void getPublicProfile_existingUser_returns200WithoutEmail() throws Exception {
        User user = persistUser();
        user.setFirstName("Jan");
        user.setLastName("Kowalski");
        userRepository.save(user);

        mockMvc.perform(get("/api/users/" + user.getId() + "/public-profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Jan K."))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.hasAvatarData").value(false))
                .andExpect(jsonPath("$.avatarUrl").doesNotExist());
    }

    @Test
    void getPublicProfile_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/users/" + java.util.UUID.randomUUID() + "/public-profile"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPublicProfile_suspendedUser_returns404() throws Exception {
        User user = persistUser();
        user.setSuspended(true);
        userRepository.save(user);

        mockMvc.perform(get("/api/users/" + user.getId() + "/public-profile"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPublicProfile_showCityFalse_omitsCity() throws Exception {
        User user = persistUser();
        user.setCity("Warszawa");
        user.setShowCity(false);
        userRepository.save(user);

        mockMvc.perform(get("/api/users/" + user.getId() + "/public-profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").doesNotExist());
    }

    @Test
    void getPublicProfile_showCityTrue_includesCity() throws Exception {
        User user = persistUser();
        user.setCity("Warszawa");
        user.setShowCity(true);
        userRepository.save(user);

        mockMvc.perform(get("/api/users/" + user.getId() + "/public-profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Warszawa"));
    }

    @Test
    void getAvatar_noAvatarSet_returns404() throws Exception {
        User user = persistUser();

        mockMvc.perform(get("/api/users/" + user.getId() + "/avatar"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAvatar_avatarSet_returnsBytesWithContentType() throws Exception {
        User user = persistUser();
        user.setAvatarData(new byte[]{1, 2, 3, 4});
        user.setAvatarContentType("image/png");
        userRepository.save(user);

        mockMvc.perform(get("/api/users/" + user.getId() + "/avatar"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", "image/png"));
    }

    @Test
    void getAvatar_suspendedUser_returns404() throws Exception {
        User user = persistUser();
        user.setAvatarData(new byte[]{1, 2, 3, 4});
        user.setAvatarContentType("image/png");
        user.setSuspended(true);
        userRepository.save(user);

        mockMvc.perform(get("/api/users/" + user.getId() + "/avatar"))
                .andExpect(status().isNotFound());
    }
}
