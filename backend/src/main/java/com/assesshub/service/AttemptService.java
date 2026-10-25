package com.assesshub.service;

import com.assesshub.common.RequestUtils;
import com.assesshub.common.TextUtils;
import com.assesshub.common.exception.ApiException;
import com.assesshub.common.exception.AttemptClosedException;
import com.assesshub.domain.AssessmentActivityLog;
import com.assesshub.domain.AssessmentAnswer;
import com.assesshub.domain.AssessmentAssignment;
import com.assesshub.domain.AssessmentAttempt;
import com.assesshub.domain.Assignment;
import com.assesshub.domain.AssignmentQuestion;
import com.assesshub.domain.Question;
import com.assesshub.domain.QuestionOption;
import com.assesshub.domain.enums.ActivityEventType;
import com.assesshub.domain.enums.AnswerGradingStatus;
import com.assesshub.domain.enums.AssigneeStatus;
import com.assesshub.domain.enums.AttemptStatus;
import com.assesshub.domain.enums.QuestionType;
import com.assesshub.domain.enums.ViolationAction;
import com.assesshub.dto.AttemptDtos.ActivityBatchRequest;
import com.assesshub.dto.AttemptDtos.ActivityEventInput;
import com.assesshub.dto.AttemptDtos.ActivityResponse;
import com.assesshub.dto.AttemptDtos.AnswerInput;
import com.assesshub.dto.AttemptDtos.AttemptQuestionDto;
import com.assesshub.dto.AttemptDtos.AttemptViewDto;
import com.assesshub.dto.AttemptDtos.CandidateOptionDto;
import com.assesshub.dto.AttemptDtos.SaveAnswersRequest;
import com.assesshub.dto.AttemptDtos.SaveAnswersResponse;
import com.assesshub.dto.AttemptDtos.SavedAnswerDto;
import com.assesshub.dto.AttemptDtos.SubmitResponse;
import com.assesshub.repository.AssessmentActivityLogRepository;
import com.assesshub.repository.AssessmentAnswerRepository;
import com.assesshub.repository.AssessmentAssignmentRepository;
import com.assesshub.repository.AssessmentAttemptRepository;
import com.assesshub.repository.AssessmentResultRepository;
import com.assesshub.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The timed assessment engine. The server is the single source of truth for timing:
 * start time and deadline are computed and persisted here, every write is checked against
 * the deadline (+ grace period), and expired attempts are auto-submitted.
 */
@Service
public class AttemptService {

    private static final Logger log = LoggerFactory.getLogger(AttemptService.class);
    public static final String ANSWER_FILE_AREA = "answers";
    /** Events closer together than this are treated as one focus violation (blur + hidden fire together). */
    static final Duration VIOLATION_DEDUP_WINDOW = Duration.ofMillis(2000);

    private final AssessmentAssignmentRepository assigneeRepository;
    private final AssessmentAttemptRepository attemptRepository;
    private final AssessmentAnswerRepository answerRepository;
    private final AssessmentActivityLogRepository activityRepository;
    private final AssessmentResultRepository resultRepository;
    private final GradingService gradingService;
    private final FileStorageService storage;
    private final AccessGuard accessGuard;
    private final Clock clock;

    public AttemptService(AssessmentAssignmentRepository assigneeRepository, AssessmentAttemptRepository attemptRepository,
                          AssessmentAnswerRepository answerRepository, AssessmentActivityLogRepository activityRepository,
                          AssessmentResultRepository resultRepository, GradingService gradingService,
                          FileStorageService storage, AccessGuard accessGuard, Clock clock) {
        this.assigneeRepository = assigneeRepository;
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
        this.activityRepository = activityRepository;
        this.resultRepository = resultRepository;
        this.gradingService = gradingService;
        this.storage = storage;
        this.accessGuard = accessGuard;
        this.clock = clock;
    }

    // ================================================================== start / resume

