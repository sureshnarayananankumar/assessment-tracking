package com.assesshub.service;

import com.assesshub.common.PageResponse;
import com.assesshub.common.TextUtils;
import com.assesshub.common.exception.ApiException;
import com.assesshub.domain.AssessmentActivityLog;
import com.assesshub.domain.AssessmentAnswer;
import com.assesshub.domain.AssessmentAttempt;
import com.assesshub.domain.AssessmentResult;
import com.assesshub.domain.Assignment;
import com.assesshub.domain.Question;
import com.assesshub.domain.QuestionOption;
import com.assesshub.domain.enums.ActivityEventType;
import com.assesshub.domain.enums.AttemptStatus;
import com.assesshub.domain.enums.ResultGradingStatus;
import com.assesshub.dto.AttemptDtos.ActivityLogDto;
import com.assesshub.dto.MiscDtos.MonitoringAttemptDto;
import com.assesshub.dto.ResultDtos.AttemptReviewDto;
import com.assesshub.dto.ResultDtos.CandidateOptionResultDto;
import com.assesshub.dto.ResultDtos.CandidateQuestionResultDto;
import com.assesshub.dto.ResultDtos.CandidateResultDto;
import com.assesshub.dto.ResultDtos.FlagRequest;
import com.assesshub.dto.ResultDtos.GradeRequest;
import com.assesshub.dto.ResultDtos.ResultDto;
import com.assesshub.dto.ResultDtos.ReviewAnswerDto;
import com.assesshub.dto.ResultDtos.ReviewOptionDto;
import com.assesshub.repository.AssessmentActivityLogRepository;
import com.assesshub.repository.AssessmentAnswerRepository;
import com.assesshub.repository.AssessmentAttemptRepository;
import com.assesshub.repository.AssessmentResultRepository;
import com.assesshub.repository.AssignmentRepository;
import com.assesshub.repository.UserRepository;
import com.assesshub.security.UserPrincipal;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Results, staff review/grading, candidate result view, monitoring queries and CSV export.
 */
@Service
public class ResultService {

    private static final int EXPORT_LIMIT = 10_000;

    private final AssessmentAttemptRepository attemptRepository;
    private final AssessmentAnswerRepository answerRepository;
    private final AssessmentResultRepository resultRepository;
    private final AssessmentActivityLogRepository activityRepository;
    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final GradingService gradingService;
    private final AccessGuard accessGuard;
    private final AuditService auditService;
    private final Clock clock;

    public ResultService(AssessmentAttemptRepository attemptRepository, AssessmentAnswerRepository answerRepository,
                         AssessmentResultRepository resultRepository, AssessmentActivityLogRepository activityRepository,
                         AssignmentRepository assignmentRepository, UserRepository userRepository,
                         GradingService gradingService, AccessGuard accessGuard, AuditService auditService, Clock clock) {
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
        this.resultRepository = resultRepository;
        this.activityRepository = activityRepository;
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.gradingService = gradingService;
        this.accessGuard = accessGuard;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ================================================================== candidate

    @Transactional(readOnly = true)
    public CandidateResultDto candidateResult(Long attemptId, UserPrincipal principal) {
        AssessmentAttempt attempt = attemptRepository.findById(attemptId)
                .filter(t -> t.getUser().getId().equals(principal.getId()))
                .orElseThrow(() -> ApiException.notFound("Attempt"));
        Assignment a = attempt.getAssignment();
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            throw ApiException.conflict("ATTEMPT_IN_PROGRESS", "This attempt is still in progress");
        }
        AssessmentResult result = resultRepository.findByAttemptId(attemptId).orElse(null);
        if (result == null || !result.isReleased()) {
            return new CandidateResultDto(attempt.getId(), a.getId(), a.getTitle(), attempt.getStatus(),
                    attempt.getStartedAt(), attempt.getSubmittedAt(), attempt.getSubmissionReason(), false,
                    null, null, null, null, null, null, null, false, List.of(),
                    "Your submission has been received. Results will be visible once they are released.");
        }
        boolean reveal = a.isShowCorrectAnswers();
        List<CandidateQuestionResultDto> questions = answerRepository.findByAttemptIdWithQuestion(attemptId).stream()
                .map(ans -> {
                    Question q = ans.getQuestion();
                    Set<Long> selected = TextUtils.parseIdList(ans.getSelectedOptionIds());
                    List<CandidateOptionResultDto> options = q.getType().usesOptions()
                            ? q.getOptions().stream().sorted(Comparator.comparingInt(QuestionOption::getDisplayOrder))
                            .map(o -> new CandidateOptionResultDto(o.getId(), o.getOptionText(), selected.contains(o.getId()),
                                    reveal ? o.isCorrect() : null)).toList()
                            : null;
                    return new CandidateQuestionResultDto(q.getId(), ans.getDisplayOrder() + 1, q.getType(), q.getTitle(),
                            q.getBody(), ans.getMaxMarks(), ans.getMarksAwarded(), ans.getCorrect(),
                            ans.getEvaluatorComment(), options, ans.getTextAnswer(), ans.getCodeAnswer(),
                            ans.getFileOriginalName());
                }).toList();
        return new CandidateResultDto(attempt.getId(), a.getId(), a.getTitle(), attempt.getStatus(),
                attempt.getStartedAt(), attempt.getSubmittedAt(), attempt.getSubmissionReason(), true,
                result.getGradingStatus(), result.getTotalScore(), result.getMaxScore(), result.getPercentage(),
                result.getPassed(), a.getPassingMarks(), result.getFeedback(), reveal, questions, null);
    }

