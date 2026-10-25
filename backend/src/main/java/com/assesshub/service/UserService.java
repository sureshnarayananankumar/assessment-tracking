package com.assesshub.service;

import com.assesshub.common.PageResponse;
import com.assesshub.common.RequestUtils;
import com.assesshub.common.TextUtils;
import com.assesshub.common.exception.ApiException;
import com.assesshub.config.AppProperties;
import com.assesshub.domain.Role;
import com.assesshub.domain.User;
import com.assesshub.domain.enums.RoleName;
import com.assesshub.domain.enums.TokenPurpose;
import com.assesshub.domain.enums.UserStatus;
import com.assesshub.dto.UserDtos.CreateUserRequest;
import com.assesshub.dto.UserDtos.UpdateUserRequest;
import com.assesshub.dto.UserDtos.UserDto;
import com.assesshub.repository.RefreshTokenRepository;
import com.assesshub.repository.RoleRepository;
import com.assesshub.repository.UserRepository;
import com.assesshub.security.UserPrincipal;
import com.assesshub.service.email.EmailTemplateService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * User administration. Only Super Admins can grant or modify Admin / Super Admin accounts.
 */
@Service
public class UserService {

    private static final Set<RoleName> PRIVILEGED = EnumSet.of(RoleName.SUPER_ADMIN, RoleName.ADMIN);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthService authService;
    private final EmailTemplateService emailTemplates;
    private final ApplicationEventPublisher events;
    private final AuditService auditService;
    private final AppProperties props;
    private final Clock clock;

    public UserService(UserRepository userRepository, RoleRepository roleRepository,
                       RefreshTokenRepository refreshTokenRepository, AuthService authService,
                       EmailTemplateService emailTemplates, ApplicationEventPublisher events,
                       AuditService auditService, AppProperties props, Clock clock) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.authService = authService;
        this.emailTemplates = emailTemplates;
        this.events = events;
        this.auditService = auditService;
        this.props = props;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserDto> search(String search, RoleName role, UserStatus status, Pageable pageable) {
        return PageResponse.from(userRepository.search(TextUtils.likePattern(search), role, status, pageable), UserDto::from);
    }

    @Transactional(readOnly = true)
    public UserDto get(Long id) {
        return UserDto.from(find(id));
    }

    @Transactional
    public UserDto create(CreateUserRequest request, UserPrincipal actor) {
        assertMayGrant(actor, request.roles());
        String email = TextUtils.normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_EXISTS", "A user with this e-mail already exists");
        }
        User user = new User();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setDepartment(TextUtils.trimToNull(request.department()));
        user.setJobTitle(TextUtils.trimToNull(request.jobTitle()));
        user.setStatus(UserStatus.INVITED);
        user.setRoles(resolveRoles(request.roles()));
        user.setCreatedBy(userRepository.getReferenceById(actor.getId()));
        userRepository.save(user);