    @Transactional(noRollbackFor = AttemptClosedException.class)
    public AttemptViewDto start(Long assignmentId, UserPrincipal principal) {
        Instant now = clock.instant();
        AssessmentAssignment aa = assigneeRepository.findForUpdate(assignmentId, principal.getId())
                .orElseThrow(() -> ApiException.notFound("Assessment"));
        if (aa.getStatus() == AssigneeStatus.REVOKED) {
            throw ApiException.forbidden("Your access to this assessment has been revoked");
        }
        Assignment a = aa.getAssignment();

        // Resume an attempt that is still running.
        var active = attemptRepository.findFirstByAssessmentAssignmentIdAndStatus(aa.getId(), AttemptStatus.IN_PROGRESS);
        if (active.isPresent()) {
            AssessmentAttempt attempt = active.get();
            if (now.isAfter(attempt.hardDeadline())) {
                finalizeAttempt(attempt, AttemptStatus.AUTO_SUBMITTED, "Time limit reached - submitted automatically by the server", now);
                throw new AttemptClosedException("ATTEMPT_EXPIRED",
                        "Your previous attempt ran out of time and was submitted automatically.");
            }
            logEvent(attempt, ActivityEventType.RESUMED, now, null, null, null, false);
            return buildView(attempt, now);
        }

        if (!a.getStatus().isOpen()) {
            throw ApiException.conflict("NOT_AVAILABLE", "This assessment is not open");
        }
        if (a.getStartAt() != null && now.isBefore(a.getStartAt())) {
            throw ApiException.conflict("NOT_STARTED", "This assessment opens at " + a.getStartAt());
        }
        Instant windowEnd = aa.effectiveDeadline();
        if (windowEnd != null && !now.isBefore(windowEnd)) {
            if (aa.getAttemptsUsed() == 0 && aa.getStatus() == AssigneeStatus.ASSIGNED) {
                aa.setStatus(AssigneeStatus.EXPIRED);
            }
            throw new AttemptClosedException("DEADLINE_PASSED", "The deadline for this assessment has passed");
        }
        if (aa.getAttemptsUsed() >= a.getMaxAttempts()) {
            throw ApiException.conflict("ATTEMPT_LIMIT_REACHED", "You have used all permitted attempts");
        }
        List<AssignmentQuestion> questions = new ArrayList<>(a.getQuestions());
        if (questions.isEmpty()) {
            throw ApiException.conflict("NO_QUESTIONS", "This assessment has no questions yet");
        }
        questions.sort(Comparator.comparingInt(AssignmentQuestion::getDisplayOrder));
        if (a.isShuffleQuestions()) {
            Collections.shuffle(questions, ThreadLocalRandom.current());
        }

        Instant deadline = now.plus(Duration.ofMinutes(a.getDurationMinutes()));
        if (windowEnd != null && windowEnd.isBefore(deadline)) {
            deadline = windowEnd;
        }

        AssessmentAttempt attempt = new AssessmentAttempt();
        attempt.setAssessmentAssignment(aa);
        attempt.setAssignment(a);
        attempt.setUser(aa.getUser());
        attempt.setAttemptNumber(aa.getAttemptsUsed() + 1);
        attempt.setStatus(AttemptStatus.IN_PROGRESS);
        attempt.setStartedAt(now);
        attempt.setDeadlineAt(deadline);
        attempt.setQuestionOrder(questions.stream().map(aq -> String.valueOf(aq.getQuestion().getId()))
                .collect(Collectors.joining(",")));
        attempt.setOptionOrder(buildOptionOrder(questions, a.isShuffleOptions()));
        attempt.setIpAddress(RequestUtils.clientIp());
        attempt.setUserAgent(RequestUtils.userAgent());
        attemptRepository.save(attempt);

        for (int i = 0; i < questions.size(); i++) {
            AssignmentQuestion aq = questions.get(i);
            AssessmentAnswer answer = new AssessmentAnswer();
            answer.setAttempt(attempt);
            answer.setQuestion(aq.getQuestion());
            answer.setDisplayOrder(i);
            answer.setMaxMarks(aq.getMarks());
            answer.setGradingStatus(AnswerGradingStatus.PENDING);
            answerRepository.save(answer);
        }

        aa.setAttemptsUsed(aa.getAttemptsUsed() + 1);
        aa.setStatus(AssigneeStatus.IN_PROGRESS);
        logEvent(attempt, ActivityEventType.STARTED, now, null, null,
                "Attempt " + attempt.getAttemptNumber() + ", deadline " + deadline, false);
        return buildView(attempt, now);
    }

