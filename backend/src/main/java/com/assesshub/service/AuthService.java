package com.assesshub.service;

import com.assesshub.common.TextUtils;
import com.assesshub.common.TokenUtils;
import com.assesshub.common.exception.ApiException;
import com.assesshub.config.AppProperties;
import com.assesshub.domain.PasswordResetToken;
import com.assesshub.domain.RefreshToken;
import com.assesshub.domain.User;
import com.assesshub.domain.enums.TokenPurpose;
import com.assesshub.domain.enums.UserStatus;
import com.assesshub.dto.AuthDtos.AuthResponse;
import com.assesshub.dto.AuthDtos.ChangePasswordRequest;
import com.assesshub.dto.AuthDtos.LoginRequest;
import com.assesshub.dto.AuthDtos.ResetPasswordRequest;
import com.assesshub.dto.AuthDtos.TokenCheckResponse;
import com.assesshub.dto.UserDtos.UserDto;
import com.assesshub.repository.PasswordResetTokenRepository;
import com.assesshub.repository.RefreshTokenRepository;
import com.assesshub.repository.UserRepository;
import com.assesshub.security.JwtService;
import com.assesshub.security.UserPrincipal;
import com.assesshub.service.email.EmailTemplateService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Login, token refresh/rotation, logout, password reset and account set-up.
 */
@Service
public class AuthService {