        if (request.sendSetupEmail() == null || request.sendSetupEmail()) {
            sendSetup(user);
        }
        auditService.log("USER_CREATED", "USER", user.getId(), "Roles: " + request.roles());
        return UserDto.from(user);
    }

    @Transactional
    public UserDto update(Long id, UpdateUserRequest request, UserPrincipal actor) {
        User user = find(id);
        boolean targetPrivileged = user.roleNames().stream().anyMatch(PRIVILEGED::contains);
        if (targetPrivileged && !actor.isSuperAdmin()) {
            throw ApiException.forbidden("Only a Super Admin can modify administrator accounts");
        }
        assertMayGrant(actor, request.roles());
        if (user.getId().equals(actor.getId())) {
            if (request.status() != UserStatus.ACTIVE) {
                throw ApiException.badRequest("SELF_DEACTIVATION", "You cannot deactivate your own account");
            }
            if (actor.isSuperAdmin() && !request.roles().contains(RoleName.SUPER_ADMIN)) {
                throw ApiException.badRequest("SELF_DEMOTION", "You cannot remove your own Super Admin role");
            }
        }
        boolean wasActiveSuperAdmin = user.hasRole(RoleName.SUPER_ADMIN) && user.getStatus() == UserStatus.ACTIVE;
        boolean remainsActiveSuperAdmin = request.roles().contains(RoleName.SUPER_ADMIN) && request.status() == UserStatus.ACTIVE;
        if (wasActiveSuperAdmin && !remainsActiveSuperAdmin && userRepository.countActiveWithRole(RoleName.SUPER_ADMIN) <= 1) {
            throw ApiException.conflict("LAST_SUPER_ADMIN", "At least one active Super Admin must remain");
        }

        String before = "roles=" + user.roleNames() + ", status=" + user.getStatus();
        user.setFullName(request.fullName().trim());
        user.setDepartment(TextUtils.trimToNull(request.department()));
        user.setJobTitle(TextUtils.trimToNull(request.jobTitle()));
        user.setRoles(resolveRoles(request.roles()));
        if (request.status() == UserStatus.DISABLED && user.getStatus() != UserStatus.DISABLED) {
            refreshTokenRepository.revokeAllForUser(user.getId(), clock.instant());
        }
        if (request.status() == UserStatus.ACTIVE && user.getPasswordHash() == null) {
            // Cannot activate without a password; keep INVITED until set-up is complete.
            user.setStatus(UserStatus.INVITED);
        } else {
            user.setStatus(request.status());
        }
        auditService.log("USER_UPDATED", "USER", user.getId(),
                before + " -> roles=" + request.roles() + ", status=" + user.getStatus());
        return UserDto.from(user);
    }

    /** Soft delete: the account is disabled so historical assessments and audit trails stay intact. */
    @Transactional
    public void deactivate(Long id, UserPrincipal actor) {
        User user = find(id);
        if (user.getId().equals(actor.getId())) {
            throw ApiException.badRequest("SELF_DEACTIVATION", "You cannot deactivate your own account");
        }
        boolean targetPrivileged = user.roleNames().stream().anyMatch(PRIVILEGED::contains);
        if (targetPrivileged && !actor.isSuperAdmin()) {
            throw ApiException.forbidden("Only a Super Admin can deactivate administrator accounts");
        }
        if (user.hasRole(RoleName.SUPER_ADMIN) && user.getStatus() == UserStatus.ACTIVE
                && userRepository.countActiveWithRole(RoleName.SUPER_ADMIN) <= 1) {
            throw ApiException.conflict("LAST_SUPER_ADMIN", "At least one active Super Admin must remain");
        }
        user.setStatus(UserStatus.DISABLED);
        refreshTokenRepository.revokeAllForUser(user.getId(), clock.instant());
        auditService.log("USER_DISABLED", "USER", user.getId(), user.getEmail());
    }

    @Transactional
    public void resendSetup(Long id, UserPrincipal actor) {
        User user = find(id);
        if (user.getStatus() == UserStatus.DISABLED) {
            throw ApiException.conflict("USER_DISABLED", "The account is disabled");
        }
        if (user.roleNames().stream().anyMatch(PRIVILEGED::contains) && !actor.isSuperAdmin()) {
            throw ApiException.forbidden("Only a Super Admin can manage administrator accounts");
        }
        sendSetup(user);
        auditService.log("USER_SETUP_EMAIL_SENT", "USER", user.getId(), user.getEmail());
    }

    private void sendSetup(User user) {
        int hours = props.security().accountSetupHours();
        TokenPurpose purpose = user.getPasswordHash() == null ? TokenPurpose.ACCOUNT_SETUP : TokenPurpose.PASSWORD_RESET;
        String raw = authService.createToken(user, purpose, Duration.ofHours(hours), RequestUtils.clientIp());
        events.publishEvent(purpose == TokenPurpose.ACCOUNT_SETUP
                ? emailTemplates.accountSetup(user, raw, hours)
                : emailTemplates.passwordReset(user, raw, hours * 60));
    }

    private void assertMayGrant(UserPrincipal actor, Set<RoleName> roles) {
        if (!actor.isSuperAdmin() && roles.stream().anyMatch(PRIVILEGED::contains)) {
            throw ApiException.forbidden("Only a Super Admin can grant Admin or Super Admin roles");
        }
    }

    private Set<Role> resolveRoles(Set<RoleName> names) {
        Set<Role> roles = new HashSet<>(roleRepository.findByNameIn(names));
        if (roles.size() != names.size()) {
            throw ApiException.badRequest("INVALID_ROLE", "One or more roles do not exist");
        }
        return roles;
    }

    private User find(Long id) {
        return userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }
}
