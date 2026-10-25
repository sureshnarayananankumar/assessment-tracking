package com.assesshub.config;

import com.assesshub.domain.AssessmentAssignment;
import com.assesshub.domain.Assignment;
import com.assesshub.domain.AssignmentQuestion;
import com.assesshub.domain.Question;
import com.assesshub.domain.QuestionOption;
import com.assesshub.domain.User;
import com.assesshub.domain.enums.AssessmentType;
import com.assesshub.domain.enums.AssigneeStatus;
import com.assesshub.domain.enums.AssignmentStatus;
import com.assesshub.domain.enums.Difficulty;
import com.assesshub.domain.enums.QuestionType;
import com.assesshub.domain.enums.QuestionVisibility;
import com.assesshub.domain.enums.ReleasePolicy;
import com.assesshub.domain.enums.RoleName;
import com.assesshub.domain.enums.UserStatus;
import com.assesshub.domain.enums.ViolationAction;
import com.assesshub.dto.PasswordPolicy;
import com.assesshub.repository.AssessmentAssignmentRepository;
import com.assesshub.repository.AssignmentRepository;
import com.assesshub.repository.QuestionRepository;
import com.assesshub.repository.RoleRepository;
import com.assesshub.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * OPTIONAL development seed (DEMO_DATA_ENABLED=true). Creates a demo recruiter, a demo candidate,
 * a mixed question bank and an active assessment assigned to the candidate. Demo accounts use the
 * password from DEMO_USER_PASSWORD - nothing is hardcoded. Never enable in production.
 */
