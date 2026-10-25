package com.assesshub.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 128) String password) {
    }

    public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserDtos.UserDto user) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 255) String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 128) String token,
            @NotBlank @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH, message = PasswordPolicy.MESSAGE)
            @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE) String newPassword) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH, message = PasswordPolicy.MESSAGE)
            @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE) String newPassword) {
    }

    public record TokenCheckResponse(boolean valid, String purpose, String email) {
    }
}
