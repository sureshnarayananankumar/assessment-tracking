package com.assesshub.security;

import com.assesshub.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Issues and validates short-lived HS256 access tokens.
 */
@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final Clock clock;

    public JwtService(AppProperties props, Clock clock) {
        String secret = props.jwt().secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is not configured. Generate one with: openssl rand -base64 48");
        }
        this.key = Keys.hmacShaKeyFor(decodeSecret(secret.trim()));
        this.issuer = props.jwt().issuer();
        this.accessTokenTtl = Duration.ofMinutes(Math.max(1, props.jwt().accessTokenMinutes()));
        this.clock = clock;
    }

    private static byte[] decodeSecret(String secret) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException notBase64) {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < MIN_SECRET_BYTES) {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes (256 bits)");
        }
        return bytes;
    }

    public String generateAccessToken(UserPrincipal principal) {
        Instant now = clock.instant();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(String.valueOf(principal.getId()))
                .claim("email", principal.getEmail())
                .claim("roles", principal.getRoles().stream().map(Enum::name).toList())
                .claim("typ", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * @return the user id contained in a valid access token
     * @throws JwtException if the token is invalid, expired or not an access token
     */
    public Long validateAndGetUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .clock(() -> Date.from(clock.instant()))
                .clockSkewSeconds(30)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!"access".equals(claims.get("typ", String.class))) {
            throw new JwtException("Not an access token");
        }
        return Long.valueOf(claims.getSubject());
    }

    @SuppressWarnings("unchecked")
    public List<String> rolesOf(Claims claims) {
        return (List<String>) claims.get("roles", List.class);
    }

    public long accessTokenTtlSeconds() {
        return accessTokenTtl.toSeconds();
    }
}
