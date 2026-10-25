-- =====================================================================
-- AssessHub - Assignment & Assessment Management System
-- V1: core relational schema (MySQL 8.x, InnoDB, utf8mb4)
-- All DATETIME(6) columns store UTC instants.
-- This file is applied automatically by Flyway on backend startup.
-- An identical copy lives in database/02_schema.sql for MySQL Workbench.
-- =====================================================================

CREATE TABLE roles (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(30)  NOT NULL,
    description VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_roles_name UNIQUE (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE users (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    email                 VARCHAR(255) NOT NULL,
    password_hash         VARCHAR(100) NULL,
    full_name             VARCHAR(150) NOT NULL,
    department            VARCHAR(100) NULL,
    job_title             VARCHAR(100) NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    email_verified        BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    locked_until          DATETIME(6)  NULL,
    last_login_at         DATETIME(6)  NULL,
    created_by            BIGINT       NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_users_status (status),
    INDEX idx_users_full_name (full_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    INDEX idx_user_roles_role (role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE refresh_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    token_hash CHAR(64)     NOT NULL,
    expires_at DATETIME(6)  NOT NULL,
    revoked_at DATETIME(6)  NULL,
    ip_address VARCHAR(45)  NULL,
    user_agent VARCHAR(512) NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_refresh_tokens_user (user_id),
    INDEX idx_refresh_tokens_expires (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE password_reset_tokens (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    token_hash CHAR(64)    NOT NULL,
    purpose    VARCHAR(20) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6) NULL,
    request_ip VARCHAR(45) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_password_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_password_reset_tokens_user (user_id),
    INDEX idx_password_reset_tokens_expires (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE questions (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    owner_id              BIGINT       NOT NULL,
    type                  VARCHAR(20)  NOT NULL,
    title                 VARCHAR(255) NOT NULL,
    body                  TEXT         NOT NULL,
    skill_category        VARCHAR(100) NULL,
    difficulty            VARCHAR(20)  NOT NULL,
    default_marks         DECIMAL(8, 2) NOT NULL,
    visibility            VARCHAR(20)  NOT NULL DEFAULT 'PRIVATE',
    coding_language       VARCHAR(40)  NULL,
    starter_code          TEXT         NULL,
    reference_answer      TEXT         NULL,
    evaluation_guidelines TEXT         NULL,
    accepted_answers      TEXT         NULL,
    case_sensitive        BOOLEAN      NOT NULL DEFAULT FALSE,
    allowed_file_types    VARCHAR(255) NULL,
    max_file_size_mb      INT          NULL,
    tags                  VARCHAR(255) NULL,
    archived              BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_questions_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT chk_questions_marks CHECK (default_marks >= 0),
    INDEX idx_questions_owner (owner_id),
    INDEX idx_questions_type (type),
    INDEX idx_questions_skill (skill_category),
    INDEX idx_questions_visibility (archived, visibility)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE question_options (
    id            BIGINT  NOT NULL AUTO_INCREMENT,
    question_id   BIGINT  NOT NULL,
    option_text   TEXT    NOT NULL,
    is_correct    BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_question_options_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE,
    INDEX idx_question_options_question (question_id, display_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE assignments (
    id                     BIGINT        NOT NULL AUTO_INCREMENT,
    owner_id               BIGINT        NOT NULL,
    title                  VARCHAR(200)  NOT NULL,
    description            TEXT          NULL,
    instructions           TEXT          NULL,
    evaluation_criteria    TEXT          NULL,
    skill_category         VARCHAR(100)  NULL,
    difficulty             VARCHAR(20)   NOT NULL,
    assessment_type        VARCHAR(30)   NOT NULL,
    total_marks            DECIMAL(8, 2) NOT NULL DEFAULT 0,
    passing_marks          DECIMAL(8, 2) NOT NULL DEFAULT 0,
    start_at               DATETIME(6)   NULL,
    end_at                 DATETIME(6)   NULL,
    duration_minutes       INT           NOT NULL,
    max_attempts           INT           NOT NULL DEFAULT 1,
    grace_period_seconds   INT           NOT NULL DEFAULT 30,
    shuffle_questions      BOOLEAN       NOT NULL DEFAULT FALSE,
    shuffle_options        BOOLEAN       NOT NULL DEFAULT FALSE,
    status                 VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    results_release_policy VARCHAR(20)   NOT NULL DEFAULT 'AFTER_GRADING',
    show_correct_answers   BOOLEAN       NOT NULL DEFAULT FALSE,
    results_released       BOOLEAN       NOT NULL DEFAULT FALSE,
    results_released_at    DATETIME(6)   NULL,
    monitoring_enabled     BOOLEAN       NOT NULL DEFAULT TRUE,
    warning_threshold      INT           NOT NULL DEFAULT 3,
    max_violations         INT           NOT NULL DEFAULT 5,
    violation_action       VARCHAR(20)   NOT NULL DEFAULT 'FLAG_FOR_REVIEW',
    published_at           DATETIME(6)   NULL,
    created_at             DATETIME(6)   NOT NULL,
    updated_at             DATETIME(6)   NOT NULL,
    version                BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_assignments_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT chk_assignments_duration CHECK (duration_minutes > 0),
    CONSTRAINT chk_assignments_attempts CHECK (max_attempts > 0),
    CONSTRAINT chk_assignments_passing CHECK (passing_marks >= 0),
    CONSTRAINT chk_assignments_grace CHECK (grace_period_seconds >= 0),
    INDEX idx_assignments_owner_status (owner_id, status),
    INDEX idx_assignments_status_end (status, end_at),
    INDEX idx_assignments_skill (skill_category)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE assignment_questions (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT        NOT NULL,
    question_id   BIGINT        NOT NULL,
    display_order INT           NOT NULL DEFAULT 0,
    marks         DECIMAL(8, 2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_assignment_questions UNIQUE (assignment_id, question_id),
    CONSTRAINT fk_assignment_questions_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id) ON DELETE CASCADE,
    CONSTRAINT fk_assignment_questions_question FOREIGN KEY (question_id) REFERENCES questions (id),
    CONSTRAINT chk_assignment_questions_marks CHECK (marks >= 0),
    INDEX idx_assignment_questions_question (question_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE assignment_attachments (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT       NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    stored_name   VARCHAR(100) NOT NULL,
    content_type  VARCHAR(100) NULL,
    size_bytes    BIGINT       NOT NULL,
    uploaded_by   BIGINT       NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_assignment_attachments_stored UNIQUE (stored_name),
    CONSTRAINT fk_assignment_attachments_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id) ON DELETE CASCADE,
    CONSTRAINT fk_assignment_attachments_user FOREIGN KEY (uploaded_by) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_assignment_attachments_assignment (assignment_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- One row per (assessment, assignee): the authorisation for a user to take an assessment.
CREATE TABLE assessment_assignments (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    assignment_id    BIGINT      NOT NULL,
    user_id          BIGINT      NOT NULL,
    assigned_by      BIGINT      NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED',
    due_at           DATETIME(6) NULL,
    attempts_used    INT         NOT NULL DEFAULT 0,
    reminder_count   INT         NOT NULL DEFAULT 0,
    last_reminder_at DATETIME(6) NULL,
    assigned_at      DATETIME(6) NOT NULL,
    completed_at     DATETIME(6) NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    version          BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_assessment_assignments UNIQUE (assignment_id, user_id),
    CONSTRAINT fk_assessment_assignments_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id) ON DELETE CASCADE,
    CONSTRAINT fk_assessment_assignments_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_assessment_assignments_assigned_by FOREIGN KEY (assigned_by) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_assessment_assignments_user_status (user_id, status),
    INDEX idx_assessment_assignments_assignment_status (assignment_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Email invitation tokens. Only the SHA-256 hash of the token is stored.
CREATE TABLE assessment_invitations (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    assessment_assignment_id BIGINT       NOT NULL,
    assignment_id            BIGINT       NOT NULL,
    user_id                  BIGINT       NOT NULL,
    email                    VARCHAR(255) NOT NULL,
    token_hash               CHAR(64)     NOT NULL,
    status                   VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    expires_at               DATETIME(6)  NOT NULL,
    sent_at                  DATETIME(6)  NULL,
    accepted_at              DATETIME(6)  NULL,
    last_used_at             DATETIME(6)  NULL,
    revoked_at               DATETIME(6)  NULL,
    invited_by               BIGINT       NULL,
    created_at               DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_assessment_invitations_token UNIQUE (token_hash),
    CONSTRAINT fk_assessment_invitations_aa FOREIGN KEY (assessment_assignment_id) REFERENCES assessment_assignments (id) ON DELETE CASCADE,
    CONSTRAINT fk_assessment_invitations_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id) ON DELETE CASCADE,
    CONSTRAINT fk_assessment_invitations_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_assessment_invitations_invited_by FOREIGN KEY (invited_by) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_assessment_invitations_aa (assessment_assignment_id),
    INDEX idx_assessment_invitations_status_expiry (status, expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE assessment_attempts (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    assessment_assignment_id BIGINT       NOT NULL,
    assignment_id            BIGINT       NOT NULL,
    user_id                  BIGINT       NOT NULL,
    attempt_number           INT          NOT NULL,
    status                   VARCHAR(20)  NOT NULL,
    started_at               DATETIME(6)  NOT NULL,
    deadline_at              DATETIME(6)  NOT NULL,
    submitted_at             DATETIME(6)  NULL,
    submission_reason        VARCHAR(255) NULL,
    question_order           TEXT         NOT NULL,
    option_order             TEXT         NULL,
    tab_switch_count         INT          NOT NULL DEFAULT 0,
    focus_loss_count         INT          NOT NULL DEFAULT 0,
    violation_count          INT          NOT NULL DEFAULT 0,
    total_away_ms            BIGINT       NOT NULL DEFAULT 0,
    last_violation_at        DATETIME(6)  NULL,
    flagged                  BOOLEAN      NOT NULL DEFAULT FALSE,
    flag_reason              VARCHAR(255) NULL,
    ip_address               VARCHAR(45)  NULL,
    user_agent               VARCHAR(512) NULL,
    last_saved_at            DATETIME(6)  NULL,
    created_at               DATETIME(6)  NOT NULL,
    updated_at               DATETIME(6)  NOT NULL,
    version                  BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_assessment_attempts_number UNIQUE (assessment_assignment_id, attempt_number),
    CONSTRAINT fk_assessment_attempts_aa FOREIGN KEY (assessment_assignment_id) REFERENCES assessment_assignments (id) ON DELETE CASCADE,
    CONSTRAINT fk_assessment_attempts_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id) ON DELETE CASCADE,
    CONSTRAINT fk_assessment_attempts_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_assessment_attempts_status_deadline (status, deadline_at),
    INDEX idx_assessment_attempts_assignment_status (assignment_id, status),
    INDEX idx_assessment_attempts_user (user_id),
    INDEX idx_assessment_attempts_flagged (flagged)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE assessment_answers (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    attempt_id          BIGINT        NOT NULL,
    question_id         BIGINT        NOT NULL,
    display_order       INT           NOT NULL DEFAULT 0,
    selected_option_ids VARCHAR(1000) NULL,
    text_answer         LONGTEXT      NULL,
    code_answer         LONGTEXT      NULL,
    code_language       VARCHAR(40)   NULL,
    file_original_name  VARCHAR(255)  NULL,
    file_stored_name    VARCHAR(100)  NULL,
    file_content_type   VARCHAR(100)  NULL,
    file_size_bytes     BIGINT        NULL,
    max_marks           DECIMAL(8, 2) NOT NULL,
    marks_awarded       DECIMAL(8, 2) NULL,
    is_correct          BOOLEAN       NULL,
    grading_status      VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    evaluator_comment   TEXT          NULL,
    graded_by           BIGINT        NULL,
    graded_at           DATETIME(6)   NULL,
    saved_at            DATETIME(6)   NULL,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_assessment_answers UNIQUE (attempt_id, question_id),
    CONSTRAINT fk_assessment_answers_attempt FOREIGN KEY (attempt_id) REFERENCES assessment_attempts (id) ON DELETE CASCADE,
    CONSTRAINT fk_assessment_answers_question FOREIGN KEY (question_id) REFERENCES questions (id),
    CONSTRAINT fk_assessment_answers_graded_by FOREIGN KEY (graded_by) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_assessment_answers_grading (grading_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE assessment_activity_logs (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    attempt_id           BIGINT       NOT NULL,
    event_type           VARCHAR(30)  NOT NULL,
    client_timestamp     DATETIME(6)  NULL,
    server_timestamp     DATETIME(6)  NOT NULL,
    duration_ms          BIGINT       NULL,
    details              VARCHAR(500) NULL,
    counted_as_violation BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT fk_activity_logs_attempt FOREIGN KEY (attempt_id) REFERENCES assessment_attempts (id) ON DELETE CASCADE,
    INDEX idx_activity_logs_attempt_time (attempt_id, server_timestamp),
    INDEX idx_activity_logs_type (event_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE assessment_results (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    attempt_id       BIGINT        NOT NULL,
    assignment_id    BIGINT        NOT NULL,
    user_id          BIGINT        NOT NULL,
    auto_score       DECIMAL(8, 2) NOT NULL DEFAULT 0,
    manual_score     DECIMAL(8, 2) NOT NULL DEFAULT 0,
    total_score      DECIMAL(8, 2) NOT NULL DEFAULT 0,
    max_score        DECIMAL(8, 2) NOT NULL DEFAULT 0,
    percentage       DECIMAL(5, 2) NOT NULL DEFAULT 0,
    passed           BOOLEAN       NULL,
    grading_status   VARCHAR(20)   NOT NULL,
    released         BOOLEAN       NOT NULL DEFAULT FALSE,
    released_at      DATETIME(6)   NULL,
    feedback         TEXT          NULL,
    graded_by        BIGINT        NULL,
    graded_at        DATETIME(6)   NULL,
    tab_switch_count INT           NOT NULL DEFAULT 0,
    focus_loss_count INT           NOT NULL DEFAULT 0,
    violation_count  INT           NOT NULL DEFAULT 0,
    flagged          BOOLEAN       NOT NULL DEFAULT FALSE,
    activity_summary TEXT          NULL,
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_assessment_results_attempt UNIQUE (attempt_id),
    CONSTRAINT fk_assessment_results_attempt FOREIGN KEY (attempt_id) REFERENCES assessment_attempts (id) ON DELETE CASCADE,
    CONSTRAINT fk_assessment_results_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id) ON DELETE CASCADE,
    CONSTRAINT fk_assessment_results_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_assessment_results_graded_by FOREIGN KEY (graded_by) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_assessment_results_assignment (assignment_id),
    INDEX idx_assessment_results_user (user_id),
    INDEX idx_assessment_results_grading (grading_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Email delivery log. Message bodies and links are intentionally NOT stored (they contain secrets).
CREATE TABLE email_notifications (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    recipient_email   VARCHAR(255)  NOT NULL,
    recipient_user_id BIGINT        NULL,
    template          VARCHAR(40)   NOT NULL,
    subject           VARCHAR(255)  NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    error_message     VARCHAR(1000) NULL,
    related_type      VARCHAR(40)   NULL,
    related_id        BIGINT        NULL,
    sent_at           DATETIME(6)   NULL,
    created_at        DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_email_notifications_user FOREIGN KEY (recipient_user_id) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_email_notifications_status (status),
    INDEX idx_email_notifications_recipient (recipient_email),
    INDEX idx_email_notifications_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE audit_logs (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    actor_id    BIGINT       NULL,
    actor_email VARCHAR(255) NULL,
    action      VARCHAR(60)  NOT NULL,
    entity_type VARCHAR(60)  NULL,
    entity_id   VARCHAR(64)  NULL,
    details     TEXT         NULL,
    ip_address  VARCHAR(45)  NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_audit_logs_created (created_at),
    INDEX idx_audit_logs_actor (actor_id),
    INDEX idx_audit_logs_entity (entity_type, entity_id),
    INDEX idx_audit_logs_action (action)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE app_settings (
    setting_key   VARCHAR(100)  NOT NULL,
    setting_value VARCHAR(1000) NOT NULL,
    description   VARCHAR(255)  NULL,
    updated_by    BIGINT        NULL,
    updated_at    DATETIME(6)   NOT NULL,
    PRIMARY KEY (setting_key),
    CONSTRAINT fk_app_settings_user FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
