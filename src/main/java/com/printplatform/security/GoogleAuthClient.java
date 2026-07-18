package com.printplatform.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Component
public class GoogleAuthClient {

    @Value("${google.client.id:}")
    private String clientId;

    private GoogleIdTokenVerifier verifier;

    public record GoogleProfile(String googleId, String email, String firstName, String lastName, String picture) {}

    public GoogleProfile verify(String idTokenString) {
        GoogleIdToken idToken;
        try {
            idToken = getVerifier().verify(idTokenString);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Weryfikacja Google nie powiodła się.");
        }

        if (idToken == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Weryfikacja Google nie powiodła się.");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Weryfikacja Google nie powiodła się.");
        }

        String firstName = (String) payload.get("given_name");
        String lastName = (String) payload.get("family_name");
        String picture = (String) payload.get("picture");
        return new GoogleProfile(payload.getSubject(), payload.getEmail(), firstName, lastName, picture);
    }

    private GoogleIdTokenVerifier getVerifier() {
        if (verifier == null) {
            verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(clientId))
                    .build();
        }
        return verifier;
    }
}
