package com.assesshub.service;

import com.assesshub.common.exception.ApiException;
import com.assesshub.common.exception.AttemptClosedException;
import com.assesshub.domain.AssessmentAnswer;
import com.assesshub.domain.AssessmentAssignment;
import com.assesshub.domain.AssessmentAttempt;
import com.assesshub.domain.Assignment;
import com.assesshub.domain.Question;
import com.assesshub.domain.enums.ActivityEventType;
import com.assesshub.domain.enums.AssigneeStatus;
import com.assesshub.domain.enums.AttemptStatus;
import com.assesshub.domain.enums.QuestionType;
import com.assesshub.domain.enums.ViolationAction;
import com.assesshub.dto.AttemptDtos.ActivityBatchRequest;
import com.assesshub.dto.AttemptDtos.ActivityEventInput;
import com.assesshub.dto.AttemptDtos.ActivityResponse;
import com.assesshub.dto.AttemptDtos.AnswerInput;
import com.assesshub.dto.AttemptDtos.AttemptViewDto;
import com.assesshub.dto.AttemptDtos.SaveAnswersRequest;
import com.assesshub.repository.AssessmentActivityLogRepository;
import com.assesshub.repository.AssessmentAnswerRepository;
import com.assesshub.repository.AssessmentAssignmentRepository;
import com.assesshub.repository.AssessmentAttemptRepository;
import com.assesshub.repository.AssessmentResultRepository;
import com.assesshub.security.UserPrincipal;
import com.assesshub.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.assesshub.service.TestFixtures.answer;
import static com.assesshub.service.TestFixtures.assignee;
import static com.assesshub.service.TestFixtures.assignment;
import static com.assesshub.service.TestFixtures.attempt;
import static com.assesshub.service.TestFixtures.candidatePrincipal;
import static com.assesshub.service.TestFixtures.question;
import static com.assesshub.service.TestFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttemptServiceTest {

    private static final Instant T0 = Instant.parse("2026-03-01T09:00:00Z");

    @Mock
    AssessmentAssignmentRepository assigneeRepository;
    @Mock
    AssessmentAttemptRepository attemptRepository;
    @Mock
    AssessmentAnswerRepository answerRepository;
    @Mock
    AssessmentActivityLogRepository activityRepository;
    @Mock
    AssessmentResultRepository resultRepository;
    @Mock
    GradingService gradingService;
    @Mock
    FileStorageService storage;

    MutableClock clock;
    AttemptService service;
    UserPrincipal candidate = candidatePrincipal(10);

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        service = new AttemptService(assigneeRepository, attemptRepository, answerRepository, activityRepository,
                resultRepository, gradingService, storage, new AccessGuard(), clock);
        when(attemptRepository.save(any(AssessmentAttempt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(attemptRepository.findFirstByAssessmentAssignmentIdAndStatus(anyLong(), eq(AttemptStatus.IN_PROGRESS)))
                .thenReturn(Optional.empty());
        when(resultRepository.findByAttemptId(any())).thenReturn(Optional.empty());
    }

    private AssessmentAssignment assigned(Assignment a) {
        AssessmentAssignment aa = assignee(a, user(10, "c10@test.local"));
        when(assigneeRepository.findForUpdate(a.getId(), 10L)).thenReturn(Optional.of(aa));
        return aa;
    }

    private AssessmentAttempt running(Assignment a, Instant deadline) {
        AssessmentAttempt t = attempt(assignee(a, user(10, "c10@test.local")), T0.minusSeconds(600), deadline);
        when(attemptRepository.findByIdForUpdate(t.getId())).thenReturn(Optional.of(t));
        return t;
    }

    // ------------------------------------------------------------------ start

    @Test
    void startComputesServerDeadlineCappedByAssessmentWindow() {
        Assignment a = assignment(60, 1, question(QuestionType.MCQ_SINGLE, 1, true, false));
        a.setEndAt(T0.plus(Duration.ofMinutes(30)));
        AssessmentAssignment aa = assigned(a);

        AttemptViewDto view = service.start(a.getId(), candidate);

        assertThat(view.status()).isEqualTo(AttemptStatus.IN_PROGRESS);
        assertThat(view.startedAt()).isEqualTo(T0);
        assertThat(view.deadlineAt()).isEqualTo(T0.plus(Duration.ofMinutes(30)));
        assertThat(view.remainingSeconds()).isEqualTo(1800);
        assertThat(aa.getAttemptsUsed()).isEqualTo(1);
        assertThat(aa.getStatus()).isEqualTo(AssigneeStatus.IN_PROGRESS);
        verify(answerRepository).save(any(AssessmentAnswer.class));
    }

    @Test
    void startUsesFullDurationWhenWindowIsLonger() {
        Assignment a = assignment(45, 1, question(QuestionType.MCQ_SINGLE, 1, true, false));
        a.setEndAt(T0.plus(Duration.ofDays(3)));
        assigned(a);
        assertThat(service.start(a.getId(), candidate).deadlineAt()).isEqualTo(T0.plus(Duration.ofMinutes(45)));
    }

    @Test
    void startIsRejectedWhenAttemptLimitReached() {
        Assignment a = assignment(30, 1, question(QuestionType.MCQ_SINGLE, 1, true, false));
        assigned(a).setAttemptsUsed(1);
        assertThatThrownBy(() -> service.start(a.getId(), candidate))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("ATTEMPT_LIMIT_REACHED");
    }

    @Test
    void startIsRejectedBeforeScheduledStart() {
        Assignment a = assignment(30, 1, question(QuestionType.MCQ_SINGLE, 1, true, false));
        a.setStartAt(T0.plus(Duration.ofHours(2)));
        assigned(a);
        assertThatThrownBy(() -> service.start(a.getId(), candidate))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("NOT_STARTED");
    }

    @Test
    void startIsRejectedAfterDeadlineAndAssigneeExpires() {
        Assignment a = assignment(30, 1, question(QuestionType.MCQ_SINGLE, 1, true, false));
        a.setEndAt(T0.minusSeconds(1));
        AssessmentAssignment aa = assigned(a);
        assertThatThrownBy(() -> service.start(a.getId(), candidate))
                .isInstanceOf(AttemptClosedException.class).extracting("code").isEqualTo("DEADLINE_PASSED");
        assertThat(aa.getStatus()).isEqualTo(AssigneeStatus.EXPIRED);
    }

    @Test
    void startResumesRunningAttemptInsteadOfCreatingANewOne() {
        Assignment a = assignment(30, 2, question(QuestionType.MCQ_SINGLE, 1, true, false));
        AssessmentAssignment aa = assigned(a);
        AssessmentAttempt existing = attempt(aa, T0.minusSeconds(120), T0.plusSeconds(600));
        when(attemptRepository.findFirstByAssessmentAssignmentIdAndStatus(aa.getId(), AttemptStatus.IN_PROGRESS))
                .thenReturn(Optional.of(existing));

        AttemptViewDto view = service.start(a.getId(), candidate);

        assertThat(view.attemptId()).isEqualTo(existing.getId());
        assertThat(aa.getAttemptsUsed()).isEqualTo(1);
        verify(attemptRepository, never()).save(any(AssessmentAttempt.class));
    }

    @Test
    void revokedAssigneeCannotStart() {
        Assignment a = assignment(30, 1, question(QuestionType.MCQ_SINGLE, 1, true, false));
        assigned(a).setStatus(AssigneeStatus.REVOKED);
        assertThatThrownBy(() -> service.start(a.getId(), candidate)).isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("FORBIDDEN");
    }

    // ------------------------------------------------------------------ answers & submission timing

    @Test
    void answersAfterDeadlinePlusGraceAreRejectedAndAttemptIsAutoSubmitted() {
        Question q = question(QuestionType.SHORT_ANSWER, 1);
        Assignment a = assignment(30, 1, q);
        AssessmentAttempt t = running(a, T0.minusSeconds(31)); // grace = 30s

        SaveAnswersRequest req = new SaveAnswersRequest(List.of(new AnswerInput(q.getId(), null, "late", null, null)));
        assertThatThrownBy(() -> service.saveAnswers(t.getId(), req, candidate))
                .isInstanceOf(AttemptClosedException.class).extracting("code").isEqualTo("ATTEMPT_EXPIRED");
        assertThat(t.getStatus()).isEqualTo(AttemptStatus.AUTO_SUBMITTED);
        assertThat(t.getAssessmentAssignment().getStatus()).isEqualTo(AssigneeStatus.COMPLETED);
        verify(gradingService).gradeAttempt(t, T0);
    }

    @Test
    void answersWithinGracePeriodAreAccepted() {
        Question q = question(QuestionType.SHORT_ANSWER, 1);
        Assignment a = assignment(30, 1, q);
        AssessmentAttempt t = running(a, T0.minusSeconds(10));
        AssessmentAnswer ans = answer(t, q, 0);
        when(answerRepository.findByAttemptIdWithQuestion(t.getId())).thenReturn(List.of(ans));

        var res = service.saveAnswers(t.getId(),
                new SaveAnswersRequest(List.of(new AnswerInput(q.getId(), null, "404", null, null))), candidate);

        assertThat(res.savedCount()).isEqualTo(1);
        assertThat(ans.getTextAnswer()).isEqualTo("404");
        assertThat(t.getStatus()).isEqualTo(AttemptStatus.IN_PROGRESS);
    }

    @Test
    void invalidOptionAndMultipleSelectionsForSingleChoiceAreRejected() {
        Question q = question(QuestionType.MCQ_SINGLE, 1, true, false);
        Assignment a = assignment(30, 1, q);
        AssessmentAttempt t = running(a, T0.plusSeconds(600));
        when(answerRepository.findByAttemptIdWithQuestion(t.getId())).thenReturn(List.of(answer(t, q, 0)));
        List<Long> both = q.getOptions().stream().map(o -> o.getId()).toList();

        assertThatThrownBy(() -> service.saveAnswers(t.getId(),
                new SaveAnswersRequest(List.of(new AnswerInput(q.getId(), List.of(999_999L), null, null, null))), candidate))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_OPTION");
        assertThatThrownBy(() -> service.saveAnswers(t.getId(),
                new SaveAnswersRequest(List.of(new AnswerInput(q.getId(), both, null, null, null))), candidate))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("SINGLE_SELECTION");
    }

    @Test
    void lateSubmissionIsRejectedButSavedAnswersAreAutoSubmitted() {
        Assignment a = assignment(30, 1, question(QuestionType.SHORT_ANSWER, 1));
        AssessmentAttempt t = running(a, T0.minusSeconds(120));
        assertThatThrownBy(() -> service.submit(t.getId(), candidate))
                .isInstanceOf(AttemptClosedException.class).extracting("code").isEqualTo("SUBMISSION_LATE");
        assertThat(t.getStatus()).isEqualTo(AttemptStatus.AUTO_SUBMITTED);
    }

    @Test
    void submitOnTimeFinalisesOnceAndSecondSubmitIsRejected() {
        Assignment a = assignment(30, 1, question(QuestionType.SHORT_ANSWER, 1));
        AssessmentAttempt t = running(a, T0.plusSeconds(300));
        var res = service.submit(t.getId(), candidate);
        assertThat(res.status()).isEqualTo(AttemptStatus.SUBMITTED);
        assertThat(t.getSubmittedAt()).isEqualTo(T0);
        assertThatThrownBy(() -> service.submit(t.getId(), candidate))
                .isInstanceOf(AttemptClosedException.class).extracting("code").isEqualTo("ATTEMPT_CLOSED");
    }

    @Test
    void schedulerAutoSubmitOnlyAffectsExpiredAttempts() {
        Assignment a = assignment(30, 1, question(QuestionType.SHORT_ANSWER, 1));
        AssessmentAttempt active = running(a, T0.plusSeconds(60));
        assertThat(service.autoSubmitIfExpired(active.getId())).isFalse();
        clock.advance(Duration.ofSeconds(91)); // deadline + 30s grace passed
        assertThat(service.autoSubmitIfExpired(active.getId())).isTrue();
        assertThat(active.getStatus()).isEqualTo(AttemptStatus.AUTO_SUBMITTED);
    }

    @Test
    void anotherUserCannotTouchTheAttempt() {
        Assignment a = assignment(30, 1, question(QuestionType.SHORT_ANSWER, 1));
        AssessmentAttempt t = running(a, T0.plusSeconds(300));
        assertThatThrownBy(() -> service.submit(t.getId(), candidatePrincipal(99)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("NOT_FOUND");
    }

    // ------------------------------------------------------------------ monitoring

    private ActivityBatchRequest events(ActivityEventInput... events) {
        return new ActivityBatchRequest(List.of(events));
    }

    private static ActivityEventInput ev(ActivityEventType type, Instant at) {
        return new ActivityEventInput(type, at, null, null);
    }

    @Test
    void hiddenAndBlurFiredTogetherCountAsOneViolation() {
        Assignment a = assignment(30, 1, question(QuestionType.SHORT_ANSWER, 1));
        AssessmentAttempt t = running(a, T0.plusSeconds(600));
        Instant at = T0.minusSeconds(5);

        ActivityResponse res = service.recordActivity(t.getId(),
                events(ev(ActivityEventType.TAB_HIDDEN, at), ev(ActivityEventType.WINDOW_BLUR, at.plusMillis(80))), candidate);

        assertThat(res.tabSwitchCount()).isEqualTo(1);
        assertThat(res.focusLossCount()).isEqualTo(1);
        assertThat(res.violationCount()).isEqualTo(1);
        assertThat(res.newViolation()).isTrue();
        assertThat(res.flagged()).isFalse();
    }

    @Test
    void reachingTheLimitFlagsAttemptForReviewWithoutEndingIt() {
        Assignment a = assignment(30, 1, question(QuestionType.SHORT_ANSWER, 1));
        a.setWarningThreshold(1);
        a.setMaxViolations(2);
        a.setViolationAction(ViolationAction.FLAG_FOR_REVIEW);
        AssessmentAttempt t = running(a, T0.plusSeconds(600));

        service.recordActivity(t.getId(), events(ev(ActivityEventType.TAB_HIDDEN, T0.minusSeconds(60))), candidate);
        ActivityResponse res = service.recordActivity(t.getId(), events(ev(ActivityEventType.TAB_HIDDEN, T0.minusSeconds(20))), candidate);

        assertThat(res.violationCount()).isEqualTo(2);
        assertThat(res.flagged()).isTrue();
        assertThat(res.status()).isEqualTo(AttemptStatus.IN_PROGRESS);
        assertThat(t.getFlagReason()).contains("2 times");
    }

    @Test
    void autoSubmitPolicyTerminatesAttemptAtTheLimit() {
        Assignment a = assignment(30, 1, question(QuestionType.SHORT_ANSWER, 1));
        a.setWarningThreshold(1);
        a.setMaxViolations(2);
        a.setViolationAction(ViolationAction.AUTO_SUBMIT);
        AssessmentAttempt t = running(a, T0.plusSeconds(600));

        ActivityResponse res = service.recordActivity(t.getId(), events(
                ev(ActivityEventType.WINDOW_BLUR, T0.minusSeconds(100)),
                ev(ActivityEventType.WINDOW_BLUR, T0.minusSeconds(50))), candidate);

        assertThat(res.status()).isEqualTo(AttemptStatus.TERMINATED);
        assertThat(t.isFlagged()).isTrue();
        verify(gradingService).gradeAttempt(t, T0);
    }

    @Test
    void monitoringDisabledRecordsEventsButCountsNoViolations() {
        Assignment a = assignment(30, 1, question(QuestionType.SHORT_ANSWER, 1));
        a.setMonitoringEnabled(false);
        AssessmentAttempt t = running(a, T0.plusSeconds(600));
        ActivityResponse res = service.recordActivity(t.getId(), events(ev(ActivityEventType.TAB_HIDDEN, T0)), candidate);
        assertThat(res.tabSwitchCount()).isEqualTo(1);
        assertThat(res.violationCount()).isZero();
    }

    @Test
    void serverOnlyEventTypesCannotBeInjectedByTheBrowser() {
        Assignment a = assignment(30, 1, question(QuestionType.SHORT_ANSWER, 1));
        AssessmentAttempt t = running(a, T0.plusSeconds(600));
        service.recordActivity(t.getId(), events(ev(ActivityEventType.SUBMITTED, T0)), candidate);
        assertThat(t.getStatus()).isEqualTo(AttemptStatus.IN_PROGRESS);
        verify(activityRepository, never()).save(any());
    }
}
