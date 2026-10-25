package com.assesshub.service;

import com.assesshub.common.exception.ApiException;
import com.assesshub.domain.AssessmentAnswer;
import com.assesshub.domain.AssessmentAttempt;
import com.assesshub.domain.AssessmentResult;
import com.assesshub.domain.Assignment;
import com.assesshub.domain.Question;
import com.assesshub.domain.enums.AnswerGradingStatus;
import com.assesshub.domain.enums.AttemptStatus;
import com.assesshub.domain.enums.EmailTemplate;
import com.assesshub.domain.enums.QuestionType;
import com.assesshub.domain.enums.ReleasePolicy;
import com.assesshub.domain.enums.ResultGradingStatus;
import com.assesshub.dto.ResultDtos.GradeItem;
import com.assesshub.dto.ResultDtos.GradeRequest;
import com.assesshub.repository.AssessmentAnswerRepository;
import com.assesshub.repository.AssessmentResultRepository;
import com.assesshub.service.email.EmailRequestedEvent;
import com.assesshub.service.email.EmailTemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.assesshub.service.TestFixtures.answer;
import static com.assesshub.service.TestFixtures.assignee;
import static com.assesshub.service.TestFixtures.assignment;
import static com.assesshub.service.TestFixtures.attempt;
import static com.assesshub.service.TestFixtures.question;
import static com.assesshub.service.TestFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GradingServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    AssessmentAnswerRepository answerRepository;
    @Mock
    AssessmentResultRepository resultRepository;
    @Mock
    EmailTemplateService emailTemplates;
    @Mock
    ApplicationEventPublisher events;

    GradingService service;

    @BeforeEach
    void setUp() {
        service = new GradingService(answerRepository, resultRepository, emailTemplates, events, new ObjectMapper());
        when(resultRepository.findByAttemptId(any())).thenReturn(Optional.empty());
        when(resultRepository.save(any(AssessmentResult.class))).thenAnswer(inv -> inv.getArgument(0));
        when(emailTemplates.resultReleased(any(), any(), any())).thenReturn(new EmailRequestedEvent(
                "c@test.local", 10L, EmailTemplate.RESULT_RELEASED, "subject", "<p>html</p>", "text", "ATTEMPT", 1L, "link"));
    }

    @Test
    void singleChoiceIsCorrectOnlyForTheCorrectOption() {
        Question q = question(QuestionType.MCQ_SINGLE, 2, false, true, false);
        AssessmentAnswer right = answer(null, q, 0);
        right.setSelectedOptionIds(String.valueOf(q.getOptions().get(1).getId()));
        AssessmentAnswer wrong = answer(null, q, 0);
        wrong.setSelectedOptionIds(String.valueOf(q.getOptions().get(0).getId()));

        service.autoGrade(right, NOW);
        service.autoGrade(wrong, NOW);

        assertThat(right.getCorrect()).isTrue();
        assertThat(right.getMarksAwarded()).isEqualByComparingTo("2");
        assertThat(wrong.getCorrect()).isFalse();
        assertThat(wrong.getMarksAwarded()).isEqualByComparingTo("0");
        assertThat(right.getGradingStatus()).isEqualTo(AnswerGradingStatus.AUTO_GRADED);
    }

    @Test
    void multiSelectRequiresTheExactSetOfCorrectOptions() {
        Question q = question(QuestionType.MCQ_MULTI, 3, true, false, true);
        Long a = q.getOptions().get(0).getId();
        Long b = q.getOptions().get(1).getId();
        Long c = q.getOptions().get(2).getId();

        AssessmentAnswer exact = answer(null, q, 0);
        exact.setSelectedOptionIds(a + "," + c);
        AssessmentAnswer partial = answer(null, q, 0);
        partial.setSelectedOptionIds(String.valueOf(a));
        AssessmentAnswer extra = answer(null, q, 0);
        extra.setSelectedOptionIds(a + "," + b + "," + c);

        service.autoGrade(exact, NOW);
        service.autoGrade(partial, NOW);
        service.autoGrade(extra, NOW);

        assertThat(exact.getMarksAwarded()).isEqualByComparingTo("3");
        assertThat(partial.getMarksAwarded()).isEqualByComparingTo("0");
        assertThat(extra.getMarksAwarded()).isEqualByComparingTo("0");
    }

    @Test
    void shortAnswerMatchesAcceptedAnswersIgnoringCaseAndWhitespace() {
        assertThat(GradingService.matchesAccepted("  Not   Found ", List.of("not found"), false)).isTrue();
        assertThat(GradingService.matchesAccepted("Not Found", List.of("not found"), true)).isFalse();
        assertThat(GradingService.matchesAccepted("", List.of("x"), false)).isFalse();

        Question q = question(QuestionType.SHORT_ANSWER, 1);
        q.setAcceptedAnswers("404\nfour hundred four");
        AssessmentAnswer ans = answer(null, q, 0);
        ans.setTextAnswer("404");
        service.autoGrade(ans, NOW);
        assertThat(ans.getCorrect()).isTrue();
    }

    @Test
    void descriptiveAnswersWaitForManualReviewButBlanksScoreZero() {
        Question longQ = question(QuestionType.LONG_ANSWER, 10);
        AssessmentAnswer answered = answer(null, longQ, 0);
        answered.setTextAnswer("My design...");
        AssessmentAnswer blank = answer(null, longQ, 1);

        service.autoGrade(answered, NOW);
        service.autoGrade(blank, NOW);

        assertThat(answered.getGradingStatus()).isEqualTo(AnswerGradingStatus.PENDING);
        assertThat(answered.getMarksAwarded()).isNull();
        assertThat(blank.getGradingStatus()).isEqualTo(AnswerGradingStatus.AUTO_GRADED);
        assertThat(blank.getMarksAwarded()).isEqualByComparingTo("0");
    }

    @Test
    void gradeAttemptAggregatesScoreAndReleasesWhenFullyGraded() {
        Question mcq = question(QuestionType.MCQ_SINGLE, 2, true, false);
        Question tf = question(QuestionType.TRUE_FALSE, 1, true, false);
        Assignment a = assignment(30, 1, mcq, tf);
        a.setPassingMarks(BigDecimal.valueOf(2));
        a.setResultsReleasePolicy(ReleasePolicy.AFTER_GRADING);
        AssessmentAttempt t = attempt(assignee(a, user(10, "c@test.local")), NOW.minusSeconds(60), NOW.plusSeconds(600));
        t.setStatus(AttemptStatus.SUBMITTED);
        AssessmentAnswer a1 = answer(t, mcq, 0);
        a1.setSelectedOptionIds(String.valueOf(mcq.getOptions().get(0).getId()));
        AssessmentAnswer a2 = answer(t, tf, 1);
        a2.setSelectedOptionIds(String.valueOf(tf.getOptions().get(1).getId()));
        when(answerRepository.findByAttemptIdWithQuestion(t.getId())).thenReturn(List.of(a1, a2));

        AssessmentResult result = service.gradeAttempt(t, NOW);

        assertThat(result.getTotalScore()).isEqualByComparingTo("2");
        assertThat(result.getMaxScore()).isEqualByComparingTo("3");
        assertThat(result.getPercentage()).isEqualByComparingTo("66.67");
        assertThat(result.getGradingStatus()).isEqualTo(ResultGradingStatus.GRADED);
        assertThat(result.getPassed()).isTrue();
        assertThat(result.isReleased()).isTrue();
        verify(events).publishEvent(any(EmailRequestedEvent.class));
    }

    @Test
    void manualReleasePolicyKeepsResultsHidden() {
        Question mcq = question(QuestionType.MCQ_SINGLE, 2, true, false);
        Assignment a = assignment(30, 1, mcq);
        a.setResultsReleasePolicy(ReleasePolicy.MANUAL);
        AssessmentAttempt t = attempt(assignee(a, user(10, "c@test.local")), NOW.minusSeconds(60), NOW.plusSeconds(600));
        t.setStatus(AttemptStatus.SUBMITTED);
        when(answerRepository.findByAttemptIdWithQuestion(t.getId())).thenReturn(List.of(answer(t, mcq, 0)));

        AssessmentResult result = service.gradeAttempt(t, NOW);

        assertThat(result.getGradingStatus()).isEqualTo(ResultGradingStatus.GRADED);
        assertThat(result.isReleased()).isFalse();
        verify(events, never()).publishEvent(any(EmailRequestedEvent.class));
    }

    @Test
    void manualGradesCannotExceedMaximumMarks() {
        Question longQ = question(QuestionType.LONG_ANSWER, 5);
        Assignment a = assignment(30, 1, longQ);
        AssessmentAttempt t = attempt(assignee(a, user(10, "c@test.local")), NOW.minusSeconds(60), NOW.plusSeconds(600));
        t.setStatus(AttemptStatus.SUBMITTED);
        AssessmentAnswer ans = answer(t, longQ, 0);
        ans.setTextAnswer("text");
        when(answerRepository.findByAttemptIdWithQuestion(t.getId())).thenReturn(List.of(ans));

        GradeRequest tooMuch = new GradeRequest(List.of(new GradeItem(ans.getId(), BigDecimal.valueOf(6), null)), null, null);
        assertThatThrownBy(() -> service.applyManualGrades(t, tooMuch, null, NOW))
                .isInstanceOf(ApiException.class).hasMessageContaining("cannot exceed");

        GradeRequest ok = new GradeRequest(List.of(new GradeItem(ans.getId(), BigDecimal.valueOf(4), "Good")), "Nice work", null);
        AssessmentResult result = service.applyManualGrades(t, ok, null, NOW);
        assertThat(result.getManualScore()).isEqualByComparingTo("4");
        assertThat(result.getFeedback()).isEqualTo("Nice work");
        assertThat(result.getGradingStatus()).isEqualTo(ResultGradingStatus.GRADED);
    }

    @Test
    void cannotGradeAnAttemptThatIsStillRunning() {
        Question longQ = question(QuestionType.LONG_ANSWER, 5);
        AssessmentAttempt t = attempt(assignee(assignment(30, 1, longQ), user(10, "c@test.local")),
                NOW.minusSeconds(60), NOW.plusSeconds(600));
        assertThatThrownBy(() -> service.applyManualGrades(t, new GradeRequest(List.of(), null, null), null, NOW))
                .isInstanceOf(ApiException.class).hasMessageContaining("submitted");
    }
}
