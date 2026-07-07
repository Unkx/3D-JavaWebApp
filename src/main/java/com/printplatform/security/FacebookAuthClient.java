package com.printplatform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Component
public class FacebookAuthClient {

    @Value("${facebook.app.id:}")
    private String appId;

    @Value("${facebook.app.secret:}")
    private String appSecret;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record FacebookProfile(String facebookId, String email, String firstName, String lastName) {}

    public FacebookProfile verify(String accessToken) {
        verifyTokenBelongsToOurApp(accessToken);
        return fetchProfile(accessToken);
    }

    private void verifyTokenBelongsToOurApp(String accessToken) {
        URI uri = UriComponentsBuilder.fromHttpUrl("https://graph.facebook.com/debug_token")
                .queryParam("input_token", accessToken)
                .queryParam("access_token", appId + "|" + appSecret)
                .build()
                .toUri();
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            JsonNode data = objectMapper.readTree(response.getBody()).path("data");
            boolean valid = data.path("is_valid").asBoolean(false);
            String tokenAppId = data.path("app_id").asText("");
            if (!valid || !appId.equals(tokenAppId)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Weryfikacja Facebook nie powiodła się.");
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Weryfikacja Facebook nie powiodła się.");
        }
    }

    private FacebookProfile fetchProfile(String accessToken) {
        URI uri = UriComponentsBuilder.fromHttpUrl("https://graph.facebook.com/me")
                .queryParam("fields", "id,email,first_name,last_name")
                .queryParam("access_token", accessToken)
                .build()
                .toUri();
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            String id = root.path("id").asText(null);
            String email = root.hasNonNull("email") ? root.path("email").asText() : null;
            String firstName = root.path("first_name").asText(null);
            String lastName = root.path("last_name").asText(null);
            return new FacebookProfile(id, email, firstName, lastName);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Weryfikacja Facebook nie powiodła się.");
        }
    }
}
