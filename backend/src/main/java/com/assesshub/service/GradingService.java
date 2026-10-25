package com.assesshub.service;

import com.assesshub.common.TextUtils;
import com.assesshub.common.exception.ApiException;
import com.assesshub.domain.AssessmentAnswer;
import com.assesshub.domain.AssessmentAttempt;
import com.assesshub.domain.AssessmentResult;
import com.assesshub.domain.Assignment;
import com.assesshub.domain.Question;
import com.assesshub.domain.QuestionOption;
import com.assesshub.domain.User;
import com.assesshub.domain.enums.AnswerGradingStatus;
import com.assesshub.domain.enums.ReleasePolicy;
import com.assesshub.domain.enums.ResultGradingStatus;
import com.assesshub.dto.ResultDtos.GradeItem;
import com.assesshub.dto.ResultDtos.GradeRequest;
import com.assesshub.repository.AssessmentAnswerRepository;
import com.assesshub.repository.AssessmentResultRepository;
import com.assesshub.service.email.EmailTemplateService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Automatic grading of objective questions, manual grading of descriptive/coding/file answers,
 * result aggregation and result release. Marks are always computed on the server.
 */
@Service
public class GradingService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final AssessmentAnswerRepository answerRepository;
    private final AssessmentResultRepository resultRepository;
    private final EmailTemplateService emailTemplates;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;

    public GradingService(AssessmentAnswerRepository answerRepository, AssessmentResultRepository resultRepository,
                          EmailTemplateService emailTemplates, ApplicationEventPublisher events, ObjectMapper objectMapper) {
        this.answerRepository = answerRepository;
        this.resultRepository = resultRepository;
        this.emailTemplates = emailTemplates;
        this.events = events;
        this.objectMapper = objectMapper;
    }

    /** Auto-grades a finalised attempt and creates/updates its result. */
    @Transactional
    public AssessmentResult gradeAttempt(AssessmentAttempt attempt, Instant now) {
        List<AssessmentAnswer> answers = answerRepository.findByAttemptIdWithQuestion(attempt.getId());
        answers.forEach(a -> autoGrade(a, now));
        return recompute(attempt, answers, null, null, false, now);
    }

    @Transactional
    public AssessmentResult applyManualGrades(AssessmentAttempt attempt, GradeRequest request, User grader, Instant now) {
        if (!attempt.getStatus().isFinal()) {
            throw ApiException.conflict("ATTEMPT_IN_PROGRESS", "The attempt must be submitted before it can be graded");
        }
        List<AssessmentAnswer> answers = answerRepository.findByAttemptIdWithQuestion(attempt.getId());
        Map<Long, AssessmentAnswer> byId = answers.stream()
                .collect(Collectors.toMap(AssessmentAnswer::getId, Function.identity()));
        for (GradeItem item : request.grades()) {
            AssessmentAnswer answer = byId.get(item.answerId());
            if (answer == null) {
                throw ApiException.badRequest("INVALID_ANSWER", "Answer " + item.answerId() + " does not belong to this attempt");
            }
            BigDecimal marks = item.marks().setScale(2, RoundingMode.HALF_UP);
            if (marks.compareTo(answer.getMaxMarks()) > 0) {
                throw ApiException.badRequest("MARKS_EXCEED_MAX",
                        "Marks for question " + (answer.getDisplayOrder() + 1) + " cannot exceed " + answer.getMaxMarks());
            }
            answer.setMarksAwarded(marks);
            answer.setGradingStatus(AnswerGradingStatus.MANUALLY_GRADED);
            answer.setCorrect(marks.compareTo(answer.getMaxMarks()) == 0 && marks.signum() > 0 ? Boolean.TRUE
                    : marks.signum() == 0 ? Boolean.FALSE : null);
            answer.setEvaluatorComment(TextUtils.trimToNull(item.comment()));
            answer.setGradedBy(grader);
            answer.setGradedAt(now);
        }
        String feedback = request.feedback() == null ? null : request.feedback().trim();
        return recompute(attempt, answers, feedback, grader, Boolean.TRUE.equals(request.release()), now);
    }

    void autoGrade(AssessmentAnswer answer, Instant now) {
        if (answer.getGradingStatus() == AnswerGradingStatus.MANUALLY_GRADED) {
            return;
        }
        Question q = answer.getQuestion();
        switch (q.getType()) {
            case MCQ_SINGLE, MCQ_MULTI, TRUE_FALSE -> {
                Set<Long> selected = TextUtils.parseIdList(answer.getSelectedOptionIds());
                Set<Long> correct = q.getOptions().stream().filter(QuestionOption::isCorrect)
                        .map(QuestionOption::getId).collect(Collectors.toSet());
                setAuto(answer, !selected.isEmpty() && selected.equals(correct), now);
            }
            case SHORT_ANSWER -> {
                List<String> accepted = TextUtils.splitLines(q.getAcceptedAnswers());
                if (!accepted.isEmpty()) {
                    setAuto(answer, matchesAccepted(answer.getTextAnswer(), accepted, q.isCaseSensitive()), now);
                } else if (!answer.isAnswered()) {
                    setAuto(answer, false, now);
                } else {
                    markPending(answer);
                }
            }
            default -> {
                // LONG_ANSWER, CODING, FILE_UPLOAD need a human evaluator; blanks score zero automatically.
                if (!answer.isAnswered()) {
                    setAuto(answer, false, now);
                } else {
                    markPending(answer);
                }
            }
        }
    }

    static boolean matchesAccepted(String answer, List<String> accepted, boolean caseSensitive) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        String normalized = normalize(answer);
        return accepted.stream().map(GradingService::normalize)
                .anyMatch(a -> caseSensitive ? a.equals(normalized) : a.equalsIgnoreCase(normalized));
    }

    private static String normalize(String s) {
        return s.trim().replaceAll("\\s+", " ");
    }

    private static void setAuto(AssessmentAnswer answer, boolean correct, Instant now) {
        answer.setCorrect(correct);
        answer.setMarksAwarded(correct ? answer.getMaxMarks() : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        answer.setGradingStatus(AnswerGradingStatus.AUTO_GRADED);
        answer.setGradedAt(now);
        answer.setGradedBy(null);
    }

    private static void markPending(AssessmentAnswer answer) {
        answer.setCorrect(null);
        answer.setMarksAwarded(null);
        answer.setGradingStatus(AnswerGradingStatus.PENDING);
    }

    private AssessmentResult recompute(AssessmentAttempt attempt, List<AssessmentAnswer> answers, String feedback,
                                       User grader, boolean forceRelease, Instant now) {
        AssessmentResult result = resultRepository.findByAttemptId(attempt.getId()).orElseGet(() -> {
            AssessmentResult r = new AssessmentResult();
            r.setAttempt(attempt);
            r.setAssignment(attempt.getAssignment());
            r.setUser(attempt.getUser());
            return r;
        });
        BigDecimal auto = BigDecimal.ZERO;
        BigDecimal manual = BigDecimal.ZERO;
        BigDecimal max = BigDecimal.ZERO;
        boolean pending = false;
        for (AssessmentAnswer a : answers) {
            max = max.add(a.getMaxMarks());
            switch (a.getGradingStatus()) {
                case AUTO_GRADED -> auto = auto.add(nz(a.getMarksAwarded()));
                case MANUALLY_GRADED -> manual = manual.add(nz(a.getMarksAwarded()));
                case PENDING -> pending = true;
            }
        }
        BigDecimal total = auto.add(manual);
        Assignment assignment = attempt.getAssignment();
        result.setAutoScore(scale(auto));
        result.setManualScore(scale(manual));
        result.setTotalScore(scale(total));
        result.setMaxScore(scale(max));
        result.setPercentage(max.signum() > 0
                ? total.multiply(HUNDRED).divide(max, 2, RoundingMode.HALF_UP).min(HUNDRED)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        result.setGradingStatus(pending ? ResultGradingStatus.PENDING_REVIEW : ResultGradingStatus.GRADED);
        result.setPassed(pending ? null : total.compareTo(assignment.getPassingMarks()) >= 0);

        result.setTabSwitchCount(attempt.getTabSwitchCount());
        result.setFocusLossCount(attempt.getFocusLossCount());
        result.setViolationCount(attempt.getViolationCount());
        result.setFlagged(attempt.isFlagged());
        result.setActivitySummary(activitySummary(attempt, answers));
        if (feedback != null) {
            result.setFeedback(feedback.isEmpty() ? null : feedback);
        }
        if (grader != null) {
            result.setGradedBy(grader);
            result.setGradedAt(now);
        }
        boolean shouldRelease = !pending && (forceRelease
                || assignment.getResultsReleasePolicy() == ReleasePolicy.AFTER_GRADING
                || assignment.isResultsReleased());
        AssessmentResult saved = resultRepository.save(result);
        if (shouldRelease) {
            release(saved, now);
        }
        return saved;
    }

    /** Makes a graded result visible to the candidate and notifies them by e-mail. */
    @Transactional
    public boolean release(AssessmentResult result, Instant now) {
        if (result.isReleased() || result.getGradingStatus() != ResultGradingStatus.GRADED) {
            return false;
        }
        result.setReleased(true);
        result.setReleasedAt(now);
        events.publishEvent(emailTemplates.resultReleased(result.getUser(), result.getAssignment(), result.getAttempt().getId()));
        return true;
    }

    private String activitySummary(AssessmentAttempt attempt, List<AssessmentAnswer> answers) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("tabSwitches", attempt.getTabSwitchCount());
        summary.put("focusLosses", attempt.getFocusLossCount());
        summary.put("violations", attempt.getViolationCount());
        summary.put("totalAwayMs", attempt.getTotalAwayMs());
        summary.put("flagged", attempt.isFlagged());
        summary.put("flagReason", attempt.getFlagReason());
        summary.put("answered", answers.stream().filter(AssessmentAnswer::isAnswered).count());
        summary.put("questions", answers.size());
        summary.put("status", attempt.getStatus().name());
        summary.put("note", "Focus events are indicators for human review, not proof of misconduct.");
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
