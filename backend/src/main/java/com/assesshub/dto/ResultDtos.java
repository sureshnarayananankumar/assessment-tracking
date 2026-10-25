package com.assesshub.dto;

import com.assesshub.domain.enums.AnswerGradingStatus;
import com.assesshub.domain.enums.AttemptStatus;
import com.assesshub.domain.enums.QuestionType;
import com.assesshub.domain.enums.ResultGradingStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ResultDtos {

    private ResultDtos() {
    }

    public record ResultDto(
            Long id,
            Long attemptId,
            int attemptNumber,
            AttemptStatus attemptStatus,
            Long assignmentId,
            String assignmentTitle,
            Long userId,
            String candidateName,
            String candidateEmail,
            BigDecimal autoScore,
            BigDecimal manualScore,
            BigDecimal totalScore,
            BigDecimal maxScore,
            BigDecimal percentage,
            Boolean passed,
            ResultGradingStatus gradingStatus,
            boolean released,
            Instant releasedAt,
            String feedback,
            String gradedByName,
            Instant gradedAt,
            int tabSwitchCount,
            int focusLossCount,
            int violationCount,
            boolean flagged,
            String activitySummary,
            Instant startedAt,
            Instant submittedAt) {
    }

    public record ReviewOptionDto(Long id, String text, boolean correct, boolean selected) {
    }

    /** Staff-only view of an answer, including the answer key and grading guidance. */
    public record ReviewAnswerDto(
            Long answerId,
            Long questionId,
            int index,
            QuestionType type,
            String title,
            String body,
            BigDecimal maxMarks,
            BigDecimal marksAwarded,
            Boolean correct,
            AnswerGradingStatus gradingStatus,
            String evaluatorComment,
            String gradedByName,
            Instant gradedAt,
            List<ReviewOptionDto> options,
            String textAnswer,
            String codeAnswer,
            String codeLanguage,
            String fileName,
            Long fileSize,
            String referenceAnswer,
            String evaluationGuidelines,
            List<String> acceptedAnswers,
            Instant savedAt) {
    }

    public record AttemptReviewDto(
            Long attemptId,
            Long assignmentId,
            String assignmentTitle,
            BigDecimal passingMarks,
            Long userId,
            String candidateName,
            String candidateEmail,
            int attemptNumber,
            AttemptStatus status,
            Instant startedAt,
            Instant deadlineAt,
            Instant submittedAt,
            String submissionReason,
            String ipAddress,
            String userAgent,
            int tabSwitchCount,
            int focusLossCount,
            int violationCount,
            long totalAwayMs,
            boolean flagged,
            String flagReason,
            ResultDto result,
            List<ReviewAnswerDto> answers) {
    }

    public record GradeItem(
            @NotNull Long answerId,
            @NotNull @DecimalMin("0.0") BigDecimal marks,
            @Size(max = 5000) String comment) {
    }

    public record GradeRequest(
            @NotNull @Size(max = 500) List<@Valid GradeItem> grades,
            @Size(max = 5000) String feedback,
            Boolean release) {
    }

    public record FlagRequest(boolean flagged, @Size(max = 255) String reason) {
    }

    /** {@code correct} is null unless the assessment is configured to reveal correct answers. */
    public record CandidateOptionResultDto(Long id, String text, boolean selected, Boolean correct) {
    }

    public record CandidateQuestionResultDto(
            Long questionId,
            int index,
            QuestionType type,
            String title,
            String body,
            BigDecimal maxMarks,
            BigDecimal marksAwarded,
            Boolean correct,
            String evaluatorComment,
            List<CandidateOptionResultDto> options,
            String textAnswer,
            String codeAnswer,
            String fileName) {
    }

    public record CandidateResultDto(
            Long attemptId,
            Long assignmentId,
            String assignmentTitle,
            AttemptStatus status,
            Instant startedAt,
            Instant submittedAt,
            String submissionReason,
            boolean released,
            ResultGradingStatus gradingStatus,
            BigDecimal totalScore,
            BigDecimal maxScore,
            BigDecimal percentage,
            Boolean passed,
            BigDecimal passingMarks,
            String feedback,
            boolean showCorrectAnswers,
            List<CandidateQuestionResultDto> questions,
            String message) {
    }
}
