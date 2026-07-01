package com.printplatform.security;

import com.printplatform.model.Role;
import com.printplatform.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Plain unit tests for {@link JwtService} — no Spring context, fields normally
 * populated via @Value are injected with ReflectionTestUtils.
 */
class JwtServiceTest {

    private static final String SECRET =
            "dGhpcy1pcy1hLXZlcnktc2VjcmV0LWtleS10aGF0LWlzLWxvbmctZW5vdWdo";
    private static final String OTHER_SECRET =
            "YW5vdGhlci1jb21wbGV0ZWx5LWRpZmZlcmVudC1zaWduaW5nLWtleS1mb3ItdGVzdHM=";
    private static final long ONE_HOUR_MILLIS = 3_600_000L;

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = newJwtService(SECRET, ONE_HOUR_MILLIS);

        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("driver@example.com");
        user.setRole(Role.USER);
    }

    private static JwtService newJwtService(String secret, long expirationMillis) {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey", secret);
        ReflectionTestUtils.setField(service, "expiration", expirationMillis);
        return service;
    }

    @Test
    void generateTokenProducesAParseableJwtWithExpectedClaims() {
        String token = jwtService.generateToken(user);

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);

        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo(user.getEmail());
        assertThat(claims.get("userId", String.class)).isEqualTo(user.getId().toString());
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(new Date());
    }

    @Test
    void extractEmailReturnsSubjectOfSelfIssuedToken() {
        String token = jwtService.generateToken(user);

        assertThat(jwtService.extractEmail(token)).isEqualTo(user.getEmail());
    }

    @Test
    void isTokenValidReturnsTrueForTokenIssuedForThatUser() {
        String token = jwtService.generateToken(user);

        assertThat(jwtService.isTokenValid(token, user)).isTrue();
    }

    @Test
    void isTokenValidReturnsFalseWhenSubjectDoesNotMatchGivenUser() {
        String token = jwtService.generateToken(user);

        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        otherUser.setEmail("someone-else@example.com");
        otherUser.setRole(Role.USER);

        assertThat(jwtService.isTokenValid(token, otherUser)).isFalse();
    }

    @Test
    void isTokenValidReturnsFalseForExpiredToken() {
        // Expiration in the past: token is already expired the instant it's minted.
        JwtService expiringJwtService = newJwtService(SECRET, -1_000L);
        String token = expiringJwtService.generateToken(user);

        assertThat(expiringJwtService.isTokenValid(token, user)).isFalse();
    }

    @Test
    void extractEmailThrowsForExpiredToken() {
        JwtService expiringJwtService = newJwtService(SECRET, -1_000L);
        String token = expiringJwtService.generateToken(user);

        assertThrows(JwtException.class, () -> expiringJwtService.extractEmail(token));
    }

    @Test
    void isTokenValidReturnsFalseWhenSignedWithADifferentKey() {
        JwtService differentKeyService = newJwtService(OTHER_SECRET, ONE_HOUR_MILLIS);
        String token = differentKeyService.generateToken(user);

        // jwtService only trusts SECRET, not OTHER_SECRET.
        assertThat(jwtService.isTokenValid(token, user)).isFalse();
    }

    @Test
    void isTokenValidReturnsFalseForTamperedToken() {
        String token = jwtService.generateToken(user);
        String[] parts = token.split("\\.");
        // Flip the last character of the signature segment to corrupt it.
        char lastChar = parts[2].charAt(parts[2].length() - 1);
        char replacement = lastChar == 'A' ? 'B' : 'A';
        String tamperedSignature = parts[2].substring(0, parts[2].length() - 1) + replacement;
        String tamperedToken = parts[0] + "." + parts[1] + "." + tamperedSignature;

        assertThat(jwtService.isTokenValid(tamperedToken, user)).isFalse();
    }

    @Test
    void isTokenValidReturnsFalseForMalformedToken() {
        assertThat(jwtService.isTokenValid("this-is-not-a-jwt", user)).isFalse();
    }

    @Test
    void extractEmailThrowsForMalformedToken() {
        assertThrows(JwtException.class, () -> jwtService.extractEmail("this-is-not-a-jwt"));
    }
}
