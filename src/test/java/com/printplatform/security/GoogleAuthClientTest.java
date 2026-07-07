package com.printplatform.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.security.GeneralSecurityException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleAuthClientTest {

    @Mock
    private GoogleIdTokenVerifier verifier;

    private GoogleAuthClient client;

    @BeforeEach
    void setUp() {
        client = new GoogleAuthClient();
        ReflectionTestUtils.setField(client, "verifier", verifier);
    }

    private GoogleIdToken buildToken(String subject, String email, boolean emailVerified,
                                      String firstName, String lastName) throws Exception {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject(subject);
        payload.setEmail(email);
        payload.setEmailVerified(emailVerified);
        payload.set("given_name", firstName);
        payload.set("family_name", lastName);
        return new GoogleIdToken(new GoogleIdToken.Header(), payload, new byte[0], new byte[0]);
    }

    @Test
    void verify_validToken_returnsProfile() throws Exception {
        when(verifier.verify("some-id-token"))
                .thenReturn(buildToken("google123", "user@example.com", true, "Jan", "Kowalski"));

        GoogleAuthClient.GoogleProfile profile = client.verify("some-id-token");

        assertThat(profile.googleId()).isEqualTo("google123");
        assertThat(profile.email()).isEqualTo("user@example.com");
        assertThat(profile.firstName()).isEqualTo("Jan");
        assertThat(profile.lastName()).isEqualTo("Kowalski");
    }

    @Test
    void verify_unverifiedEmail_throwsUnauthorized() throws Exception {
        when(verifier.verify("some-id-token"))
                .thenReturn(buildToken("google123", "user@example.com", false, "Jan", "Kowalski"));

        assertThatThrownBy(() -> client.verify("some-id-token"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void verify_invalidSignature_throwsUnauthorized() throws Exception {
        when(verifier.verify("bad-token")).thenReturn(null);

        assertThatThrownBy(() -> client.verify("bad-token"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void verify_verifierThrows_throwsUnauthorized() throws Exception {
        when(verifier.verify("bad-token")).thenThrow(new GeneralSecurityException("boom"));

        assertThatThrownBy(() -> client.verify("bad-token"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
