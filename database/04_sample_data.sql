-- =====================================================================
-- AssessHub - OPTIONAL sample development data (question bank + one assessment).
--
-- Prerequisites:
--   * Schema and reference data exist (Flyway already ran, or you executed 02 and 03).
--   * A user exists whose email matches @owner_email below - normally the Super Admin
--     created on first startup from BOOTSTRAP_ADMIN_EMAIL.
-- No passwords are inserted by this script.
-- Do NOT run this against a production database.
-- =====================================================================

USE assesshub;

SET @owner_email := 'superadmin@example.com';
SET @owner_id := (SELECT id FROM users WHERE email = @owner_email);

-- Abort early (NULL owner violates NOT NULL) if the owner does not exist.
SELECT IF(@owner_id IS NULL, 'ERROR: owner user not found - edit @owner_email', CONCAT('Owner id = ', @owner_id)) AS check_owner;

SET @now := UTC_TIMESTAMP(6);

-- ---------- Frontend: single-choice ----------
INSERT INTO questions (owner_id, type, title, body, skill_category, difficulty, default_marks, visibility, case_sensitive, archived, created_at, updated_at)
VALUES (@owner_id, 'MCQ_SINGLE', 'React state updates',
        'Which React hook should you use to run a side effect after the component renders?',
        'Frontend', 'BEGINNER', 2.00, 'SHARED', FALSE, FALSE, @now, @now);
SET @q1 := LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, is_correct, display_order) VALUES
    (@q1, 'useState', FALSE, 0), (@q1, 'useEffect', TRUE, 1), (@q1, 'useMemo', FALSE, 2), (@q1, 'useRef', FALSE, 3);

-- ---------- Backend: multiple-select ----------
INSERT INTO questions (owner_id, type, title, body, skill_category, difficulty, default_marks, visibility, case_sensitive, archived, created_at, updated_at)
VALUES (@owner_id, 'MCQ_MULTI', 'Idempotent HTTP methods',
        'Select ALL HTTP methods that are idempotent by definition.',
        'Backend', 'INTERMEDIATE', 3.00, 'SHARED', FALSE, FALSE, @now, @now);
SET @q2 := LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, is_correct, display_order) VALUES
    (@q2, 'GET', TRUE, 0), (@q2, 'POST', FALSE, 1), (@q2, 'PUT', TRUE, 2), (@q2, 'DELETE', TRUE, 3), (@q2, 'PATCH', FALSE, 4);

-- ---------- QA: true/false ----------
INSERT INTO questions (owner_id, type, title, body, skill_category, difficulty, default_marks, visibility, case_sensitive, archived, created_at, updated_at)
VALUES (@owner_id, 'TRUE_FALSE', 'Regression testing',
        'True or false: regression testing verifies that recent changes have not broken existing functionality.',
        'QA', 'BEGINNER', 1.00, 'SHARED', FALSE, FALSE, @now, @now);
SET @q3 := LAST_INSERT_ID();
INSERT INTO question_options (question_id, option_text, is_correct, display_order) VALUES
    (@q3, 'True', TRUE, 0), (@q3, 'False', FALSE, 1);

-- ---------- Backend: short answer (auto-graded by accepted answers) ----------
INSERT INTO questions (owner_id, type, title, body, skill_category, difficulty, default_marks, visibility, accepted_answers, case_sensitive, archived, created_at, updated_at)
VALUES (@owner_id, 'SHORT_ANSWER', 'Not Found status code',
        'Which HTTP status code means the requested resource was not found? (number only)',
        'Backend', 'BEGINNER', 1.00, 'SHARED', '404', FALSE, FALSE, @now, @now);
SET @q4 := LAST_INSERT_ID();

-- ---------- Full stack: long answer (manual review) ----------
INSERT INTO questions (owner_id, type, title, body, skill_category, difficulty, default_marks, visibility, evaluation_guidelines, case_sensitive, archived, created_at, updated_at)
VALUES (@owner_id, 'LONG_ANSWER', 'Designing a URL shortener',
        'Describe how you would design a URL shortener. Cover the API, data model, ID generation and scaling.',
        'Full Stack', 'ADVANCED', 10.00, 'SHARED',
        'Award marks for: clear API (2), data model (2), collision-free ID generation (2), caching/scaling (2), trade-offs (2).',
        FALSE, FALSE, @now, @now);
SET @q5 := LAST_INSERT_ID();

-- ---------- Coding (manual review) ----------
INSERT INTO questions (owner_id, type, title, body, skill_category, difficulty, default_marks, visibility, coding_language, starter_code, reference_answer, evaluation_guidelines, case_sensitive, archived, created_at, updated_at)
VALUES (@owner_id, 'CODING', 'Reverse words in a sentence',
        'Write a function reverseWords(s) that returns the words of the sentence in reverse order, separated by single spaces.',
        'Programming', 'INTERMEDIATE', 8.00, 'SHARED', 'javascript',
        'function reverseWords(s) {\n  // your code here\n}\n',
        'function reverseWords(s) {\n  return s.trim().split(/\\s+/).reverse().join(" ");\n}',
        'Correctness (4), handles extra whitespace (2), readability (2).',
        FALSE, FALSE, @now, @now);
SET @q6 := LAST_INSERT_ID();

-- ---------- Sample assessment (draft, ready to publish) ----------
INSERT INTO assignments (owner_id, title, description, instructions, evaluation_criteria, skill_category, difficulty, assessment_type,
                         total_marks, passing_marks, duration_minutes, max_attempts, grace_period_seconds, shuffle_questions, shuffle_options,
                         status, results_release_policy, show_correct_answers, results_released, monitoring_enabled, warning_threshold,
                         max_violations, violation_action, created_at, updated_at)
VALUES (@owner_id, 'Full-Stack Developer Screening',
        'A mixed screening covering frontend, backend, QA fundamentals and a short coding task.',
        'You have 45 minutes. Answers are saved automatically. Stay on this tab: leaving the window is recorded.',
        'Objective questions are auto-graded. Coding and long answers are reviewed manually.',
        'Full Stack', 'INTERMEDIATE', 'MIXED',
        25.00, 15.00, 45, 1, 30, TRUE, TRUE,
        'DRAFT', 'AFTER_GRADING', FALSE, FALSE, TRUE, 3, 5, 'FLAG_FOR_REVIEW', @now, @now);
SET @a1 := LAST_INSERT_ID();

INSERT INTO assignment_questions (assignment_id, question_id, display_order, marks) VALUES
    (@a1, @q1, 0, 2.00), (@a1, @q2, 1, 3.00), (@a1, @q3, 2, 1.00),
    (@a1, @q4, 3, 1.00), (@a1, @q5, 4, 10.00), (@a1, @q6, 5, 8.00);

SELECT 'Sample data inserted' AS result, @a1 AS assignment_id;