    // ================================================================== staff review & grading

    @Transactional(readOnly = true)
    public AttemptReviewDto review(Long attemptId, UserPrincipal principal) {
        AssessmentAttempt attempt = findManaged(attemptId, principal);
        return toReview(attempt);
    }

    @Transactional
    public AttemptReviewDto grade(Long attemptId, GradeRequest request, UserPrincipal principal) {
        AssessmentAttempt attempt = attemptRepository.findByIdForUpdate(attemptId)
                .orElseThrow(() -> ApiException.notFound("Attempt"));
        accessGuard.requireStaff(principal);
        accessGuard.assertCanManage(principal, attempt.getAssignment());
        AssessmentResult result = gradingService.applyManualGrades(attempt, request,
                userRepository.getReferenceById(principal.getId()), clock.instant());
        auditService.log("ATTEMPT_GRADED", "ATTEMPT", attemptId,
                request.grades().size() + " answer(s) graded; total=" + result.getTotalScore() + "/" + result.getMaxScore()
                        + "; status=" + result.getGradingStatus() + "; released=" + result.isReleased());
        return toReview(attempt);
    }

    @Transactional
    public AttemptReviewDto flag(Long attemptId, FlagRequest request, UserPrincipal principal) {
        AssessmentAttempt attempt = attemptRepository.findByIdForUpdate(attemptId)
                .orElseThrow(() -> ApiException.notFound("Attempt"));
        accessGuard.requireStaff(principal);
        accessGuard.assertCanManage(principal, attempt.getAssignment());
        attempt.setFlagged(request.flagged());
        attempt.setFlagReason(request.flagged() ? TextUtils.trimToNull(request.reason()) : null);
        resultRepository.findByAttemptId(attemptId).ifPresent(r -> r.setFlagged(request.flagged()));
        AssessmentActivityLog entry = new AssessmentActivityLog();
        entry.setAttempt(attempt);
        entry.setEventType(ActivityEventType.FLAGGED);
        entry.setServerTimestamp(clock.instant());
        entry.setDetails(TextUtils.truncate((request.flagged() ? "Flagged by reviewer: " : "Flag cleared by reviewer: ")
                + (request.reason() == null ? "" : request.reason()), 500));
        activityRepository.save(entry);
        auditService.log(request.flagged() ? "ATTEMPT_FLAGGED" : "ATTEMPT_UNFLAGGED", "ATTEMPT", attemptId, request.reason());
        return toReview(attempt);
    }

    @Transactional
    public ResultDto releaseOne(Long attemptId, UserPrincipal principal) {
        AssessmentAttempt attempt = findManaged(attemptId, principal);
        AssessmentResult result = resultRepository.findByAttemptId(attemptId)
                .orElseThrow(() -> ApiException.conflict("NOT_GRADED", "This attempt has no result yet"));
        if (result.getGradingStatus() != ResultGradingStatus.GRADED) {
            throw ApiException.conflict("NOT_GRADED", "Finish grading before releasing the result");
        }
        gradingService.release(result, clock.instant());
        auditService.log("RESULT_RELEASED", "ATTEMPT", attempt.getId(), null);
        return toResultDto(result);
    }

