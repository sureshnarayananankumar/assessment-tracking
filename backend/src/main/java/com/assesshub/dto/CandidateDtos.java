package com.assesshub.dto;

import com.assesshub.domain.enums.AssessmentType;
import com.assesshub.domain.enums.AttemptStatus;
import com.assesshub.domain.enums.CandidateAssessmentState;
import com.assesshub.domain.enums.Difficulty;
import com.assesshub.domain.enums.ViolationAction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class CandidateDtos {

    private CandidateDtos() {
    }

    public record MyAssessmentDto(
            Long assignmentId,
            Long assigneeId,
            String title,
            String description,
            String skillCategory,
            Difficulty difficulty,
            AssessmentType assessmentType,
            int durationMinutes,
            Instant startAt,
            Instant deadline,
            int maxAttempts,
            int attemptsUsed,
            CandidateAssessmentState state,
            int questionCount,
            BigDecimal totalMarks,
            Long activeAttemptId,
            Long latestAttemptId,
            AttemptStatus latestAttemptStatus,
            Instant latestSubmittedAt,
            boolean resultReleased,
            BigDecimal score,
            BigDecimal percentage,
            Boolean passed,
            Instant assignedAt) {
    }

    public record AssessmentInstructionsDto(
            MyAssessmentDto summary,
            String instructions,
            String evaluationCriteria,
            BigDecimal passingMarks,
            boolean monitoringEnabled,
            int warningThreshold,
            int maxViolations,
            ViolationAction violationAction,
            int gracePeriodSeconds,
            List<AssignmentDtos.AttachmentDto> attachments,
            boolean canStart,
            String unavailableReason,
            Instant serverTime) {
    }
}
