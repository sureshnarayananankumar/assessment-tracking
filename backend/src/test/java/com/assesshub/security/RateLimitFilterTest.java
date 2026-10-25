package com.assesshub.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.assesshub.support.MutableClock;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
    private final RateLimitFilter filter = new RateLimitFilter(2, new ObjectMapper().registerModule(new JavaTimeModule()), clock);

    private int call(String method, String uri) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRequestURI(uri);
        req.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res.getStatus();
    }

    @Test
    void limitsLoginAttemptsPerIpAndWindow() throws Exception {
        assertThat(call("POST", "/api/auth/login")).isEqualTo(200);
        assertThat(call("POST", "/api/auth/login")).isEqualTo(200);
        assertThat(call("POST", "/api/auth/login")).isEqualTo(429);
        clock.advance(Duration.ofSeconds(61));
        assertThat(call("POST", "/api/auth/login")).isEqualTo(200);
    }

    @Test
    void doesNotLimitOrdinaryApiCalls() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(call("GET", "/api/assignments")).isEqualTo(200);
        }
    }
}
