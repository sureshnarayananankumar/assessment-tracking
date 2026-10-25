package com.assesshub.security;

import com.assesshub.config.AppProperties;
import com.assesshub.domain.enums.RoleName;
import com.assesshub.support.MutableClock;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static AppProperties props(String secret) {
        return new AppProperties("http://localhost", null, new AppProperties.Jwt(secret, "assesshub", 15, 7),
                null, null, null, null, null, null, null);
    }

    private final UserPrincipal principal = new UserPrincipal(42L, "user@test.local", "User", null,
            Set.of(RoleName.RECRUITER), true, false);

    @Test
    void issuesAndValidatesAccessToken() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
        JwtService jwt = new JwtService(props("an-adequately-long-test-secret-value-0123456789"), clock);
        String token = jwt.generateAccessToken(principal);
        assertThat(jwt.validateAndGetUserId(token)).isEqualTo(42L);
        assertThat(jwt.accessTokenTtlSeconds()).isEqualTo(900);
    }

    @Test
    void rejectsExpiredToken() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
        JwtService jwt = new JwtService(props("an-adequately-long-test-secret-value-0123456789"), clock);
        String token = jwt.generateAccessToken(principal);
        clock.advance(Duration.ofMinutes(16));
        assertThatThrownBy(() -> jwt.validateAndGetUserId(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        MutableClock clock = new MutableClock(Instant.now());
        JwtService a = new JwtService(props("first-secret-value-that-is-long-enough-0123456789"), clock);
        JwtService b = new JwtService(props("second-secret-value-that-is-long-enough-987654321"), clock);
        String token = a.generateAccessToken(principal);
        assertThatThrownBy(() -> b.validateAndGetUserId(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void refusesMissingOrShortSecret() {
        MutableClock clock = new MutableClock(Instant.now());
        assertThatThrownBy(() -> new JwtService(props(""), clock)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService(props("too-short"), clock)).isInstanceOf(IllegalStateException.class);
    }
}