    @Transactional(noRollbackFor = AttemptClosedException.class)
    public AttemptViewDto getView(Long attemptId, UserPrincipal principal) {
        Instant now = clock.instant();
        AssessmentAttempt attempt = lockOwned(attemptId, principal);
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS && now.isAfter(attempt.hardDeadline())) {
            finalizeAttempt(attempt, AttemptStatus.AUTO_SUBMITTED, "Time limit reached - submitted automatically by the server", now);
        }
        return buildView(attempt, now);
    }

    // ================================================================== answers

    @Transactional(noRollbackFor = AttemptClosedException.class)
    public SaveAnswersResponse saveAnswers(Long attemptId, SaveAnswersRequest request, UserPrincipal principal) {
        Instant now = clock.instant();
        AssessmentAttempt attempt = lockOwned(attemptId, principal);
        ensureWritable(attempt, now);

        Map<Long, AssessmentAnswer> answers = answerRepository.findByAttemptIdWithQuestion(attemptId).stream()
                .collect(Collectors.toMap(a -> a.getQuestion().getId(), Function.identity()));
        int saved = 0;
        for (AnswerInput input : request.answers()) {
            AssessmentAnswer answer = answers.get(input.questionId());
            if (answer == null) {
                throw ApiException.badRequest("INVALID_QUESTION", "Question " + input.questionId() + " is not part of this attempt");
            }
            applyAnswer(answer, input);
            answer.setSavedAt(now);
            saved++;
        }
        attempt.setLastSavedAt(now);
        return new SaveAnswersResponse(now, remainingSeconds(attempt, now), saved, attempt.getStatus());
    }

    private void applyAnswer(AssessmentAnswer answer, AnswerInput input) {
        Question q = answer.getQuestion();
        QuestionType type = q.getType();
        if (type.usesOptions()) {
            List<Long> selected = input.selectedOptionIds() == null ? List.of() : input.selectedOptionIds();
            Set<Long> valid = q.getOptions().stream().map(QuestionOption::getId).collect(Collectors.toSet());
            if (!valid.containsAll(selected)) {
                throw ApiException.badRequest("INVALID_OPTION", "Invalid option for question " + q.getId());
            }
            if (type.singleSelection() && selected.stream().distinct().count() > 1) {
                throw ApiException.badRequest("SINGLE_SELECTION", "Only one option may be selected for question " + q.getId());
            }
            answer.setSelectedOptionIds(TextUtils.joinIds(selected));
        } else if (type.usesText()) {
            answer.setTextAnswer(input.textAnswer() == null || input.textAnswer().isBlank() ? null : input.textAnswer());
        } else if (type == QuestionType.CODING) {
            answer.setCodeAnswer(input.codeAnswer() == null || input.codeAnswer().isBlank() ? null : input.codeAnswer());
            answer.setCodeLanguage(TextUtils.trimToNull(input.codeLanguage()) == null
                    ? q.getCodingLanguage() : input.codeLanguage().trim());
        }
        // FILE_UPLOAD answers are written through uploadFile().
    }

    @Transactional(noRollbackFor = AttemptClosedException.class)
    public SavedAnswerDto uploadFile(Long attemptId, Long questionId, MultipartFile file, UserPrincipal principal) {
        Instant now = clock.instant();
        AssessmentAttempt attempt = lockOwned(attemptId, principal);
        ensureWritable(attempt, now);
        AssessmentAnswer answer = answerRepository.findByAttemptIdAndQuestionId(attemptId, questionId)
                .orElseThrow(() -> ApiException.badRequest("INVALID_QUESTION", "Question is not part of this attempt"));
        Question q = answer.getQuestion();
        if (q.getType() != QuestionType.FILE_UPLOAD) {
            throw ApiException.badRequest("NOT_FILE_QUESTION", "This question does not accept file uploads");
        }
        List<String> allowed = q.getAllowedFileTypes() == null ? List.of() : Arrays.asList(q.getAllowedFileTypes().split(","));
        long maxBytes = (q.getMaxFileSizeMb() == null ? 10L : q.getMaxFileSizeMb()) * 1024L * 1024L;
        FileStorageService.StoredFile stored = storage.store(file, ANSWER_FILE_AREA, allowed, maxBytes);
        storage.deleteQuietly(ANSWER_FILE_AREA, answer.getFileStoredName());
        answer.setFileStoredName(stored.storedName());
        answer.setFileOriginalName(stored.originalName());
        answer.setFileContentType(stored.contentType());
        answer.setFileSizeBytes(stored.size());
        answer.setSavedAt(now);
        attempt.setLastSavedAt(now);
        return toSavedDto(answer);
    }

    public record AnswerFile(Resource resource, String fileName) {
    }

    @Transactional(readOnly = true)
    public AnswerFile downloadAnswerFile(Long attemptId, Long questionId, UserPrincipal principal) {
        AssessmentAttempt attempt = attemptRepository.findById(attemptId).orElseThrow(() -> ApiException.notFound("Attempt"));
        boolean owner = attempt.getUser().getId().equals(principal.getId());
        if (!owner && !accessGuard.canManage(principal, attempt.getAssignment())) {
            throw ApiException.notFound("Attempt");
        }
        AssessmentAnswer answer = answerRepository.findByAttemptIdAndQuestionId(attemptId, questionId)
                .filter(a -> a.getFileStoredName() != null)
                .orElseThrow(() -> ApiException.notFound("File"));
        return new AnswerFile(storage.load(ANSWER_FILE_AREA, answer.getFileStoredName()), answer.getFileOriginalName());
    }

    // ================================================================== monitoring

    @Transactional(noRollbackFor = AttemptClosedException.class)
    public ActivityResponse recordActivity(Long attemptId, ActivityBatchRequest request, UserPrincipal principal) {
        Instant now = clock.instant();
        AssessmentAttempt attempt = lockOwned(attemptId, principal);
        Assignment a = attempt.getAssignment();
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS && now.isAfter(attempt.hardDeadline())) {
            finalizeAttempt(attempt, AttemptStatus.AUTO_SUBMITTED, "Time limit reached - submitted automatically by the server", now);
        }
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            return activityResponse(attempt, false, "This attempt is no longer in progress");
        }

        List<ActivityEventInput> events = new ArrayList<>(request.events());
        events.sort(Comparator.comparing(e -> e.occurredAt() == null ? now : e.occurredAt()));
        boolean newViolation = false;
        for (ActivityEventInput event : events) {
            ActivityEventType type = event.type();
            if (!type.isClientReportable()) {
                continue; // server-only event types cannot be injected by the browser
            }
            Instant clientTime = clamp(event.occurredAt(), attempt.getStartedAt(), now);
            boolean violation = false;
            if (type == ActivityEventType.TAB_HIDDEN) {
                attempt.setTabSwitchCount(attempt.getTabSwitchCount() + 1);
            } else if (type == ActivityEventType.WINDOW_BLUR) {
                attempt.setFocusLossCount(attempt.getFocusLossCount() + 1);
            } else if (type.isReturn() && event.durationMs() != null) {
                long elapsed = Duration.between(attempt.getStartedAt(), now).toMillis();
                attempt.setTotalAwayMs(attempt.getTotalAwayMs() + Math.min(event.durationMs(), Math.max(0, elapsed)));
            }
            if (a.isMonitoringEnabled() && type.isPotentialViolation()) {
                Instant last = attempt.getLastViolationAt();
                if (last == null || Duration.between(last, clientTime).abs().compareTo(VIOLATION_DEDUP_WINDOW) > 0) {
                    violation = true;
                    newViolation = true;
                    attempt.setViolationCount(attempt.getViolationCount() + 1);
                    attempt.setLastViolationAt(clientTime);
                }
            }
            logEvent(attempt, type, now, clientTime, event.durationMs(), TextUtils.truncate(event.details(), 500), violation);
        }

        String message = null;
        if (a.isMonitoringEnabled() && attempt.getViolationCount() >= a.getMaxViolations()) {
            String reason = "Left the assessment window " + attempt.getViolationCount() + " times (limit " + a.getMaxViolations() + ")";
            if (a.getViolationAction() == ViolationAction.FLAG_FOR_REVIEW && !attempt.isFlagged()) {
                attempt.setFlagged(true);
                attempt.setFlagReason(reason);
                logEvent(attempt, ActivityEventType.FLAGGED, now, null, null, reason, false);
            } else if (a.getViolationAction() == ViolationAction.AUTO_SUBMIT) {
                attempt.setFlagged(true);
                attempt.setFlagReason(reason);
                finalizeAttempt(attempt, AttemptStatus.TERMINATED, "Submitted automatically: " + reason, now);
                message = "The assessment was submitted automatically because the focus-loss limit was reached.";
            }
        }
        if (message == null && newViolation) {
            message = attempt.getViolationCount() >= a.getWarningThreshold()
                    ? "Warning: you have left the assessment window " + attempt.getViolationCount() + " times. This is recorded for review."
                    : "Leaving the assessment window is recorded (" + attempt.getViolationCount() + " of " + a.getMaxViolations() + ").";
        }
        return activityResponse(attempt, newViolation, message);
    }

    // ================================================================== submission

    @Transactional(noRollbackFor = AttemptClosedException.class)
    public SubmitResponse submit(Long attemptId, UserPrincipal principal) {
        Instant now = clock.instant();
        AssessmentAttempt attempt = lockOwned(attemptId, principal);
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new AttemptClosedException("ATTEMPT_CLOSED", "This attempt has already been submitted");
        }
        if (now.isAfter(attempt.hardDeadline())) {
            finalizeAttempt(attempt, AttemptStatus.AUTO_SUBMITTED,
                    "Late submission rejected - saved answers submitted automatically at the deadline", now);
            throw new AttemptClosedException("SUBMISSION_LATE",
                    "The deadline has passed. Your answers saved before the deadline were submitted automatically.");
        }
        finalizeAttempt(attempt, AttemptStatus.SUBMITTED, "Submitted by candidate", now);
        boolean released = resultRepository.findByAttemptId(attemptId).map(r -> r.isReleased()).orElse(false);
        return new SubmitResponse(attempt.getId(), attempt.getStatus(), attempt.getSubmittedAt(),
                "Your assessment has been submitted successfully.", released);
    }

    /** Called by the scheduler for attempts whose deadline has passed. */
    @Transactional
    public boolean autoSubmitIfExpired(Long attemptId) {
        Instant now = clock.instant();
        AssessmentAttempt attempt = attemptRepository.findByIdForUpdate(attemptId).orElse(null);
        if (attempt == null || attempt.getStatus() != AttemptStatus.IN_PROGRESS || !now.isAfter(attempt.hardDeadline())) {
            return false;
        }
        finalizeAttempt(attempt, AttemptStatus.AUTO_SUBMITTED, "Time limit reached - submitted automatically by the server", now);
        log.info("Auto-submitted expired attempt {}", attemptId);
        return true;
    }

    /** Ends a running attempt when the assessor revokes access. Joins the caller's transaction. */
    @Transactional
    public void terminateActiveAttempt(AssessmentAssignment aa, String reason) {
        attemptRepository.findFirstByAssessmentAssignmentIdAndStatus(aa.getId(), AttemptStatus.IN_PROGRESS)
                .flatMap(a -> attemptRepository.findByIdForUpdate(a.getId()))
                .ifPresent(attempt -> finalizeAttempt(attempt, AttemptStatus.TERMINATED, reason, clock.instant()));
    }

    void finalizeAttempt(AssessmentAttempt attempt, AttemptStatus status, String reason, Instant now) {
        if (attempt.getStatus().isFinal()) {
            return;
        }
        attempt.setStatus(status);
        attempt.setSubmittedAt(now);
        attempt.setSubmissionReason(TextUtils.truncate(reason, 255));
        AssessmentAssignment aa = attempt.getAssessmentAssignment();
        if (aa.getStatus() != AssigneeStatus.REVOKED) {
            aa.setStatus(AssigneeStatus.COMPLETED);
        }
        aa.setCompletedAt(now);
        ActivityEventType event = switch (status) {
            case SUBMITTED -> ActivityEventType.SUBMITTED;
            case AUTO_SUBMITTED -> ActivityEventType.AUTO_SUBMITTED;
            default -> ActivityEventType.TERMINATED;
        };
        logEvent(attempt, event, now, null, null, reason, false);
        attemptRepository.saveAndFlush(attempt);
        gradingService.gradeAttempt(attempt, now);
    }

    // ================================================================== helpers

    private AssessmentAttempt lockOwned(Long attemptId, UserPrincipal principal) {
        AssessmentAttempt attempt = attemptRepository.findByIdForUpdate(attemptId)
                .orElseThrow(() -> ApiException.notFound("Attempt"));
        if (!attempt.getUser().getId().equals(principal.getId())) {
            throw ApiException.notFound("Attempt");
        }
        return attempt;
    }

    private void ensureWritable(AssessmentAttempt attempt, Instant now) {
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new AttemptClosedException("ATTEMPT_CLOSED", "This attempt has already been submitted");
        }
        if (now.isAfter(attempt.hardDeadline())) {
            finalizeAttempt(attempt, AttemptStatus.AUTO_SUBMITTED, "Time limit reached - submitted automatically by the server", now);
            throw new AttemptClosedException("ATTEMPT_EXPIRED",
                    "Time is up. Your answers saved before the deadline were submitted automatically.");
        }
    }

    private void logEvent(AssessmentAttempt attempt, ActivityEventType type, Instant serverTime, Instant clientTime,
                          Long durationMs, String details, boolean violation) {
        AssessmentActivityLog entry = new AssessmentActivityLog();
        entry.setAttempt(attempt);
        entry.setEventType(type);
        entry.setServerTimestamp(serverTime);
        entry.setClientTimestamp(clientTime);
        entry.setDurationMs(durationMs);
        entry.setDetails(TextUtils.truncate(details, 500));
        entry.setCountedAsViolation(violation);
        activityRepository.save(entry);
    }

    private static Instant clamp(Instant value, Instant min, Instant max) {
        if (value == null) {
            return max;
        }
        if (value.isBefore(min)) {
            return min;
        }
        return value.isAfter(max) ? max : value;
    }

    static long remainingSeconds(AssessmentAttempt attempt, Instant now) {
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            return 0;
        }
        return Math.max(0, Duration.between(now, attempt.getDeadlineAt()).getSeconds());
    }

    private ActivityResponse activityResponse(AssessmentAttempt attempt, boolean newViolation, String message) {
        Assignment a = attempt.getAssignment();
        return new ActivityResponse(attempt.getTabSwitchCount(), attempt.getFocusLossCount(), attempt.getViolationCount(),
                a.getWarningThreshold(), a.getMaxViolations(), a.getViolationAction(), attempt.isFlagged(),
                attempt.getStatus(), newViolation, message);
    }

    private static String buildOptionOrder(List<AssignmentQuestion> questions, boolean shuffle) {
        StringBuilder sb = new StringBuilder();
        for (AssignmentQuestion aq : questions) {
            Question q = aq.getQuestion();
            if (!q.getType().usesOptions()) {
                continue;
            }
            List<Long> ids = new ArrayList<>(q.getOptions().stream()
                    .sorted(Comparator.comparingInt(QuestionOption::getDisplayOrder))
                    .map(QuestionOption::getId).toList());
            if (shuffle && q.getType() != QuestionType.TRUE_FALSE) {
                Collections.shuffle(ids, ThreadLocalRandom.current());
            }
            if (!sb.isEmpty()) {
                sb.append(';');
            }
            sb.append(q.getId()).append(':').append(ids.stream().map(String::valueOf).collect(Collectors.joining(",")));
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    static Map<Long, List<Long>> parseOptionOrder(String value) {
        Map<Long, List<Long>> map = new HashMap<>();
        if (value == null || value.isBlank()) {
            return map;
        }
        for (String part : value.split(";")) {
            int colon = part.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            Long qid = Long.valueOf(part.substring(0, colon));
            map.put(qid, new ArrayList<>(TextUtils.parseIdList(part.substring(colon + 1))));
        }
        return map;
    }

    AttemptViewDto buildView(AssessmentAttempt attempt, Instant now) {
        Assignment a = attempt.getAssignment();
        boolean running = attempt.getStatus() == AttemptStatus.IN_PROGRESS;
        List<AttemptQuestionDto> questions = List.of();
        List<SavedAnswerDto> saved = List.of();
        if (running) {
            List<AssessmentAnswer> answers = answerRepository.findByAttemptIdWithQuestion(attempt.getId());
            Map<Long, List<Long>> optionOrder = parseOptionOrder(attempt.getOptionOrder());
            questions = answers.stream().map(ans -> toCandidateQuestion(ans, optionOrder)).toList();
            saved = answers.stream().map(AttemptService::toSavedDto).toList();
        }
        return new AttemptViewDto(attempt.getId(), a.getId(), a.getTitle(), a.getInstructions(), attempt.getStatus(),
                attempt.getAttemptNumber(), attempt.getStartedAt(), attempt.getDeadlineAt(), now,
                remainingSeconds(attempt, now), a.getDurationMinutes(), a.getGracePeriodSeconds(),
                a.isMonitoringEnabled(), a.getWarningThreshold(), a.getMaxViolations(), a.getViolationAction(),
                attempt.getTabSwitchCount(), attempt.getFocusLossCount(), attempt.getViolationCount(),
                attempt.getLastSavedAt(), attempt.getSubmittedAt(), attempt.getSubmissionReason(), questions, saved);
    }

    /** Candidate-safe projection: never exposes correct flags, reference or accepted answers. */
    private static AttemptQuestionDto toCandidateQuestion(AssessmentAnswer ans, Map<Long, List<Long>> optionOrder) {
        Question q = ans.getQuestion();
        List<CandidateOptionDto> options = null;
        if (q.getType().usesOptions()) {
            Map<Long, QuestionOption> byId = new LinkedHashMap<>();
            q.getOptions().stream().sorted(Comparator.comparingInt(QuestionOption::getDisplayOrder))
                    .forEach(o -> byId.put(o.getId(), o));
            List<Long> order = optionOrder.getOrDefault(q.getId(), new ArrayList<>(byId.keySet()));
            options = order.stream().map(byId::get).filter(o -> o != null)
                    .map(o -> new CandidateOptionDto(o.getId(), o.getOptionText())).toList();
        }
        return new AttemptQuestionDto(q.getId(), ans.getDisplayOrder() + 1, q.getType(), q.getTitle(), q.getBody(),
                ans.getMaxMarks(), q.getCodingLanguage(), q.getStarterCode(), q.getAllowedFileTypes(),
                q.getMaxFileSizeMb(), options);
    }

    private static SavedAnswerDto toSavedDto(AssessmentAnswer ans) {
        return new SavedAnswerDto(ans.getQuestion().getId(), new ArrayList<>(TextUtils.parseIdList(ans.getSelectedOptionIds())),
                ans.getTextAnswer(), ans.getCodeAnswer(), ans.getCodeLanguage(), ans.getFileOriginalName(),
                ans.getFileSizeBytes(), ans.getSavedAt());
    }
}