@Component
@Order(2)
@ConditionalOnProperty(prefix = "app.demo-data", name = "enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    static final String RECRUITER_EMAIL = "recruiter@demo.local";
    static final String CANDIDATE_EMAIL = "candidate@demo.local";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final QuestionRepository questionRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssessmentAssignmentRepository assigneeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;
    private final Clock clock;

    public DemoDataSeeder(UserRepository userRepository, RoleRepository roleRepository,
                          QuestionRepository questionRepository, AssignmentRepository assignmentRepository,
                          AssessmentAssignmentRepository assigneeRepository, PasswordEncoder passwordEncoder,
                          AppProperties props, Clock clock) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.questionRepository = questionRepository;
        this.assignmentRepository = assignmentRepository;
        this.assigneeRepository = assigneeRepository;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String password = props.demoData().password();
        if (!PasswordPolicy.isValid(password)) {
            log.warn("DEMO_DATA_ENABLED=true but DEMO_USER_PASSWORD is missing or weak ({}). Demo data skipped.",
                    PasswordPolicy.MESSAGE);
            return;
        }
        if (userRepository.existsByEmailIgnoreCase(RECRUITER_EMAIL)) {
            return;
        }
        String hash = passwordEncoder.encode(password);
        User recruiter = user(RECRUITER_EMAIL, "Riley Recruiter", RoleName.RECRUITER, hash, "Talent Acquisition");
        User candidate = user(CANDIDATE_EMAIL, "Casey Candidate", RoleName.CANDIDATE, hash, null);

        List<Question> questions = List.of(
                options(recruiter, QuestionType.MCQ_SINGLE, "React effects", "Which hook runs side effects after render?",
                        "Frontend", 2, new String[]{"useState", "useEffect", "useMemo", "useRef"}, new boolean[]{false, true, false, false}),
                options(recruiter, QuestionType.MCQ_MULTI, "Idempotent HTTP methods", "Select ALL idempotent HTTP methods.",
                        "Backend", 3, new String[]{"GET", "POST", "PUT", "DELETE"}, new boolean[]{true, false, true, true}),
                options(recruiter, QuestionType.TRUE_FALSE, "Regression testing",
                        "Regression testing checks that new changes did not break existing behaviour.", "QA", 1,
                        new String[]{"True", "False"}, new boolean[]{true, false}),
                shortAnswer(recruiter),
                text(recruiter, QuestionType.LONG_ANSWER, "Design a rate limiter",
                        "Explain how you would design an API rate limiter for a multi-instance service.", "Backend", 6),
                coding(recruiter));
        questionRepository.saveAll(questions);

        Instant now = clock.instant();
        Assignment a = new Assignment();
        a.setOwner(recruiter);
        a.setTitle("Demo: Full-Stack Screening");
        a.setDescription("A short mixed assessment to try the candidate experience.");
        a.setInstructions("You have 20 minutes. Answers save automatically. Leaving this tab is recorded for review.");
        a.setEvaluationCriteria("Objective questions are auto-graded; descriptive and coding answers are reviewed manually.");
        a.setSkillCategory("Full Stack");
        a.setDifficulty(Difficulty.INTERMEDIATE);
        a.setAssessmentType(AssessmentType.MIXED);
        a.setPassingMarks(BigDecimal.valueOf(10));
        a.setDurationMinutes(20);
        a.setMaxAttempts(2);
        a.setGracePeriodSeconds(30);
        a.setShuffleQuestions(true);
        a.setShuffleOptions(true);
        a.setStatus(AssignmentStatus.ACTIVE);
        a.setPublishedAt(now);
        a.setEndAt(now.plus(Duration.ofDays(14)));
        a.setResultsReleasePolicy(ReleasePolicy.AFTER_GRADING);
        a.setShowCorrectAnswers(true);
        a.setMonitoringEnabled(true);
        a.setWarningThreshold(2);
        a.setMaxViolations(5);
        a.setViolationAction(ViolationAction.FLAG_FOR_REVIEW);
        for (int i = 0; i < questions.size(); i++) {
            AssignmentQuestion aq = new AssignmentQuestion();
            aq.setAssignment(a);
            aq.setQuestion(questions.get(i));
            aq.setDisplayOrder(i);
            aq.setMarks(questions.get(i).getDefaultMarks());
            a.getQuestions().add(aq);
        }
        a.recalculateTotalMarks();
        assignmentRepository.save(a);

        AssessmentAssignment aa = new AssessmentAssignment();
        aa.setAssignment(a);
        aa.setUser(candidate);
        aa.setAssignedBy(recruiter);
        aa.setStatus(AssigneeStatus.ASSIGNED);
        aa.setAssignedAt(now);
        assigneeRepository.save(aa);
        log.info("Demo data created: {} (recruiter) and {} (candidate) using DEMO_USER_PASSWORD", RECRUITER_EMAIL, CANDIDATE_EMAIL);
    }

    private User user(String email, String name, RoleName role, String hash, String department) {
        User u = new User();
        u.setEmail(email);
        u.setFullName(name);
        u.setPasswordHash(hash);
        u.setStatus(UserStatus.ACTIVE);
        u.setEmailVerified(true);
        u.setDepartment(department);
        u.setRoles(new HashSet<>(Set.of(roleRepository.findByName(role).orElseThrow())));
        return userRepository.save(u);
    }

    private static Question base(User owner, QuestionType type, String title, String body, String skill, int marks) {
        Question q = new Question();
        q.setOwner(owner);
        q.setType(type);
        q.setTitle(title);
        q.setBody(body);
        q.setSkillCategory(skill);
        q.setDifficulty(Difficulty.INTERMEDIATE);
        q.setDefaultMarks(BigDecimal.valueOf(marks).setScale(2));
        q.setVisibility(QuestionVisibility.SHARED);
        return q;
    }

    private static Question options(User owner, QuestionType type, String title, String body, String skill, int marks,
                                    String[] texts, boolean[] correct) {
        Question q = base(owner, type, title, body, skill, marks);
        for (int i = 0; i < texts.length; i++) {
            q.addOption(new QuestionOption(texts[i], correct[i], i));
        }
        return q;
    }

    private static Question shortAnswer(User owner) {
        Question q = base(owner, QuestionType.SHORT_ANSWER, "HTTP Not Found",
                "Which HTTP status code means 'Not Found'? (number only)", "Backend", 1);
        q.setAcceptedAnswers("404");
        return q;
    }

    private static Question text(User owner, QuestionType type, String title, String body, String skill, int marks) {
        Question q = base(owner, type, title, body, skill, marks);
        q.setEvaluationGuidelines("Algorithm choice (2), distributed state (2), trade-offs (2).");
        return q;
    }

    private static Question coding(User owner) {
        Question q = base(owner, QuestionType.CODING, "Reverse words",
                "Implement reverseWords(s) returning the words of s in reverse order separated by single spaces.",
                "Programming", 5);
        q.setCodingLanguage("javascript");
        q.setStarterCode("function reverseWords(s) {\n  // your code here\n}\n");
        q.setReferenceAnswer("const reverseWords = s => s.trim().split(/\\s+/).reverse().join(' ');");
        q.setEvaluationGuidelines("Correctness (3), whitespace handling (1), readability (1).");
        return q;
    }
}
