-- Generated from backend/src/main/resources/db/migration/V2__seed_reference_data.sql - keep in sync.
USE assesshub;

-- =====================================================================
-- V2: reference data (roles and default application settings).
-- No user accounts or passwords are created here. The first Super Admin
-- is created at startup from BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD.
-- An identical copy lives in database/03_seed_reference_data.sql.
-- =====================================================================

INSERT INTO roles (name, description) VALUES
    ('SUPER_ADMIN', 'Full access: users, permissions, settings, audit logs and all records'),
    ('ADMIN',       'Manage assignments, questions, schedules, invitations, results and reports'),
    ('RECRUITER',   'Create and manage own assignments, invite candidates and review their results'),
    ('CANDIDATE',   'Take assigned assessments and view released results');

INSERT INTO app_settings (setting_key, setting_value, description, updated_at) VALUES
    ('org.name',                          'AssessHub',      'Organisation name shown in the UI and emails', UTC_TIMESTAMP(6)),
    ('invitation.expiry.hours',           '168',            'Default lifetime of invitation links in hours', UTC_TIMESTAMP(6)),
    ('assessment.default.grace.seconds',  '30',             'Default grace period accepted after the deadline (network latency)', UTC_TIMESTAMP(6)),
    ('assessment.default.duration.minutes','60',            'Default duration for new assessments', UTC_TIMESTAMP(6)),
    ('monitoring.default.warning.threshold','3',            'Focus-loss events before a stronger warning is shown', UTC_TIMESTAMP(6)),
    ('monitoring.default.max.violations', '5',              'Focus-loss events before the configured action is applied', UTC_TIMESTAMP(6)),
    ('monitoring.default.action',         'FLAG_FOR_REVIEW','WARN_ONLY, FLAG_FOR_REVIEW or AUTO_SUBMIT', UTC_TIMESTAMP(6)),
    ('results.default.release.policy',    'AFTER_GRADING',  'AFTER_GRADING or MANUAL', UTC_TIMESTAMP(6)),
    ('email.reminders.enabled',           'true',           'Send automatic reminders 24h before deadlines', UTC_TIMESTAMP(6)),
    ('email.reminder.hours.before',       '24',             'Hours before the deadline to send the automatic reminder', UTC_TIMESTAMP(6));
