package com.assesshub.security;

import com.assesshub.domain.User;
import com.assesshub.domain.enums.RoleName;
import com.assesshub.domain.enums.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * Authenticated user as seen by Spring Security. Built from the database on every request,
 * so role changes and deactivation take effect immediately.
 */
public final class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String fullName;
    private final String passwordHash;
    private final Set<RoleName> roles;
    private final boolean active;
    private final boolean locked;

    public UserPrincipal(Long id, String email, String fullName, String passwordHash,
                         Set<RoleName> roles, boolean active, boolean locked) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.roles = roles.isEmpty() ? EnumSet.noneOf(RoleName.class) : EnumSet.copyOf(roles);
        this.active = active;
        this.locked = locked;
    }

    public static UserPrincipal from(User user) {
        boolean locked = user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now());
        return new UserPrincipal(user.getId(), user.getEmail(), user.getFullName(), user.getPasswordHash(),
                user.roleNames(), user.getStatus() == UserStatus.ACTIVE, locked);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public Set<RoleName> getRoles() {
        return roles;
    }

    public boolean hasRole(RoleName role) {
        return roles.contains(role);
    }

    /** Super Admin or Admin: organisation-wide visibility. */
    public boolean isAdminLevel() {
        return roles.contains(RoleName.SUPER_ADMIN) || roles.contains(RoleName.ADMIN);
    }

    public boolean isSuperAdmin() {
        return roles.contains(RoleName.SUPER_ADMIN);
    }

    /** Any role that can author assessments. */
    public boolean isStaff() {
        return roles.stream().anyMatch(RoleName::isStaff);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).toList();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !locked;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
