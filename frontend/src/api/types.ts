// Mirrors the backend DTOs (com.assesshub.dto.*). All instants are ISO-8601 UTC strings.

export type RoleName = 'SUPER_ADMIN' | 'ADMIN' | 'RECRUITER' | 'CANDIDATE';
export type UserStatus = 'ACTIVE' | 'INVITED' | 'DISABLED';
export type QuestionType = 'MCQ_SINGLE' | 'MCQ_MULTI' | 'TRUE_FALSE' | 'SHORT_ANSWER' | 'LONG_ANSWER' | 'CODING' | 'FILE_UPLOAD';
export type Difficulty = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'EXPERT';
export type QuestionVisibility = 'PRIVATE' | 'SHARED';
export type AssessmentType = 'QUIZ' | 'CODING' | 'DESCRIPTIVE' | 'MIXED' | 'TAKE_HOME';
export type AssignmentStatus = 'DRAFT' | 'PUBLISHED' | 'ACTIVE' | 'COMPLETED' | 'ARCHIVED';
export type ReleasePolicy = 'AFTER_GRADING' | 'MANUAL';
export type ViolationAction = 'WARN_ONLY' | 'FLAG_FOR_REVIEW' | 'AUTO_SUBMIT';
export type AssigneeStatus = 'ASSIGNED' | 'IN_PROGRESS' | 'COMPLETED' | 'EXPIRED' | 'REVOKED';
export type InvitationStatus = 'PENDING' | 'ACCEPTED' | 'REVOKED' | 'EXPIRED';
export type AttemptStatus = 'IN_PROGRESS' | 'SUBMITTED' | 'AUTO_SUBMITTED' | 'TERMINATED';
export type AnswerGradingStatus = 'PENDING' | 'AUTO_GRADED' | 'MANUALLY_GRADED';
export type ResultGradingStatus = 'PENDING_REVIEW' | 'GRADED';
export type CandidateAssessmentState = 'UPCOMING' | 'AVAILABLE' | 'IN_PROGRESS' | 'COMPLETED' | 'EXPIRED';
export type ActivityEventType =
  | 'TAB_HIDDEN' | 'TAB_VISIBLE' | 'WINDOW_BLUR' | 'WINDOW_FOCUS' | 'FULLSCREEN_EXIT' | 'COPY_ATTEMPT' | 'PASTE_ATTEMPT'
  | 'WARNING_SHOWN' | 'STARTED' | 'RESUMED' | 'SUBMITTED' | 'AUTO_SUBMITTED' | 'TERMINATED' | 'FLAGGED';

export interface ApiEnvelope<T> {
  success: boolean;
  data: T;
  message?: string;
  timestamp: string;
}

