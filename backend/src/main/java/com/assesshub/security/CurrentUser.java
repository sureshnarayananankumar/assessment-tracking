package com.assesshub.security;

import com.assesshub.common.exception.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Static accessor for the authenticated principal.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<UserPrincipal> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public static UserPrincipal get() {
        return find().orElseThrow(() -> ApiException.unauthorized("UNAUTHORIZED", "Authentication is required"));
    }
}
