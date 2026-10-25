package com.assesshub.service;

import com.assesshub.common.PageResponse;
import com.assesshub.common.TextUtils;
import com.assesshub.common.TokenUtils;
import com.assesshub.common.exception.ApiException;
import com.assesshub.domain.AssessmentAssignment;
import com.assesshub.domain.AssessmentAttempt;
import com.assesshub.domain.AssessmentInvitation;
import com.assesshub.domain.AssessmentResult;
import com.assesshub.domain.Assignment;
import com.assesshub.domain.Role;
import com.assesshub.domain.User;
import com.assesshub.domain.enums.AssigneeStatus;
import com.assesshub.domain.enums.InvitationStatus;
import com.assesshub.domain.enums.RoleName;
import com.assesshub.domain.enums.UserStatus;
import com.assesshub.dto.AssignmentDtos.AssigneeDto;
import com.assesshub.dto.AssignmentDtos.InviteRequest;
import com.assesshub.dto.AssignmentDtos.InviteResultDto;
import com.assesshub.dto.AssignmentDtos.InviteeRequest;
import com.assesshub.dto.MiscDtos.AcceptInvitationRequest;
import com.assesshub.dto.MiscDtos.AcceptInvitationResponse;
import com.assesshub.dto.MiscDtos.InvitationInfoDto;
import com.assesshub.repository.AssessmentAssignmentRepository;
import com.assesshub.repository.AssessmentAttemptRepository;
import com.assesshub.repository.AssessmentInvitationRepository;
import com.assesshub.repository.AssessmentResultRepository;
import com.assesshub.repository.AssignmentRepository;
import com.assesshub.repository.RoleRepository;
import com.assesshub.repository.UserRepository;
import com.assesshub.security.UserPrincipal;
import com.assesshub.service.email.EmailTemplateService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assigning assessments to people by e-mail, secure invitation links, reminders and revocation.
 */
@Service
public class InvitationService {

    private final AssignmentRepository assignmentRepository;
    private final AssessmentAssignmentRepository assigneeRepository;
    private final AssessmentInvitationRepository invitationRepository;
    private final AssessmentAttemptRepository attemptRepository;
    private final AssessmentResultRepository resultRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AccessGuard accessGuard;
    private final AttemptService attemptService;
    private final AuthService authService;
    private final SettingsService settings;
    private final EmailTemplateService emailTemplates;
    private final ApplicationEventPublisher events;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final Clock clock;

