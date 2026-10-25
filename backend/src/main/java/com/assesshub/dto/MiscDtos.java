package com.assesshub.dto;

import com.assesshub.domain.enums.AttemptStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * DTOs for monitoring, dashboards, audit logs, settings and invitations.
 */
public final class MiscDtos {

    private MiscDtos() {
    }

    public record MonitoringAttemptDto(
            Long attemptId,
            Long assignmentId,
            String assignmentTitle,
            Long userId,
            String candidateName,
            String candidateEmail,
            int attemptNumber,
            AttemptStatus status,
            Instant startedAt,
            Instant deadlineAt,
            Instant submittedAt,
            long remainingSeconds,
            int tabSwitchCount,
            int focusLossCount,
            int violationCount,
            long totalAwayMs,
            boolean flagged,
            String flagReason,
            Instant lastSavedAt) {
    }

    public record CountItem(String label, long count) {
    }

    public record StaffDashboardDto(
            long totalAssignments,
            Map<String, Long> assignmentsByStatus,
            long totalInvited,
            long pendingAssessments,
            long inProgressAssessments,
            long completedAssessments,
            long expiredAssessments,
            BigDecimal averageScore,
            long passedCount,
            long failedCount,
            long pendingReviewCount,
            long flaggedAttempts,
            long totalTabSwitches,
            long totalFocusLosses,
            long totalAttempts,
            List<CountItem> scoreDistribution,
            List<MonitoringAttemptDto> recentAttempts,
            List<MonitoringAttemptDto> flaggedRecent) {
    }

    public record AuditLogDto(
            Long id,
            Long actorId,
            String actorEmail,
            String action,
            String entityType,
            String entityId,
            String details,
            String ipAddress,
            Instant createdAt) {
    }

    public record SettingDto(String key, String value, String description, Instant updatedAt) {
    }

    public record UpdateSettingsRequest(@NotEmpty Map<String, @Size(max = 1000) String> values) {
    }

    public record PublicSettingsDto(String orgName) {
    }

    public record InvitationInfoDto(
            Long assignmentId,
            String assignmentTitle,
            String description,
            String instructions,
            int durationMinutes,
            Instant startAt,
            Instant deadline,
            int questionCount,
            String candidateName,
            String candidateEmail,
            String orgName,
            boolean requiresLogin,
            boolean hasPassword) {
    }

    public record AcceptInvitationRequest(
            @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH, message = PasswordPolicy.MESSAGE)
            @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE) String password) {
    }

    public record AcceptInvitationResponse(boolean requiresLogin, AuthDtos.AuthResponse auth, Long assignmentId,
                                           String message) {
    }

    public record EmailNotificationDto(
            Long id,
            String recipientEmail,
            String template,
            String subject,
            String status,
            String errorMessage,
            String relatedType,
            Long relatedId,
            Instant sentAt,
            Instant createdAt) {
    }
}
