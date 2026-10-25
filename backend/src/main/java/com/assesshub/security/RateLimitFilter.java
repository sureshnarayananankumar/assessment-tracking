package com.assesshub.security;

import com.assesshub.common.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window, per-IP rate limiting for unauthenticated, brute-forceable endpoints
 * (login, password reset, token refresh, invitation links).
 * In-memory: for several backend replicas use a shared store (Redis) or limit at the gateway.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000;

    private final int limitPerMinute;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(int limitPerMinute, ObjectMapper objectMapper, Clock clock) {
        this.limitPerMinute = Math.max(1, limitPerMinute);
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!"POST".equalsIgnoreCase(request.getMethod()) && !path.startsWith("/api/invitations/")) {
            return true;
        }
        return !(path.equals("/api/auth/login")
                || path.equals("/api/auth/forgot-password")
                || path.equals("/api/auth/reset-password")
                || path.equals("/api/auth/refresh")
                || path.startsWith("/api/invitations/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = clock.millis();
        String bucket = bucketName(request.getRequestURI());
        String key = request.getRemoteAddr() + "|" + bucket;
        int limit = bucket.equals("refresh") ? limitPerMinute * 3 : limitPerMinute;

        Window window = windows.compute(key, (k, w) -> (w == null || now - w.start >= WINDOW_MS) ? new Window(now) : w);
        int count = window.count.incrementAndGet();
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> now - e.getValue().start >= WINDOW_MS);
        }
        if (count > limit) {
            long retryAfter = Math.max(1, (WINDOW_MS - (now - window.start)) / 1000);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ApiError.of(429, "Too Many Requests", "RATE_LIMITED",
                    "Too many requests. Please wait " + retryAfter + " seconds and try again.", request.getRequestURI()));
            return;
        }
        chain.doFilter(request, response);
    }

    private static String bucketName(String path) {
        if (path.startsWith("/api/invitations/")) {
            return "invitation";
        }
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /** Clears all counters (used by tests). */
    public void reset() {
        windows.clear();
    }

    private static final class Window {
        final long start;
        final AtomicInteger count = new AtomicInteger();

        Window(long start) {
            this.start = start;
        }
    }
}