export interface ApiErrorBody {
  success: false;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  fieldErrors?: Record<string, string>;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface PageQuery {
  page?: number;
  size?: number;
  sort?: string;
}

// ------------------------------------------------------------- auth & users
export interface User {
  id: number;
  email: string;
  fullName: string;
  department?: string;
  jobTitle?: string;
  status: UserStatus;
  emailVerified: boolean;
  roles: RoleName[];
  hasPassword: boolean;
  lastLoginAt?: string;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}

export interface TokenCheck {
  valid: boolean;
  purpose?: 'PASSWORD_RESET' | 'ACCOUNT_SETUP';
  email?: string;
}

export interface UserPayload {
  email?: string;
  fullName: string;
  roles: RoleName[];
  status?: UserStatus;
  department?: string;
  jobTitle?: string;
  sendSetupEmail?: boolean;
}

// ------------------------------------------------------------- questions
export interface OptionDto {
  id: number;
  text: string;
  correct?: boolean | null;
  displayOrder: number;
}

export interface Question {
  id: number;
  type: QuestionType;
  title: string;
  body: string;
  skillCategory?: string;
  difficulty: Difficulty;
  defaultMarks: number;
  visibility: QuestionVisibility;
  codingLanguage?: string;
  starterCode?: string;
  referenceAnswer?: string;
  evaluationGuidelines?: string;
  acceptedAnswers: string[];
  caseSensitive: boolean;
  allowedFileTypes?: string;
  maxFileSizeMb?: number;
  tags?: string;
  archived: boolean;
  ownerId: number;
  ownerName: string;
  editable: boolean;
  options: OptionDto[];
  createdAt: string;
  updatedAt: string;
}

export interface QuestionPayload {
  type: QuestionType;
  title: string;
  body: string;
  skillCategory?: string;
  difficulty: Difficulty;
  defaultMarks: number;
  visibility?: QuestionVisibility;
  codingLanguage?: string;
  starterCode?: string;
  referenceAnswer?: string;
  evaluationGuidelines?: string;
  acceptedAnswers?: string[];
  caseSensitive?: boolean;
  allowedFileTypes?: string;
  maxFileSizeMb?: number;
  tags?: string;
  options?: { text: string; correct: boolean }[];
}

// ------------------------------------------------------------- assignments
export interface Attachment {
  id: number;
  originalName: string;
  contentType?: string;
  sizeBytes: number;
  createdAt: string;
}

export interface AssignmentQuestion {
  id: number;
  questionId: number;
  displayOrder: number;
  marks: number;
  question: Question;
}

export interface Assignment {
  id: number;
  title: string;
  description?: string;
  instructions?: string;
  evaluationCriteria?: string;
  skillCategory?: string;
  difficulty: Difficulty;
  assessmentType: AssessmentType;
  totalMarks: number;
  passingMarks: number;
  startAt?: string;
  endAt?: string;
  durationMinutes: number;
  maxAttempts: number;
  gracePeriodSeconds: number;
  shuffleQuestions: boolean;
  shuffleOptions: boolean;
  status: AssignmentStatus;
  resultsReleasePolicy: ReleasePolicy;
  showCorrectAnswers: boolean;
  resultsReleased: boolean;
  resultsReleasedAt?: string;
  monitoringEnabled: boolean;
  warningThreshold: number;
  maxViolations: number;
  violationAction: ViolationAction;
  publishedAt?: string;
  ownerId: number;
  ownerName: string;
  questionCount: number;
  assigneeCount: number;
  completedCount: number;
  attemptCount: number;
  hasAttempts: boolean;
  questions?: AssignmentQuestion[];
  attachments?: Attachment[];
  createdAt: string;
  updatedAt: string;
}

export interface AssignmentPayload {
  title: string;
  description?: string;
  instructions?: string;
  evaluationCriteria?: string;
  skillCategory?: string;
  difficulty: Difficulty;
  assessmentType: AssessmentType;
  passingMarks: number;
  startAt?: string | null;
  endAt?: string | null;
  durationMinutes: number;
  maxAttempts: number;
  gracePeriodSeconds?: number;
  shuffleQuestions: boolean;
  shuffleOptions: boolean;
  resultsReleasePolicy?: ReleasePolicy;
  showCorrectAnswers: boolean;
  monitoringEnabled?: boolean;
  warningThreshold?: number;
  maxViolations?: number;
  violationAction?: ViolationAction;
  questions?: { questionId: number; marks?: number }[];
}

export interface InviteResult {
  invited: number;
  reinvited: number;
  skipped: number;
  messages: string[];
}

export interface Assignee {
  id: number;
  userId: number;
  email: string;
  fullName: string;
  status: AssigneeStatus;
  dueAt?: string;
  effectiveDeadline?: string;
  attemptsUsed: number;
  maxAttempts: number;
  assignedAt: string;
  completedAt?: string;
  invitationStatus?: InvitationStatus;
  invitationSentAt?: string;
  invitationExpiresAt?: string;
  reminderCount: number;
  lastReminderAt?: string;
  latestAttemptId?: number;
  latestAttemptStatus?: AttemptStatus;
  latestScore?: number;
  latestPercentage?: number;
  passed?: boolean | null;
  gradingStatus?: ResultGradingStatus;
  tabSwitchCount: number;
  flagged: boolean;
}

// ------------------------------------------------------------- candidate
export interface MyAssessment {
  assignmentId: number;
  assigneeId: number;
  title: string;
  description?: string;
  skillCategory?: string;
  difficulty: Difficulty;
  assessmentType: AssessmentType;
  durationMinutes: number;
  startAt?: string;
  deadline?: string;
  maxAttempts: number;
  attemptsUsed: number;
  state: CandidateAssessmentState;
  questionCount: number;
  totalMarks: number;
  activeAttemptId?: number;
  latestAttemptId?: number;
  latestAttemptStatus?: AttemptStatus;
  latestSubmittedAt?: string;
  resultReleased: boolean;
  score?: number;
  percentage?: number;
  passed?: boolean | null;
  assignedAt: string;
}

export interface AssessmentInstructions {
  summary: MyAssessment;
  instructions?: string;
  evaluationCriteria?: string;
  passingMarks: number;
  monitoringEnabled: boolean;
  warningThreshold: number;
  maxViolations: number;
  violationAction: ViolationAction;
  gracePeriodSeconds: number;
  attachments: Attachment[];
  canStart: boolean;
  unavailableReason?: string;
  serverTime: string;
}

// ------------------------------------------------------------- attempts
export interface CandidateOption {
  id: number;
  text: string;
}

export interface AttemptQuestion {
  questionId: number;
  index: number;
  type: QuestionType;
  title: string;
  body: string;
  marks: number;
  codingLanguage?: string;
  starterCode?: string;
  allowedFileTypes?: string;
  maxFileSizeMb?: number;
  options?: CandidateOption[] | null;
}

export interface SavedAnswer {
  questionId: number;
  selectedOptionIds: number[];
  textAnswer?: string | null;
  codeAnswer?: string | null;
  codeLanguage?: string | null;
  fileName?: string | null;
  fileSize?: number | null;
  savedAt?: string | null;
}

export interface AttemptView {
  attemptId: number;
  assignmentId: number;
  title: string;
  instructions?: string;
  status: AttemptStatus;
  attemptNumber: number;
  startedAt: string;
  deadlineAt: string;
  serverTime: string;
  remainingSeconds: number;
  durationMinutes: number;
  gracePeriodSeconds: number;
  monitoringEnabled: boolean;
  warningThreshold: number;
  maxViolations: number;
  violationAction: ViolationAction;
  tabSwitchCount: number;
  focusLossCount: number;
  violationCount: number;
  lastSavedAt?: string;
  submittedAt?: string;
  submissionReason?: string;
  questions: AttemptQuestion[];
  answers: SavedAnswer[];
}

export interface AnswerInput {
  questionId: number;
  selectedOptionIds?: number[];
  textAnswer?: string | null;
  codeAnswer?: string | null;
  codeLanguage?: string | null;
}

export interface SaveAnswersResponse {
  savedAt: string;
  remainingSeconds: number;
  savedCount: number;
  status: AttemptStatus;
}

export interface ActivityEventInput {
  type: ActivityEventType;
  occurredAt: string;
  durationMs?: number;
  details?: string;
}

export interface ActivityResponse {
  tabSwitchCount: number;
  focusLossCount: number;
  violationCount: number;
  warningThreshold: number;
  maxViolations: number;
  violationAction: ViolationAction;
  flagged: boolean;
  status: AttemptStatus;
  newViolation: boolean;
  message?: string;
}

export interface SubmitResponse {
  attemptId: number;
  status: AttemptStatus;
  submittedAt: string;
  message: string;
  resultReleased: boolean;
}

export interface ActivityLog {
  id: number;
  eventType: ActivityEventType;
  clientTimestamp?: string;
  serverTimestamp: string;
  durationMs?: number;
  details?: string;
  countedAsViolation: boolean;
}

// ------------------------------------------------------------- results
export interface Result {
  id: number;
  attemptId: number;
  attemptNumber: number;
  attemptStatus: AttemptStatus;
  assignmentId: number;
  assignmentTitle: string;
  userId: number;
  candidateName: string;
  candidateEmail: string;
  autoScore: number;
  manualScore: number;
  totalScore: number;
  maxScore: number;
  percentage: number;
  passed?: boolean | null;
  gradingStatus: ResultGradingStatus;
  released: boolean;
  releasedAt?: string;
  feedback?: string;
  gradedByName?: string;
  gradedAt?: string;
  tabSwitchCount: number;
  focusLossCount: number;
  violationCount: number;
  flagged: boolean;
  activitySummary?: string;
  startedAt: string;
  submittedAt?: string;
}

export interface ReviewOption {
  id: number;
  text: string;
  correct: boolean;
  selected: boolean;
}

export interface ReviewAnswer {
  answerId: number;
  questionId: number;
  index: number;
  type: QuestionType;
  title: string;
  body: string;
  maxMarks: number;
  marksAwarded?: number | null;
  correct?: boolean | null;
  gradingStatus: AnswerGradingStatus;
  evaluatorComment?: string;
  gradedByName?: string;
  gradedAt?: string;
  options?: ReviewOption[] | null;
  textAnswer?: string;
  codeAnswer?: string;
  codeLanguage?: string;
  fileName?: string;
  fileSize?: number;
  referenceAnswer?: string;
  evaluationGuidelines?: string;
  acceptedAnswers: string[];
  savedAt?: string;
}

export interface AttemptReview {
  attemptId: number;
  assignmentId: number;
  assignmentTitle: string;
  passingMarks: number;
  userId: number;
  candidateName: string;
  candidateEmail: string;
  attemptNumber: number;
  status: AttemptStatus;
  startedAt: string;
  deadlineAt: string;
  submittedAt?: string;
  submissionReason?: string;
  ipAddress?: string;
  userAgent?: string;
  tabSwitchCount: number;
  focusLossCount: number;
  violationCount: number;
  totalAwayMs: number;
  flagged: boolean;
  flagReason?: string;
  result?: Result | null;
  answers: ReviewAnswer[];
}

export interface CandidateResultOption {
  id: number;
  text: string;
  selected: boolean;
  correct?: boolean | null;
}

export interface CandidateQuestionResult {
  questionId: number;
  index: number;
  type: QuestionType;
  title: string;
  body: string;
  maxMarks: number;
  marksAwarded?: number | null;
  correct?: boolean | null;
  evaluatorComment?: string;
  options?: CandidateResultOption[] | null;
  textAnswer?: string;
  codeAnswer?: string;
  fileName?: string;
}

export interface CandidateResult {
  attemptId: number;
  assignmentId: number;
  assignmentTitle: string;
  status: AttemptStatus;
  startedAt: string;
  submittedAt?: string;
  submissionReason?: string;
  released: boolean;
  gradingStatus?: ResultGradingStatus;
  totalScore?: number;
  maxScore?: number;
  percentage?: number;
  passed?: boolean | null;
  passingMarks?: number;
  feedback?: string;
  showCorrectAnswers: boolean;
  questions: CandidateQuestionResult[];
  message?: string;
}

// ------------------------------------------------------------- monitoring, dashboard, admin
export interface MonitoringAttempt {
  attemptId: number;
  assignmentId: number;
  assignmentTitle: string;
  userId: number;
  candidateName: string;
  candidateEmail: string;
  attemptNumber: number;
  status: AttemptStatus;
  startedAt: string;
  deadlineAt: string;
  submittedAt?: string;
  remainingSeconds: number;
  tabSwitchCount: number;
  focusLossCount: number;
  violationCount: number;
  totalAwayMs: number;
  flagged: boolean;
  flagReason?: string;
  lastSavedAt?: string;
}

export interface CountItem {
  label: string;
  count: number;
}

export interface StaffDashboard {
  totalAssignments: number;
  assignmentsByStatus: Record<string, number>;
  totalInvited: number;
  pendingAssessments: number;
  inProgressAssessments: number;
  completedAssessments: number;
  expiredAssessments: number;
  averageScore: number;
  passedCount: number;
  failedCount: number;
  pendingReviewCount: number;
  flaggedAttempts: number;
  totalTabSwitches: number;
  totalFocusLosses: number;
  totalAttempts: number;
  scoreDistribution: CountItem[];
  recentAttempts: MonitoringAttempt[];
  flaggedRecent: MonitoringAttempt[];
}

export interface AuditLog {
  id: number;
  actorId?: number;
  actorEmail?: string;
  action: string;
  entityType?: string;
  entityId?: string;
  details?: string;
  ipAddress?: string;
  createdAt: string;
}

export interface EmailNotification {
  id: number;
  recipientEmail: string;
  template: string;
  subject: string;
  status: string;
  errorMessage?: string;
  relatedType?: string;
  relatedId?: number;
  sentAt?: string;
  createdAt: string;
}

export interface Setting {
  key: string;
  value: string;
  description?: string;
  updatedAt?: string;
}

export interface InvitationInfo {
  assignmentId: number;
  assignmentTitle: string;
  description?: string;
  instructions?: string;
  durationMinutes: number;
  startAt?: string;
  deadline?: string;
  questionCount: number;
  candidateName: string;
  candidateEmail: string;
  orgName: string;
  requiresLogin: boolean;
  hasPassword: boolean;
}

export interface AcceptInvitationResponse {
  requiresLogin: boolean;
  auth?: AuthResponse | null;
  assignmentId: number;
  message?: string;
}
