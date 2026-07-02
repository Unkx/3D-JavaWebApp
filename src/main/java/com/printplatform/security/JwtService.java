package com.printplatform.security;

import com.printplatform.model.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    /** The base64 secret committed to the repo for local dev/tests. Must never sign real tokens. */
    private static final String INSECURE_DEFAULT_SECRET =
            "dGhpcy1pcy1hLXZlcnktc2VjcmV0LWtleS10aGF0LWlzLWxvbmctZW5vdWdo";

    @Value("${app.jwt.secret}")
    private String secretKey;

    @Value("${app.jwt.expiration}")
    private long expiration;

    /** Escape hatch for local dev and tests only. Never enable in a real environment. */
    @Value("${app.security.allow-insecure-secret:false}")
    private boolean allowInsecureSecret;

    @PostConstruct
    void validateSecret() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "app.jwt.secret (env JWT_SECRET) must be set to a base64-encoded key of at least 256 bits.");
        }
        byte[] decoded;
        try {
            decoded = Decoders.BASE64.decode(secretKey);
        } catch (RuntimeException e) {
            throw new IllegalStateException("app.jwt.secret (env JWT_SECRET) must be valid base64.", e);
        }
        if (decoded.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret (env JWT_SECRET) must decode to at least 256 bits (32 bytes); got "
                            + (decoded.length * 8) + " bits.");
        }
        if (INSECURE_DEFAULT_SECRET.equals(secretKey) && !allowInsecureSecret) {
            throw new IllegalStateException(
                    "Refusing to start with the committed default JWT secret. Set a unique JWT_SECRET "
                            + "(e.g. `openssl rand -base64 48`), or set app.security.allow-insecure-secret=true "
                            + "for local development only.");
        }
    }

    public String generateToken(User user) {
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId().toString())
                .claim("role", user.getRole().name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token, User user) {
        try {
            return extractEmail(token).equals(user.getEmail()) && !isTokenExpired(token);
        } catch (JwtException e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return resolver.apply(claims);
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
    }
}
