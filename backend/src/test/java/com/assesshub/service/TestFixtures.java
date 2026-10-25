package com.assesshub.service;

import com.assesshub.domain.AssessmentAnswer;
import com.assesshub.domain.AssessmentAssignment;
import com.assesshub.domain.AssessmentAttempt;
import com.assesshub.domain.Assignment;
import com.assesshub.domain.AssignmentQuestion;
import com.assesshub.domain.Question;
import com.assesshub.domain.QuestionOption;
import com.assesshub.domain.User;
import com.assesshub.domain.enums.AnswerGradingStatus;
import com.assesshub.domain.enums.AssessmentType;
import com.assesshub.domain.enums.AssigneeStatus;
import com.assesshub.domain.enums.AssignmentStatus;
import com.assesshub.domain.enums.AttemptStatus;
import com.assesshub.domain.enums.Difficulty;
import com.assesshub.domain.enums.QuestionType;
import com.assesshub.domain.enums.RoleName;
import com.assesshub.security.UserPrincipal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Builders for in-memory entities used by unit tests. */
final class TestFixtures {

    private static final AtomicLong IDS = new AtomicLong(1000);

    private TestFixtures() {
    }

    static User user(long id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setFullName(email);
        return u;
    }

    static UserPrincipal candidatePrincipal(long id) {
        return new UserPrincipal(id, "c" + id + "@test.local", "Candidate", null, Set.of(RoleName.CANDIDATE), true, false);
    }

    static Question question(QuestionType type, int marks, boolean... correctFlags) {
        Question q = new Question();
        q.setId(IDS.incrementAndGet());
        q.setType(type);
        q.setTitle(type.name());
        q.setBody("Body");
        q.setDifficulty(Difficulty.BEGINNER);
        q.setDefaultMarks(BigDecimal.valueOf(marks).setScale(2));
        q.setOwner(user(1, "owner@test.local"));
        for (int i = 0; i < correctFlags.length; i++) {
            QuestionOption o = new QuestionOption("Option " + i, correctFlags[i], i);
            o.setId(IDS.incrementAndGet());
            q.addOption(o);
        }
        return q;
    }

    static Assignment assignment(int durationMinutes, int maxAttempts, Question... questions) {
        Assignment a = new Assignment();
        a.setId(IDS.incrementAndGet());
        a.setOwner(user(1, "owner@test.local"));
        a.setTitle("Assessment");
        a.setDifficulty(Difficulty.INTERMEDIATE);
        a.setAssessmentType(AssessmentType.QUIZ);
        a.setDurationMinutes(durationMinutes);
        a.setMaxAttempts(maxAttempts);
        a.setGracePeriodSeconds(30);
        a.setStatus(AssignmentStatus.ACTIVE);
        a.setPassingMarks(BigDecimal.valueOf(2));
        for (int i = 0; i < questions.length; i++) {
            AssignmentQuestion aq = new AssignmentQuestion();
            aq.setAssignment(a);
            aq.setQuestion(questions[i]);
            aq.setDisplayOrder(i);
            aq.setMarks(questions[i].getDefaultMarks());
            a.getQuestions().add(aq);
        }
        a.recalculateTotalMarks();
        return a;
    }

    static AssessmentAssignment assignee(Assignment a, User user) {
        AssessmentAssignment aa = new AssessmentAssignment();
        aa.setId(IDS.incrementAndGet());
        aa.setAssignment(a);
        aa.setUser(user);
        aa.setStatus(AssigneeStatus.ASSIGNED);
        aa.setAssignedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return aa;
    }

    static AssessmentAttempt attempt(AssessmentAssignment aa, Instant startedAt, Instant deadlineAt) {
        AssessmentAttempt t = new AssessmentAttempt();
        t.setId(IDS.incrementAndGet());
        t.setAssessmentAssignment(aa);
        t.setAssignment(aa.getAssignment());
        t.setUser(aa.getUser());
        t.setAttemptNumber(1);
        t.setStatus(AttemptStatus.IN_PROGRESS);
        t.setStartedAt(startedAt);
        t.setDeadlineAt(deadlineAt);
        t.setQuestionOrder("");
        aa.setAttemptsUsed(1);
        aa.setStatus(AssigneeStatus.IN_PROGRESS);
        return t;
    }

    static AssessmentAnswer answer(AssessmentAttempt attempt, Question q, int order) {
        AssessmentAnswer a = new AssessmentAnswer();
        a.setId(IDS.incrementAndGet());
        a.setAttempt(attempt);
        a.setQuestion(q);
        a.setDisplayOrder(order);
        a.setMaxMarks(q.getDefaultMarks());
        a.setGradingStatus(AnswerGradingStatus.PENDING);
        return a;
    }
}