    @Transactional(readOnly = true)
    public List<ActivityLogDto> activity(Long attemptId, UserPrincipal principal) {
        findManaged(attemptId, principal);
        return activityRepository.findByAttemptIdOrderByServerTimestampAscIdAsc(attemptId).stream()
                .map(l -> new ActivityLogDto(l.getId(), l.getEventType(), l.getClientTimestamp(), l.getServerTimestamp(),
                        l.getDurationMs(), l.getDetails(), l.isCountedAsViolation()))
                .toList();
    }

    // ================================================================== lists, monitoring, export

    @Transactional(readOnly = true)
    public PageResponse<ResultDto> search(UserPrincipal principal, Long assignmentId, Boolean passed,
                                          ResultGradingStatus gradingStatus, Boolean flagged, String search,
                                          Pageable pageable) {
        if (assignmentId != null) {
            accessGuard.assertCanManage(principal, assignmentRepository.findById(assignmentId)
                    .orElseThrow(() -> ApiException.notFound("Assessment")));
        }
        return PageResponse.from(resultRepository.search(accessGuard.ownerScope(principal), assignmentId, passed,
                gradingStatus, flagged, TextUtils.likePattern(search), pageable), this::toResultDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<MonitoringAttemptDto> monitor(UserPrincipal principal, Long assignmentId, AttemptStatus status,
                                                      Boolean flagged, String search, Pageable pageable) {
        Instant now = clock.instant();
        return PageResponse.from(attemptRepository.search(accessGuard.ownerScope(principal), assignmentId, status, flagged,
                TextUtils.likePattern(search), pageable), t -> toMonitoringDto(t, now));
    }

    @Transactional
    public String exportCsv(UserPrincipal principal, Long assignmentId, Boolean passed,
                            ResultGradingStatus gradingStatus, Boolean flagged, String search) {
        PageResponse<ResultDto> page = search(principal, assignmentId, passed, gradingStatus, flagged, search,
                PageRequest.of(0, EXPORT_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt")));
        StringBuilder csv = new StringBuilder("﻿"); // BOM so Excel detects UTF-8
        csv.append(String.join(",", "Candidate", "Email", "Assessment", "Attempt", "Attempt Status", "Started (UTC)",
                "Submitted (UTC)", "Score", "Max Score", "Percentage", "Passed", "Grading Status", "Released",
                "Tab Switches", "Focus Losses", "Violations", "Flagged")).append("\r\n");
        DateTimeFormatter iso = DateTimeFormatter.ISO_INSTANT;
        for (ResultDto r : page.content()) {
            csv.append(String.join(",",
                    cell(r.candidateName()), cell(r.candidateEmail()), cell(r.assignmentTitle()),
                    cell(String.valueOf(r.attemptNumber())), cell(String.valueOf(r.attemptStatus())),
                    cell(r.startedAt() == null ? "" : iso.format(r.startedAt())),
                    cell(r.submittedAt() == null ? "" : iso.format(r.submittedAt())),
                    cell(String.valueOf(r.totalScore())), cell(String.valueOf(r.maxScore())),
                    cell(String.valueOf(r.percentage())),
                    cell(r.passed() == null ? "Pending" : (r.passed() ? "Yes" : "No")),
                    cell(String.valueOf(r.gradingStatus())), cell(r.released() ? "Yes" : "No"),
                    cell(String.valueOf(r.tabSwitchCount())), cell(String.valueOf(r.focusLossCount())),
                    cell(String.valueOf(r.violationCount())), cell(r.flagged() ? "Yes" : "No")))
                    .append("\r\n");
        }
        auditService.log("RESULTS_EXPORTED", "ASSIGNMENT", assignmentId, page.content().size() + " row(s)");
        return csv.toString();
    }

    /** Quotes a CSV cell and neutralises spreadsheet formula injection. */
    static String cell(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    // ================================================================== mapping

    private AssessmentAttempt findManaged(Long attemptId, UserPrincipal principal) {
        accessGuard.requireStaff(principal);
        AssessmentAttempt attempt = attemptRepository.findById(attemptId).orElseThrow(() -> ApiException.notFound("Attempt"));
        accessGuard.assertCanManage(principal, attempt.getAssignment());
        return attempt;
    }

    private AttemptReviewDto toReview(AssessmentAttempt t) {
        Assignment a = t.getAssignment();
        List<AssessmentAnswer> answers = answerRepository.findByAttemptIdWithQuestion(t.getId());
        List<ReviewAnswerDto> reviewAnswers = answers.stream().map(ans -> {
            Question q = ans.getQuestion();
            Set<Long> selected = TextUtils.parseIdList(ans.getSelectedOptionIds());
            List<ReviewOptionDto> options = q.getType().usesOptions()
                    ? q.getOptions().stream().sorted(Comparator.comparingInt(QuestionOption::getDisplayOrder))
                    .map(o -> new ReviewOptionDto(o.getId(), o.getOptionText(), o.isCorrect(), selected.contains(o.getId())))
                    .toList()
                    : null;
            return new ReviewAnswerDto(ans.getId(), q.getId(), ans.getDisplayOrder() + 1, q.getType(), q.getTitle(),
                    q.getBody(), ans.getMaxMarks(), ans.getMarksAwarded(), ans.getCorrect(), ans.getGradingStatus(),
                    ans.getEvaluatorComment(), ans.getGradedBy() == null ? null : ans.getGradedBy().getFullName(),
                    ans.getGradedAt(), options, ans.getTextAnswer(), ans.getCodeAnswer(), ans.getCodeLanguage(),
                    ans.getFileOriginalName(), ans.getFileSizeBytes(), q.getReferenceAnswer(), q.getEvaluationGuidelines(),
                    TextUtils.splitLines(q.getAcceptedAnswers()), ans.getSavedAt());
        }).toList();
        ResultDto result = resultRepository.findByAttemptId(t.getId()).map(this::toResultDto).orElse(null);
        return new AttemptReviewDto(t.getId(), a.getId(), a.getTitle(), a.getPassingMarks(), t.getUser().getId(),
                t.getUser().getFullName(), t.getUser().getEmail(), t.getAttemptNumber(), t.getStatus(), t.getStartedAt(),
                t.getDeadlineAt(), t.getSubmittedAt(), t.getSubmissionReason(), t.getIpAddress(), t.getUserAgent(),
                t.getTabSwitchCount(), t.getFocusLossCount(), t.getViolationCount(), t.getTotalAwayMs(), t.isFlagged(),
                t.getFlagReason(), result, reviewAnswers);
    }

    ResultDto toResultDto(AssessmentResult r) {
        AssessmentAttempt t = r.getAttempt();
        return new ResultDto(r.getId(), t.getId(), t.getAttemptNumber(), t.getStatus(), r.getAssignment().getId(),
                r.getAssignment().getTitle(), r.getUser().getId(), r.getUser().getFullName(), r.getUser().getEmail(),
                r.getAutoScore(), r.getManualScore(), r.getTotalScore(), r.getMaxScore(), r.getPercentage(), r.getPassed(),
                r.getGradingStatus(), r.isReleased(), r.getReleasedAt(), r.getFeedback(),
                r.getGradedBy() == null ? null : r.getGradedBy().getFullName(), r.getGradedAt(), r.getTabSwitchCount(),
                r.getFocusLossCount(), r.getViolationCount(), r.isFlagged(), r.getActivitySummary(), t.getStartedAt(),
                t.getSubmittedAt());
    }

    MonitoringAttemptDto toMonitoringDto(AssessmentAttempt t, Instant now) {
        return new MonitoringAttemptDto(t.getId(), t.getAssignment().getId(), t.getAssignment().getTitle(),
                t.getUser().getId(), t.getUser().getFullName(), t.getUser().getEmail(), t.getAttemptNumber(),
                t.getStatus(), t.getStartedAt(), t.getDeadlineAt(), t.getSubmittedAt(),
                AttemptService.remainingSeconds(t, now), t.getTabSwitchCount(), t.getFocusLossCount(),
                t.getViolationCount(), t.getTotalAwayMs(), t.isFlagged(), t.getFlagReason(), t.getLastSavedAt());
    }
}
