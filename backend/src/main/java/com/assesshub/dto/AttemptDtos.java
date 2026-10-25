package com.assesshub.dto;

import com.assesshub.domain.enums.ActivityEventType;
import com.assesshub.domain.enums.AttemptStatus;
import com.assesshub.domain.enums.QuestionType;
import com.assesshub.domain.enums.ViolationAction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AttemptDtos {

    private AttemptDtos() {
    }

    /** Option as shown to candidates: never includes the correct flag. */
    public record CandidateOptionDto(Long id, String text) {
    }

    /** Question as shown to candidates: no reference answers, accepted answers or grading guidance. */
    public record AttemptQuestionDto(
            Long questionId,
            int index,
            QuestionType type,
            String title,
            String body,
            BigDecimal marks,
            String codingLanguage,
            String starterCode,
            String allowedFileTypes,
            Integer maxFileSizeMb,
            List<CandidateOptionDto> options) {
    }

    public record SavedAnswerDto(
            Long questionId,
            List<Long> selectedOptionIds,
            String textAnswer,
            String codeAnswer,
            String codeLanguage,
            String fileName,
            Long fileSize,
            Instant savedAt) {
    }

    public record AttemptViewDto(
            Long attemptId,
            Long assignmentId,
            String title,
            String instructions,
            AttemptStatus status,
            int attemptNumber,
            Instant startedAt,
            Instant deadlineAt,
            Instant serverTime,
            long remainingSeconds,
            int durationMinutes,
            int gracePeriodSeconds,
            boolean monitoringEnabled,
            int warningThreshold,
            int maxViolations,
            ViolationAction violationAction,
            int tabSwitchCount,
            int focusLossCount,
            int violationCount,
            Instant lastSavedAt,
            Instant submittedAt,
            String submissionReason,
            List<AttemptQuestionDto> questions,
            List<SavedAnswerDto> answers) {
    }

    public record AnswerInput(
            @NotNull Long questionId,
            @Size(max = 50) List<Long> selectedOptionIds,
            @Size(max = 100000) String textAnswer,
            @Size(max = 200000) String codeAnswer,
            @Size(max = 40) String codeLanguage) {
    }

    public record SaveAnswersRequest(@NotNull @Size(max = 500) List<@Valid AnswerInput> answers) {
    }

    public record SaveAnswersResponse(Instant savedAt, long remainingSeconds, int savedCount, AttemptStatus status) {
    }

    public record ActivityEventInput(
            @NotNull ActivityEventType type,
            Instant occurredAt,
            @PositiveOrZero Long durationMs,
            @Size(max = 500) String details) {
    }

    public record ActivityBatchRequest(@NotEmpty @Size(max = 100) List<@Valid ActivityEventInput> events) {
    }

    public record ActivityResponse(
            int tabSwitchCount,
            int focusLossCount,
            int violationCount,
            int warningThreshold,
            int maxViolations,
            ViolationAction violationAction,
            boolean flagged,
            AttemptStatus status,
            boolean newViolation,
            String message) {
    }

    public record SubmitResponse(Long attemptId, AttemptStatus status, Instant submittedAt, String message,
                                 boolean resultReleased) {
    }

    public record ActivityLogDto(
            Long id,
            ActivityEventType eventType,
            Instant clientTimestamp,
            Instant serverTimestamp,
            Long durationMs,
            String details,
            boolean countedAsViolation) {
    }
}