    /** Access token plus the raw refresh token to be placed in an HttpOnly cookie. */
    public record AuthResult(AuthResponse response, String refreshToken, Duration refreshTtl) {
    }

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final EmailTemplateService emailTemplates;
    private final ApplicationEventPublisher events;
    private final AppProperties props;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                       PasswordResetTokenRepository resetTokenRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, AuditService auditService, EmailTemplateService emailTemplates,
                       ApplicationEventPublisher events, AppProperties props, Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.emailTemplates = emailTemplates;
        this.events = events;
        this.props = props;
        this.clock = clock;
        // Used to keep response time constant for unknown e-mails (prevents account enumeration).
        this.dummyHash = passwordEncoder.encode(TokenUtils.newToken());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult login(LoginRequest request, String ip, String userAgent) {
        String email = TextUtils.normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        Instant now = clock.instant();

        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            auditService.logAs(null, email, "LOGIN_FAILED", "USER", null, "Unknown e-mail");
            throw invalidCredentials();
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            auditService.logAs(user.getId(), user.getEmail(), "LOGIN_BLOCKED", "USER", user.getId(), "Account locked");
            throw new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                    "Too many failed attempts. Try again in a few minutes or reset your password.");
        }
        boolean passwordOk = user.getPasswordHash() != null && passwordEncoder.matches(request.password(), user.getPasswordHash());
        if (!passwordOk) {
            if (user.getPasswordHash() == null) {
                passwordEncoder.matches(request.password(), dummyHash);
            }
            int failures = user.getFailedLoginAttempts() + 1;
            if (failures >= props.security().maxFailedLogins()) {
                user.setLockedUntil(now.plus(Duration.ofMinutes(props.security().lockoutMinutes())));
                user.setFailedLoginAttempts(0);
                auditService.logAs(user.getId(), user.getEmail(), "ACCOUNT_LOCKED", "USER", user.getId(),
                        "Locked after " + failures + " failed logins");
            } else {
                user.setFailedLoginAttempts(failures);
            }
            auditService.logAs(user.getId(), user.getEmail(), "LOGIN_FAILED", "USER", user.getId(), "Wrong password");
            throw invalidCredentials();
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            auditService.logAs(user.getId(), user.getEmail(), "LOGIN_BLOCKED", "USER", user.getId(), "Status " + user.getStatus());
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "This account is not active");
        }
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        auditService.logAs(user.getId(), user.getEmail(), "LOGIN_SUCCESS", "USER", user.getId(), null);
        return issueTokens(user, ip, userAgent);
    }

    /** Creates a new access token and a rotating refresh token for the given (active) user. */
    @Transactional
    public AuthResult issueTokens(User user, String ip, String userAgent) {
        Instant now = clock.instant();
        String raw = TokenUtils.newToken();
        Duration ttl = Duration.ofDays(Math.max(1, props.jwt().refreshTokenDays()));
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(TokenUtils.sha256Hex(raw));
        token.setExpiresAt(now.plus(ttl));
        token.setIpAddress(TextUtils.truncate(ip, 45));
        token.setUserAgent(TextUtils.truncate(userAgent, 512));
        refreshTokenRepository.save(token);

        String access = jwtService.generateAccessToken(UserPrincipal.from(user));
        AuthResponse response = new AuthResponse(access, "Bearer", jwtService.accessTokenTtlSeconds(), UserDto.from(user));
        return new AuthResult(response, raw, ttl);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult refresh(String rawToken, String ip, String userAgent) {
        if (!TokenUtils.looksValid(rawToken)) {
            throw ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Session expired. Please sign in again.");
        }
        Instant now = clock.instant();
        RefreshToken token = refreshTokenRepository.findByTokenHash(TokenUtils.sha256Hex(rawToken))
                .orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Session expired. Please sign in again."));
        User user = token.getUser();
        if (token.getRevokedAt() != null) {
            // A rotated token was presented again: treat as theft and end every session of this user.
            refreshTokenRepository.revokeAllForUser(user.getId(), now);
            auditService.logAs(user.getId(), user.getEmail(), "REFRESH_TOKEN_REUSE", "USER", user.getId(),
                    "All sessions revoked");
            throw ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Session expired. Please sign in again.");
        }
        if (!token.getExpiresAt().isAfter(now) || user.getStatus() != UserStatus.ACTIVE) {
            token.setRevokedAt(now);
            throw ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Session expired. Please sign in again.");
        }
        token.setRevokedAt(now);
        return issueTokens(user, ip, userAgent);
    }

    @Transactional
    public void logout(String rawToken) {
        if (!TokenUtils.looksValid(rawToken)) {
            return;
        }
        refreshTokenRepository.findByTokenHash(TokenUtils.sha256Hex(rawToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(clock.instant());
            }
            User user = token.getUser();
            auditService.logAs(user.getId(), user.getEmail(), "LOGOUT", "USER", user.getId(), null);
        });
    }

    @Transactional(readOnly = true)
    public UserDto me(UserPrincipal principal) {
        return userRepository.findById(principal.getId()).map(UserDto::from)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHORIZED", "Authentication is required"));
    }

    /** Always succeeds from the caller's perspective to avoid revealing which e-mails exist. */
    @Transactional
    public void forgotPassword(String email, String ip) {
        userRepository.findByEmailIgnoreCase(TextUtils.normalizeEmail(email))
                .filter(u -> u.getStatus() != UserStatus.DISABLED)
                .ifPresent(user -> {
                    int minutes = props.security().passwordResetMinutes();
                    String raw = createToken(user, TokenPurpose.PASSWORD_RESET, Duration.ofMinutes(minutes), ip);
                    events.publishEvent(emailTemplates.passwordReset(user, raw, minutes));
                    auditService.logAs(user.getId(), user.getEmail(), "PASSWORD_RESET_REQUESTED", "USER", user.getId(), null);
                });
    }

    /** Creates a single-use token (invalidating older ones) and returns the raw value for the e-mail link. */
    @Transactional
    public String createToken(User user, TokenPurpose purpose, Duration ttl, String ip) {
        Instant now = clock.instant();
        resetTokenRepository.invalidateAllForUser(user.getId(), now);
        String raw = TokenUtils.newToken();
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(TokenUtils.sha256Hex(raw));
        token.setPurpose(purpose);
        token.setExpiresAt(now.plus(ttl));
        token.setRequestIp(TextUtils.truncate(ip, 45));
        resetTokenRepository.save(token);
        return raw;
    }

    @Transactional(readOnly = true)
    public TokenCheckResponse checkResetToken(String rawToken) {
        PasswordResetToken token = findUsableResetToken(rawToken);
        if (token == null) {
            return new TokenCheckResponse(false, null, null);
        }
        return new TokenCheckResponse(true, token.getPurpose().name(), maskEmail(token.getUser().getEmail()));
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = findUsableResetToken(request.token());
        if (token == null || token.getUser().getStatus() == UserStatus.DISABLED) {
            throw ApiException.badRequest("INVALID_TOKEN", "This link is invalid or has expired. Please request a new one.");
        }
        Instant now = clock.instant();
        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setEmailVerified(true); // the link was delivered to this mailbox
        if (user.getStatus() == UserStatus.INVITED) {
            user.setStatus(UserStatus.ACTIVE);
        }
        token.setUsedAt(now);
        resetTokenRepository.invalidateAllForUser(user.getId(), now);
        refreshTokenRepository.revokeAllForUser(user.getId(), now);
        auditService.logAs(user.getId(), user.getEmail(),
                token.getPurpose() == TokenPurpose.ACCOUNT_SETUP ? "ACCOUNT_SETUP_COMPLETED" : "PASSWORD_RESET",
                "USER", user.getId(), null);
    }

    @Transactional
    public AuthResult changePassword(UserPrincipal principal, ChangePasswordRequest request, String ip, String userAgent) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHORIZED", "Authentication is required"));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("INVALID_PASSWORD", "Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("PASSWORD_REUSED", "The new password must differ from the current one");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokenRepository.revokeAllForUser(user.getId(), clock.instant());
        auditService.logAs(user.getId(), user.getEmail(), "PASSWORD_CHANGED", "USER", user.getId(), "Other sessions revoked");
        return issueTokens(user, ip, userAgent);
    }

    private PasswordResetToken findUsableResetToken(String rawToken) {
        if (!TokenUtils.looksValid(rawToken)) {
            return null;
        }
        Instant now = clock.instant();
        return resetTokenRepository.findByTokenHash(TokenUtils.sha256Hex(rawToken))
                .filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(now))
                .orElse(null);
    }

    private static ApiException invalidCredentials() {
        return ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid e-mail or password");
    }

    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(at, 0));
        }
        return email.charAt(0) + "***" + email.substring(at - 1);
    }
}
