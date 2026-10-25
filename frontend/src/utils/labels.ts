import type {
  AssessmentType, AssigneeStatus, AssignmentStatus, AttemptStatus, CandidateAssessmentState, Difficulty,
  QuestionType, RoleName, ViolationAction,
} from '../api/types';

export type ChipColor = 'default' | 'primary' | 'secondary' | 'success' | 'warning' | 'error' | 'info';

export const QUESTION_TYPE_LABEL: Record<QuestionType, string> = {
  MCQ_SINGLE: 'Multiple choice',
  MCQ_MULTI: 'Multiple select',
  TRUE_FALSE: 'True / False',
  SHORT_ANSWER: 'Short answer',
  LONG_ANSWER: 'Long answer',
  CODING: 'Coding',
  FILE_UPLOAD: 'File upload',
};

export const DIFFICULTY_LABEL: Record<Difficulty, string> = {
  BEGINNER: 'Beginner',
  INTERMEDIATE: 'Intermediate',
  ADVANCED: 'Advanced',
  EXPERT: 'Expert',
};

export const ASSESSMENT_TYPE_LABEL: Record<AssessmentType, string> = {
  QUIZ: 'Quiz',
  CODING: 'Coding challenge',
  DESCRIPTIVE: 'Descriptive',
  MIXED: 'Mixed',
  TAKE_HOME: 'Take-home',
};

export const ROLE_LABEL: Record<RoleName, string> = {
  SUPER_ADMIN: 'Super Admin',
  ADMIN: 'Admin',
  RECRUITER: 'Recruiter',
  CANDIDATE: 'Candidate',
};

export const VIOLATION_ACTION_LABEL: Record<ViolationAction, string> = {
  WARN_ONLY: 'Warn only',
  FLAG_FOR_REVIEW: 'Flag for human review',
  AUTO_SUBMIT: 'Submit automatically',
};

export const STATUS_COLOR: Record<string, ChipColor> = {
  // assignment
  DRAFT: 'default',
  PUBLISHED: 'info',
  ACTIVE: 'success',
  COMPLETED: 'secondary',
  ARCHIVED: 'default',
  // assignee / candidate state
  ASSIGNED: 'info',
  IN_PROGRESS: 'warning',
  EXPIRED: 'error',
  REVOKED: 'error',
  UPCOMING: 'info',
  AVAILABLE: 'success',
  // attempt
  SUBMITTED: 'success',
  AUTO_SUBMITTED: 'warning',
  TERMINATED: 'error',
  // grading / invitation / user
  PENDING_REVIEW: 'warning',
  GRADED: 'success',
  PENDING: 'info',
  ACCEPTED: 'success',
  INVITED: 'info',
  DISABLED: 'error',
  SENT: 'success',
  FAILED: 'error',
  SKIPPED: 'default',
};

export function humanize(value?: string | null): string {
  if (!value) return '—';
  return value
    .toLowerCase()
    .split('_')
    .map((w, i) => (i === 0 ? w.charAt(0).toUpperCase() + w.slice(1) : w))
    .join(' ');
}

export const ATTEMPT_STATUS_LABEL: Record<AttemptStatus, string> = {
  IN_PROGRESS: 'In progress',
  SUBMITTED: 'Submitted',
  AUTO_SUBMITTED: 'Auto-submitted',
  TERMINATED: 'Ended',
};

export const STATE_LABEL: Record<CandidateAssessmentState, string> = {
  UPCOMING: 'Upcoming',
  AVAILABLE: 'Available',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
  EXPIRED: 'Expired',
};

export const ASSIGNMENT_STATUS_LABEL: Record<AssignmentStatus, string> = {
  DRAFT: 'Draft',
  PUBLISHED: 'Published',
  ACTIVE: 'Active',
  COMPLETED: 'Completed',
  ARCHIVED: 'Archived',
};

export const ASSIGNEE_STATUS_LABEL: Record<AssigneeStatus, string> = {
  ASSIGNED: 'Assigned',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
  EXPIRED: 'Expired',
  REVOKED: 'Revoked',
};

export const CODING_LANGUAGES = [
  'javascript', 'typescript', 'python', 'java', 'csharp', 'go', 'kotlin', 'php', 'ruby', 'sql', 'cpp', 'rust', 'swift', 'other',
];
