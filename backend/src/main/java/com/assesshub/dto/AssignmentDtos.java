package com.assesshub.dto;

import com.assesshub.domain.enums.AssessmentType;
import com.assesshub.domain.enums.AssigneeStatus;
import com.assesshub.domain.enums.AssignmentStatus;
import com.assesshub.domain.enums.AttemptStatus;
import com.assesshub.domain.enums.Difficulty;
import com.assesshub.domain.enums.InvitationStatus;
import com.assesshub.domain.enums.ReleasePolicy;
import com.assesshub.domain.enums.ResultGradingStatus;
import com.assesshub.domain.enums.ViolationAction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AssignmentDtos {

    private AssignmentDtos() {
    }

    public record AssignmentQuestionRequest(
            @NotNull Long questionId,
            @DecimalMin("0.0") @DecimalMax("1000.0") BigDecimal marks) {
    }

    public record AssignmentRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 10000) String description,
            @Size(max = 20000) String instructions,
            @Size(max = 10000) String evaluationCriteria,
            @Size(max = 100) String skillCategory,
            @NotNull Difficulty difficulty,
            @NotNull AssessmentType assessmentType,
            @NotNull @DecimalMin("0.0") @DecimalMax("100000.0") BigDecimal passingMarks,
            Instant startAt,
            Instant endAt,
            @NotNull @Min(1) @Max(1440) Integer durationMinutes,
            @NotNull @Min(1) @Max(20) Integer maxAttempts,
            @Min(0) @Max(600) Integer gracePeriodSeconds,
            boolean shuffleQuestions,
            boolean shuffleOptions,
            ReleasePolicy resultsReleasePolicy,
            boolean showCorrectAnswers,
            Boolean monitoringEnabled,
            @Min(1) @Max(100) Integer warningThreshold,
            @Min(1) @Max(100) Integer maxViolations,
            ViolationAction violationAction,
            @Valid @Size(max = 200) List<AssignmentQuestionRequest> questions) {
    }

    public record AssignmentQuestionDto(Long id, Long questionId, int displayOrder, BigDecimal marks,
                                        QuestionDtos.QuestionDto question) {
    }

    public record AttachmentDto(Long id, String originalName, String contentType, long sizeBytes, Instant createdAt) {
    }

    public record AssignmentDto(
            Long id,
            String title,
            String description,
            String instructions,
            String evaluationCriteria,
            String skillCategory,
            Difficulty difficulty,
            AssessmentType assessmentType,
            BigDecimal totalMarks,
            BigDecimal passingMarks,
            Instant startAt,
            Instant endAt,
            int durationMinutes,
            int maxAttempts,
            int gracePeriodSeconds,
            boolean shuffleQuestions,
            boolean shuffleOptions,
            AssignmentStatus status,
            ReleasePolicy resultsReleasePolicy,
            boolean showCorrectAnswers,
            boolean resultsReleased,
            Instant resultsReleasedAt,
            boolean monitoringEnabled,
            int warningThreshold,
            int maxViolations,
            ViolationAction violationAction,
            Instant publishedAt,
            Long ownerId,
            String ownerName,
            int questionCount,
            long assigneeCount,
            long completedCount,
            long attemptCount,
            boolean hasAttempts,
            List<AssignmentQuestionDto> questions,
            List<AttachmentDto> attachments,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record StatusChangeRequest(@NotNull AssignmentStatus status) {
    }

    public record InviteeRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @Size(max = 150) String fullName) {
    }

    public record InviteRequest(
            @NotEmpty @Size(max = 200) List<@Valid InviteeRequest> invitees,
            Instant dueAt,
            @Size(max = 2000) String message) {
    }

    public record InviteResultDto(int invited, int reinvited, int skipped, List<String> messages) {
    }

    public record AssigneeDto(
            Long id,
            Long userId,
            String email,
            String fullName,
            AssigneeStatus status,
            Instant dueAt,
            Instant effectiveDeadline,
            int attemptsUsed,
            int maxAttempts,
            Instant assignedAt,
            Instant completedAt,
            InvitationStatus invitationStatus,
            Instant invitationSentAt,
            Instant invitationExpiresAt,
            int reminderCount,
            Instant lastReminderAt,
            Long latestAttemptId,
            AttemptStatus latestAttemptStatus,
            BigDecimal latestScore,
            BigDecimal latestPercentage,
            Boolean passed,
            ResultGradingStatus gradingStatus,
            int tabSwitchCount,
            boolean flagged) {
    }
}