    public InvitationService(AssignmentRepository assignmentRepository, AssessmentAssignmentRepository assigneeRepository,
                             AssessmentInvitationRepository invitationRepository, AssessmentAttemptRepository attemptRepository,
                             AssessmentResultRepository resultRepository, UserRepository userRepository,
                             RoleRepository roleRepository, AccessGuard accessGuard, AttemptService attemptService,
                             AuthService authService, SettingsService settings, EmailTemplateService emailTemplates,
                             ApplicationEventPublisher events, PasswordEncoder passwordEncoder, AuditService auditService,
                             Clock clock) {
        this.assignmentRepository = assignmentRepository;
        this.assigneeRepository = assigneeRepository;
        this.invitationRepository = invitationRepository;
        this.attemptRepository = attemptRepository;
        this.resultRepository = resultRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.accessGuard = accessGuard;
        this.attemptService = attemptService;
        this.authService = authService;
        this.settings = settings;
        this.emailTemplates = emailTemplates;
        this.events = events;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ================================================================== staff side

    @Transactional
    public InviteResultDto invite(Long assignmentId, InviteRequest request, UserPrincipal principal) {
        Assignment a = findAssignment(assignmentId);
        accessGuard.assertCanManage(principal, a);
        if (!a.getStatus().isOpen()) {
            throw ApiException.conflict("NOT_PUBLISHED", "Publish the assessment before inviting candidates");
        }
        Instant now = clock.instant();
        if (request.dueAt() != null && !request.dueAt().isAfter(now)) {
            throw ApiException.badRequest("INVALID_DUE_DATE", "The due date must be in the future");
        }
        Role candidateRole = roleRepository.findByName(RoleName.CANDIDATE)
                .orElseThrow(() -> new IllegalStateException("CANDIDATE role missing"));
        User actor = userRepository.getReferenceById(principal.getId());

        Map<String, InviteeRequest> unique = new LinkedHashMap<>();
        request.invitees().forEach(i -> unique.putIfAbsent(TextUtils.normalizeEmail(i.email()), i));

        int invited = 0;
        int reinvited = 0;
        int skipped = 0;
        List<String> messages = new ArrayList<>();
        for (Map.Entry<String, InviteeRequest> entry : unique.entrySet()) {
            String email = entry.getKey();
            User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
            if (user == null) {
                user = new User();
                user.setEmail(email);
                String name = TextUtils.trimToNull(entry.getValue().fullName());
                user.setFullName(name != null ? name : email.substring(0, email.indexOf('@')));
                user.setStatus(UserStatus.INVITED);
                user.setRoles(new HashSet<>(Set.of(candidateRole)));
                user.setCreatedBy(actor);
                userRepository.save(user);
            } else if (user.getStatus() == UserStatus.DISABLED) {
                skipped++;
                messages.add(email + ": account is disabled");
                continue;
            }

            AssessmentAssignment aa = assigneeRepository.findByAssignmentIdAndUserId(a.getId(), user.getId()).orElse(null);
            if (aa == null) {
                aa = new AssessmentAssignment();
                aa.setAssignment(a);
                aa.setUser(user);
                aa.setAssignedBy(actor);
                aa.setStatus(AssigneeStatus.ASSIGNED);
                aa.setAssignedAt(now);
                aa.setDueAt(request.dueAt());
                assigneeRepository.save(aa);
                invited++;
            } else {
                if (aa.getAttemptsUsed() >= a.getMaxAttempts() && aa.getStatus() == AssigneeStatus.COMPLETED) {
                    skipped++;
                    messages.add(email + ": already completed all attempts");
                    continue;
                }
                if (aa.getStatus() == AssigneeStatus.REVOKED || aa.getStatus() == AssigneeStatus.EXPIRED) {
                    aa.setStatus(aa.getAttemptsUsed() > 0 ? AssigneeStatus.COMPLETED : AssigneeStatus.ASSIGNED);
                }
                if (request.dueAt() != null) {
                    aa.setDueAt(request.dueAt());
                }
                reinvited++;
                messages.add(email + ": invitation re-sent (previous link revoked)");
            }
            issueInvitation(aa, a, user, actor, now, request.message(), false);
        }
        auditService.log("CANDIDATES_INVITED", "ASSIGNMENT", a.getId(),
                "invited=" + invited + ", reinvited=" + reinvited + ", skipped=" + skipped);
        return new InviteResultDto(invited, reinvited, skipped, messages);
    }

    @Transactional
    public void remind(Long assignmentId, Long assigneeId, UserPrincipal principal) {
        Assignment a = findAssignment(assignmentId);
        accessGuard.assertCanManage(principal, a);
        AssessmentAssignment aa = assigneeRepository.findByIdAndAssignmentId(assigneeId, assignmentId)
                .orElseThrow(() -> ApiException.notFound("Assignee"));
        if (!a.getStatus().isOpen()) {
            throw ApiException.conflict("NOT_OPEN", "Reminders can only be sent for open assessments");
        }
        if (aa.getStatus() != AssigneeStatus.ASSIGNED && aa.getStatus() != AssigneeStatus.IN_PROGRESS) {
            throw ApiException.conflict("NOT_PENDING", "Reminders can only be sent to candidates who have not finished");
        }
        sendReminder(aa, userRepository.getReferenceById(principal.getId()));
        auditService.log("INVITATION_REMINDER", "ASSIGNMENT", assignmentId, aa.getUser().getEmail());
    }

    /** Also used by the scheduler (actor may be null). */
    @Transactional
    public void sendReminder(AssessmentAssignment aa, User actor) {
        Instant now = clock.instant();
        issueInvitation(aa, aa.getAssignment(), aa.getUser(), actor, now, null, true);
        aa.setReminderCount(aa.getReminderCount() + 1);
        aa.setLastReminderAt(now);
    }

    @Transactional
    public void revoke(Long assignmentId, Long assigneeId, UserPrincipal principal) {
        Assignment a = findAssignment(assignmentId);
        accessGuard.assertCanManage(principal, a);
        AssessmentAssignment aa = assigneeRepository.findByIdAndAssignmentId(assigneeId, assignmentId)
                .orElseThrow(() -> ApiException.notFound("Assignee"));
        if (aa.getStatus() == AssigneeStatus.REVOKED) {
            return;
        }
        aa.setStatus(AssigneeStatus.REVOKED);
        invitationRepository.revokeActiveForAssignee(aa.getId(), clock.instant());
        attemptService.terminateActiveAttempt(aa, "Access revoked by the assessor");
        auditService.log("ASSIGNEE_REVOKED", "ASSIGNMENT", assignmentId, aa.getUser().getEmail());
    }

    @Transactional(readOnly = true)
    public PageResponse<AssigneeDto> listAssignees(Long assignmentId, AssigneeStatus status, String search,
                                                   Pageable pageable, UserPrincipal principal) {
        Assignment a = findAssignment(assignmentId);
        accessGuard.assertCanManage(principal, a);
        return PageResponse.from(
                assigneeRepository.searchByAssignment(assignmentId, status, TextUtils.likePattern(search), pageable),
                aa -> toAssigneeDto(aa, a));
    }

    private AssigneeDto toAssigneeDto(AssessmentAssignment aa, Assignment a) {
        AssessmentInvitation inv = invitationRepository
                .findFirstByAssessmentAssignmentIdOrderByCreatedAtDescIdDesc(aa.getId()).orElse(null);
        List<AssessmentAttempt> attempts = attemptRepository.findByAssessmentAssignmentIdOrderByAttemptNumberDesc(aa.getId());
        AssessmentAttempt latest = attempts.isEmpty() ? null : attempts.get(0);
        AssessmentResult result = latest == null ? null : resultRepository.findByAttemptId(latest.getId()).orElse(null);
        Instant now = clock.instant();
        InvitationStatus invStatus = inv == null ? null
                : (inv.getStatus().isUsable() && !inv.getExpiresAt().isAfter(now) ? InvitationStatus.EXPIRED : inv.getStatus());
        return new AssigneeDto(aa.getId(), aa.getUser().getId(), aa.getUser().getEmail(), aa.getUser().getFullName(),
                aa.getStatus(), aa.getDueAt(), aa.effectiveDeadline(), aa.getAttemptsUsed(), a.getMaxAttempts(),
                aa.getAssignedAt(), aa.getCompletedAt(), invStatus, inv == null ? null : inv.getSentAt(),
                inv == null ? null : inv.getExpiresAt(), aa.getReminderCount(), aa.getLastReminderAt(),
                latest == null ? null : latest.getId(), latest == null ? null : latest.getStatus(),
                result == null ? null : result.getTotalScore(), result == null ? null : result.getPercentage(),
                result == null ? null : result.getPassed(), result == null ? null : result.getGradingStatus(),
                latest == null ? 0 : latest.getTabSwitchCount(), latest != null && latest.isFlagged());
    }

    private void issueInvitation(AssessmentAssignment aa, Assignment a, User user, User actor, Instant now,
                                 String message, boolean reminder) {
        invitationRepository.revokeActiveForAssignee(aa.getId(), now);
        String raw = TokenUtils.newToken();
        Instant expires = now.plus(Duration.ofHours(settings.getInt(SettingsService.INVITATION_EXPIRY_HOURS)));
        Instant deadline = aa.effectiveDeadline();
        if (deadline != null && deadline.isAfter(expires)) {
            expires = deadline;
        }
        AssessmentInvitation inv = new AssessmentInvitation();
        inv.setAssessmentAssignment(aa);
        inv.setAssignment(a);
        inv.setUser(user);
        inv.setEmail(user.getEmail());
        inv.setTokenHash(TokenUtils.sha256Hex(raw));
        inv.setStatus(InvitationStatus.PENDING);
        inv.setExpiresAt(expires);
        inv.setSentAt(now);
        inv.setInvitedBy(actor);
        invitationRepository.save(inv);
        events.publishEvent(emailTemplates.invitation(user, a, deadline, raw, TextUtils.trimToNull(message), reminder));
    }

    // ================================================================== public (token) side

    public record AcceptResult(AcceptInvitationResponse response, AuthService.AuthResult auth) {
    }

    @Transactional(readOnly = true)
    public InvitationInfoDto getInfo(String token) {
        AssessmentInvitation inv = resolveUsable(token);
        Assignment a = inv.getAssignment();
        User user = inv.getUser();
        return new InvitationInfoDto(a.getId(), a.getTitle(), a.getDescription(), a.getInstructions(),
                a.getDurationMinutes(), a.getStartAt(), inv.getAssessmentAssignment().effectiveDeadline(),
                a.getQuestions().size(), user.getFullName(), user.getEmail(), settings.orgName(),
                user.isStaff(), user.getPasswordHash() != null);
    }

    @Transactional
    public AcceptResult accept(String token, AcceptInvitationRequest request, String ip, String userAgent) {
        AssessmentInvitation inv = resolveUsable(token);
        Instant now = clock.instant();
        User user = inv.getUser();
        Long assignmentId = inv.getAssignment().getId();
        if (user.isStaff()) {
            // Privileged accounts must authenticate with their password; links never grant staff sessions.
            return new AcceptResult(new AcceptInvitationResponse(true, null, assignmentId,
                    "Sign in with your account to start this assessment."), null);
        }
        if (request != null && request.password() != null && user.getPasswordHash() == null) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        if (user.getStatus() == UserStatus.INVITED) {
            user.setStatus(UserStatus.ACTIVE);
        }
        user.setEmailVerified(true);
        user.setLastLoginAt(now);
        if (inv.getStatus() == InvitationStatus.PENDING) {
            inv.setStatus(InvitationStatus.ACCEPTED);
            inv.setAcceptedAt(now);
        }
        inv.setLastUsedAt(now);
        auditService.logAs(user.getId(), user.getEmail(), "INVITATION_ACCEPTED", "ASSIGNMENT", assignmentId, null);
        AuthService.AuthResult auth = authService.issueTokens(user, ip, userAgent);
        return new AcceptResult(new AcceptInvitationResponse(false, auth.response(), assignmentId, null), auth);
    }

    private AssessmentInvitation resolveUsable(String token) {
        if (!TokenUtils.looksValid(token)) {
            throw ApiException.notFound("Invitation");
        }
        AssessmentInvitation inv = invitationRepository.findByTokenHash(TokenUtils.sha256Hex(token))
                .orElseThrow(() -> ApiException.notFound("Invitation"));
        Instant now = clock.instant();
        if (inv.getStatus() == InvitationStatus.REVOKED) {
            throw new ApiException(HttpStatus.GONE, "INVITATION_REVOKED",
                    "This invitation link is no longer valid. A newer link may have been sent to you.");
        }
        if (!inv.isUsableAt(now)) {
            throw new ApiException(HttpStatus.GONE, "INVITATION_EXPIRED", "This invitation link has expired.");
        }
        if (inv.getAssessmentAssignment().getStatus() == AssigneeStatus.REVOKED
                || inv.getUser().getStatus() == UserStatus.DISABLED) {
            throw new ApiException(HttpStatus.GONE, "INVITATION_REVOKED", "This invitation has been withdrawn.");
        }
        return inv;
    }

    private Assignment findAssignment(Long id) {
        return assignmentRepository.findById(id).orElseThrow(() -> ApiException.notFound("Assessment"));
    }
}
