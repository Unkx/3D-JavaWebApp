package com.printplatform.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacebookAuthClientTest {

    @Mock
    private RestTemplate restTemplate;

    private FacebookAuthClient client;

    @BeforeEach
    void setUp() {
        client = new FacebookAuthClient();
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(client, "appId", "our-app-id");
        ReflectionTestUtils.setField(client, "appSecret", "our-app-secret");
    }

    private void stubDebugToken(boolean isValid, String appId) {
        String json = "{\"data\":{\"is_valid\":" + isValid + ",\"app_id\":\"" + appId + "\"}}";
        when(restTemplate.getForEntity(
                argThat((URI uri) -> uri != null && uri.toString().contains("debug_token")), eq(String.class)))
                .thenReturn(new ResponseEntity<>(json, HttpStatus.OK));
    }

    private void stubProfile(String id, String email, String firstName, String lastName) {
        String emailField = email == null ? "" : ",\"email\":\"" + email + "\"";
        String json = "{\"id\":\"" + id + "\"" + emailField
                + ",\"first_name\":\"" + firstName + "\",\"last_name\":\"" + lastName + "\"}";
        when(restTemplate.getForEntity(
                argThat((URI uri) -> uri != null && uri.toString().contains("/me")), eq(String.class)))
                .thenReturn(new ResponseEntity<>(json, HttpStatus.OK));
    }

    @Test
    void verify_validToken_returnsProfile() {
        stubDebugToken(true, "our-app-id");
        stubProfile("fb123", "user@example.com", "Jan", "Kowalski");

        FacebookAuthClient.FacebookProfile profile = client.verify("some-token");

        assertThat(profile.facebookId()).isEqualTo("fb123");
        assertThat(profile.email()).isEqualTo("user@example.com");
        assertThat(profile.firstName()).isEqualTo("Jan");
        assertThat(profile.lastName()).isEqualTo("Kowalski");
    }

    @Test
    void verify_missingEmail_returnsProfileWithNullEmail() {
        stubDebugToken(true, "our-app-id");
        stubProfile("fb123", null, "Jan", "Kowalski");

        FacebookAuthClient.FacebookProfile profile = client.verify("some-token");

        assertThat(profile.email()).isNull();
    }

    @Test
    void verify_tokenAppIdMismatch_throwsUnauthorized() {
        stubDebugToken(true, "someone-elses-app-id");

        assertThatThrownBy(() -> client.verify("some-token"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void verify_tokenNotValid_throwsUnauthorized() {
        stubDebugToken(false, "our-app-id");

        assertThatThrownBy(() -> client.verify("some-token"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void verify_graphApiThrows_throwsUnauthorized() {
        when(restTemplate.getForEntity(
                argThat((URI uri) -> uri.toString().contains("debug_token")), eq(String.class)))
                .thenThrow(new RuntimeException("network down"));

        assertThatThrownBy(() -> client.verify("some-token"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
