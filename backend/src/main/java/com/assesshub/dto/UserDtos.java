package com.assesshub.dto;

import com.assesshub.domain.User;
import com.assesshub.domain.enums.RoleName;
import com.assesshub.domain.enums.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserDto(
            Long id,
            String email,
            String fullName,
            String department,
            String jobTitle,
            UserStatus status,
            boolean emailVerified,
            Set<RoleName> roles,
            boolean hasPassword,
            Instant lastLoginAt,
            Instant createdAt) {

        public static UserDto from(User u) {
            return new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getDepartment(), u.getJobTitle(),
                    u.getStatus(), u.isEmailVerified(), u.roleNames(), u.getPasswordHash() != null,
                    u.getLastLoginAt(), u.getCreatedAt());
        }
    }

    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 150) String fullName,
            @NotEmpty Set<RoleName> roles,
            @Size(max = 100) String department,
            @Size(max = 100) String jobTitle,
            Boolean sendSetupEmail) {
    }

    public record UpdateUserRequest(
            @NotBlank @Size(max = 150) String fullName,
            @NotEmpty Set<RoleName> roles,
            @NotNull UserStatus status,
            @Size(max = 100) String department,
            @Size(max = 100) String jobTitle) {
    }
}
