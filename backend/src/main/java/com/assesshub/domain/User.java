package com.assesshub.domain;

import com.assesshub.domain.enums.RoleName;
import com.assesshub.domain.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** BCrypt hash. Null until the user completes account set-up. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(length = 100)
    private String department;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    @Version
    private long version;

    public Set<RoleName> roleNames() {
        if (roles.isEmpty()) {
            return EnumSet.noneOf(RoleName.class);
        }
        return roles.stream().map(Role::getName).collect(Collectors.toCollection(() -> EnumSet.noneOf(RoleName.class)));
    }

    public boolean hasRole(RoleName role) {
        return roles.stream().anyMatch(r -> r.getName() == role);
    }

    public boolean isStaff() {
        return roles.stream().anyMatch(r -> r.getName().isStaff());
    }
}
