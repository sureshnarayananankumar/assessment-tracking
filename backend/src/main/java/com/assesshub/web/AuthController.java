package com.assesshub.web;

import com.assesshub.common.ApiResponse;
import com.assesshub.common.exception.ApiException;
import com.assesshub.dto.AuthDtos.AuthResponse;
import com.assesshub.dto.AuthDtos.ChangePasswordRequest;
import com.assesshub.dto.AuthDtos.ForgotPasswordRequest;
import com.assesshub.dto.AuthDtos.LoginRequest;
import com.assesshub.dto.AuthDtos.ResetPasswordRequest;
import com.assesshub.dto.AuthDtos.TokenCheckResponse;
import com.assesshub.dto.UserDtos.UserDto;
import com.assesshub.security.CurrentUser;
import com.assesshub.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Login, token refresh, logout and password management")
public class AuthController {

    private final AuthService authService;
    private final WebUtils webUtils;

    public AuthController(AuthService authService, WebUtils webUtils) {
        this.authService = authService;
        this.webUtils = webUtils;
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with e-mail and password", description = "Returns a short-lived access token and sets an HttpOnly refresh cookie.")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        AuthService.AuthResult result = authService.login(request, WebUtils.ip(http), WebUtils.userAgent(http));
        return withCookie(result);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange the refresh cookie for a new access token (rotates the refresh token)")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(name = WebUtils.REFRESH_COOKIE, required = false) String refreshToken, HttpServletRequest http) {
        requireAjaxHeader(http);
        if (refreshToken == null) {
            throw ApiException.unauthorized("NO_SESSION", "Not signed in");
        }
        try {
            return withCookie(authService.refresh(refreshToken, WebUtils.ip(http), WebUtils.userAgent(http)));
        } catch (ApiException ex) {
            if (ex.getStatus() == HttpStatus.UNAUTHORIZED) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .header(HttpHeaders.SET_COOKIE, webUtils.clearRefreshCookie().toString())
                        .body(new ApiResponse<>(false, null, ex.getMessage(), java.time.Instant.now()));
            }
            throw ex;
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the refresh token and clear the cookie")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = WebUtils.REFRESH_COOKIE, required = false) String refreshToken, HttpServletRequest http) {
        requireAjaxHeader(http);
        authService.logout(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, webUtils.clearRefreshCookie().toString())
                .body(ApiResponse.message("Signed out"));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset e-mail (always returns 200)")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
        authService.forgotPassword(request.email(), WebUtils.ip(http));
        return ApiResponse.message("If an account exists for this e-mail, a reset link has been sent.");
    }

    @GetMapping("/reset-password/validate")
    @Operation(summary = "Check whether a reset / account set-up token is still valid")
    public ApiResponse<TokenCheckResponse> validateResetToken(@RequestParam String token) {
        return ApiResponse.ok(authService.checkResetToken(token));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Set a new password using a reset or account set-up token")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.message("Your password has been updated. You can now sign in.");
    }

    @GetMapping("/me")
    @Operation(summary = "Current user profile")
    public ApiResponse<UserDto> me() {
        return ApiResponse.ok(authService.me(CurrentUser.get()));
    }

    @PutMapping("/me/password")
    @Operation(summary = "Change the current user's password (revokes other sessions)")
    public ResponseEntity<ApiResponse<AuthResponse>> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                                    HttpServletRequest http) {
        return withCookie(authService.changePassword(CurrentUser.get(), request, WebUtils.ip(http), WebUtils.userAgent(http)));
    }

    private ResponseEntity<ApiResponse<AuthResponse>> withCookie(AuthService.AuthResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, webUtils.refreshCookie(result).toString())
                .body(ApiResponse.ok(result.response()));
    }

    /**
     * Cookie-authenticated endpoints additionally require a custom header, which browsers never
     * attach to cross-site form posts (defence in depth on top of SameSite=Strict).
     */
    private static void requireAjaxHeader(HttpServletRequest http) {
        if (!"XMLHttpRequest".equals(http.getHeader("X-Requested-With"))) {
            throw ApiException.forbidden("Missing X-Requested-With header");
        }
    }
}
