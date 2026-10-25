package com.assesshub.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Uniform error envelope. {@code code} is a stable machine-readable identifier
 * (for example ATTEMPT_EXPIRED) that the frontend can branch on.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        boolean success,
        int status,
        String error,
        String code,
        String message,
        String path,
        Instant timestamp,
        Map<String, String> fieldErrors) {

    public static ApiError of(int status, String error, String code, String message, String path) {
        return new ApiError(false, status, error, code, message, path, Instant.now(), null);
    }
}
