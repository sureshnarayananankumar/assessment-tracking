import { downloadBlob, request, requestMessage } from './client';
import type {
  AcceptInvitationResponse, ActivityEventInput, ActivityLog, ActivityResponse, AnswerInput, Assignee, Assignment,
  AssignmentPayload, AssignmentStatus, AssessmentInstructions, Attachment, AttemptReview, AttemptStatus, AttemptView,
  AuditLog, AuthResponse, CandidateResult, Difficulty, EmailNotification, InvitationInfo, InviteResult,
  MonitoringAttempt, MyAssessment, Page, PageQuery, Question, QuestionPayload, QuestionType, Result,
  ResultGradingStatus, RoleName, SaveAnswersResponse, SavedAnswer, Setting, StaffDashboard, SubmitResponse, TokenCheck,
  User, UserPayload, UserStatus, AssigneeStatus,
} from './types';

type Params = Record<string, string | number | boolean | undefined | null>;

function clean(params: object): Params {
  return Object.fromEntries(Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== ''));
}

export const authApi = {
  login: (email: string, password: string) =>
    request<AuthResponse>({ method: 'POST', url: '/auth/login', data: { email, password } }),
  logout: () => requestMessage({ method: 'POST', url: '/auth/logout' }),
  me: () => request<User>({ url: '/auth/me' }),
  forgotPassword: (email: string) => requestMessage({ method: 'POST', url: '/auth/forgot-password', data: { email } }),
  validateResetToken: (token: string) => request<TokenCheck>({ url: '/auth/reset-password/validate', params: { token } }),
  resetPassword: (token: string, newPassword: string) =>
    requestMessage({ method: 'POST', url: '/auth/reset-password', data: { token, newPassword } }),
  changePassword: (currentPassword: string, newPassword: string) =>
    request<AuthResponse>({ method: 'PUT', url: '/auth/me/password', data: { currentPassword, newPassword } }),
};

export const invitationApi = {
  info: (token: string) => request<InvitationInfo>({ url: `/invitations/${encodeURIComponent(token)}` }),
  accept: (token: string, password?: string) =>
    request<AcceptInvitationResponse>({
      method: 'POST',
      url: `/invitations/${encodeURIComponent(token)}/accept`,
      data: password ? { password } : {},
    }),
};

export const settingsApi = {
  publicSettings: () => request<{ orgName: string }>({ url: '/settings/public' }),
  all: () => request<Setting[]>({ url: '/settings' }),
  update: (values: Record<string, string>) => request<Setting[]>({ method: 'PUT', url: '/settings', data: { values } }),
};

export const userApi = {
  list: (q: PageQuery & { search?: string; role?: RoleName | ''; status?: UserStatus | '' }) =>
    request<Page<User>>({ url: '/users', params: clean(q) }),
  create: (payload: UserPayload) => request<User>({ method: 'POST', url: '/users', data: payload }),
  update: (id: number, payload: UserPayload) => request<User>({ method: 'PUT', url: `/users/${id}`, data: payload }),
  deactivate: (id: number) => requestMessage({ method: 'DELETE', url: `/users/${id}` }),
  sendSetup: (id: number) => requestMessage({ method: 'POST', url: `/users/${id}/send-setup` }),
};

export const questionApi = {
  list: (q: PageQuery & { search?: string; type?: QuestionType | ''; difficulty?: Difficulty | ''; skill?: string }) =>
    request<Page<Question>>({ url: '/questions', params: clean(q) }),
  skills: () => request<string[]>({ url: '/questions/skills' }),
  get: (id: number) => request<Question>({ url: `/questions/${id}` }),
  create: (payload: QuestionPayload) => request<Question>({ method: 'POST', url: '/questions', data: payload }),
  update: (id: number, payload: QuestionPayload) => request<Question>({ method: 'PUT', url: `/questions/${id}`, data: payload }),
  remove: (id: number) => requestMessage({ method: 'DELETE', url: `/questions/${id}` }),
  duplicate: (id: number) => request<Question>({ method: 'POST', url: `/questions/${id}/duplicate` }),
};

export const assignmentApi = {
  list: (q: PageQuery & { search?: string; status?: AssignmentStatus | ''; skill?: string }) =>
    request<Page<Assignment>>({ url: '/assignments', params: clean(q) }),
  get: (id: number) => request<Assignment>({ url: `/assignments/${id}` }),
  create: (payload: AssignmentPayload) => request<Assignment>({ method: 'POST', url: '/assignments', data: payload }),
  update: (id: number, payload: AssignmentPayload) => request<Assignment>({ method: 'PUT', url: `/assignments/${id}`, data: payload }),
  remove: (id: number) => requestMessage({ method: 'DELETE', url: `/assignments/${id}` }),
  publish: (id: number) => request<Assignment>({ method: 'POST', url: `/assignments/${id}/publish` }),
  changeStatus: (id: number, status: AssignmentStatus) =>
    request<Assignment>({ method: 'POST', url: `/assignments/${id}/status`, data: { status } }),
  duplicate: (id: number) => request<Assignment>({ method: 'POST', url: `/assignments/${id}/duplicate` }),
  invite: (id: number, invitees: { email: string; fullName?: string }[], dueAt?: string | null, message?: string) =>
    request<InviteResult>({ method: 'POST', url: `/assignments/${id}/invite`, data: { invitees, dueAt: dueAt || null, message } }),
  assignees: (id: number, q: PageQuery & { search?: string; status?: AssigneeStatus | '' }) =>
    request<Page<Assignee>>({ url: `/assignments/${id}/assignees`, params: clean(q) }),
  remind: (id: number, assigneeId: number) =>
    requestMessage({ method: 'POST', url: `/assignments/${id}/assignees/${assigneeId}/remind` }),
  revoke: (id: number, assigneeId: number) =>
    requestMessage({ method: 'POST', url: `/assignments/${id}/assignees/${assigneeId}/revoke` }),
  results: (id: number, q: PageQuery & { search?: string; passed?: string; gradingStatus?: ResultGradingStatus | '' }) =>
    request<Page<Result>>({ url: `/assignments/${id}/results`, params: clean(q) }),
  exportResults: (id: number) => downloadBlob(`/assignments/${id}/results/export`, `assessment-${id}-results.csv`),
  releaseResults: (id: number) => requestMessage({ method: 'POST', url: `/assignments/${id}/release-results` }),
  uploadAttachment: (id: number, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return request<Attachment>({ method: 'POST', url: `/assignments/${id}/attachments`, data: form });
  },
  deleteAttachment: (id: number, attachmentId: number) =>
    requestMessage({ method: 'DELETE', url: `/assignments/${id}/attachments/${attachmentId}` }),
};

export const candidateApi = {
  my: () => request<MyAssessment[]>({ url: '/assessments/my' }),
  instructions: (assignmentId: number) => request<AssessmentInstructions>({ url: `/assessments/${assignmentId}` }),
  start: (assignmentId: number) => request<AttemptView>({ method: 'POST', url: `/assessments/${assignmentId}/start` }),
  downloadAttachment: (assignmentId: number, attachment: Attachment) =>
    downloadBlob(`/assessments/${assignmentId}/attachments/${attachment.id}`, attachment.originalName),
};

export const attemptApi = {
  get: (attemptId: number) => request<AttemptView>({ url: `/attempts/${attemptId}` }),
  saveAnswers: (attemptId: number, answers: AnswerInput[]) =>
    request<SaveAnswersResponse>({ method: 'PUT', url: `/attempts/${attemptId}/answers`, data: { answers } }),
  uploadFile: (attemptId: number, questionId: number, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return request<SavedAnswer>({ method: 'POST', url: `/attempts/${attemptId}/answers/${questionId}/file`, data: form });
  },
  downloadFile: (attemptId: number, questionId: number, name: string) =>
    downloadBlob(`/attempts/${attemptId}/answers/${questionId}/file`, name),
  activity: (attemptId: number, events: ActivityEventInput[]) =>
    request<ActivityResponse>({ method: 'POST', url: `/attempts/${attemptId}/activity`, data: { events } }),
  submit: (attemptId: number) => request<SubmitResponse>({ method: 'POST', url: `/attempts/${attemptId}/submit` }),
  results: (attemptId: number) => request<CandidateResult>({ url: `/attempts/${attemptId}/results` }),
  review: (attemptId: number) => request<AttemptReview>({ url: `/attempts/${attemptId}/review` }),
  activityLog: (attemptId: number) => request<ActivityLog[]>({ url: `/attempts/${attemptId}/activity` }),
  grade: (attemptId: number, grades: { answerId: number; marks: number; comment?: string }[], feedback?: string, release?: boolean) =>
    request<AttemptReview>({ method: 'POST', url: `/attempts/${attemptId}/grade`, data: { grades, feedback, release } }),
  flag: (attemptId: number, flagged: boolean, reason?: string) =>
    request<AttemptReview>({ method: 'POST', url: `/attempts/${attemptId}/flag`, data: { flagged, reason } }),
  release: (attemptId: number) => request<Result>({ method: 'POST', url: `/attempts/${attemptId}/release` }),
};

export const monitoringApi = {
  attempts: (q: PageQuery & { assignmentId?: number; status?: AttemptStatus | ''; flagged?: string; search?: string }) =>
    request<Page<MonitoringAttempt>>({ url: '/monitoring/attempts', params: clean(q) }),
};

export const reportApi = {
  results: (q: PageQuery & { assignmentId?: number; passed?: string; gradingStatus?: ResultGradingStatus | ''; flagged?: string; search?: string }) =>
    request<Page<Result>>({ url: '/reports/results', params: clean(q) }),
  exportResults: (q: { assignmentId?: number; passed?: string; gradingStatus?: string; flagged?: string; search?: string }) => {
    const qs = new URLSearchParams(clean(q) as Record<string, string>).toString();
    return downloadBlob(`/reports/results/export${qs ? `?${qs}` : ''}`, 'assessment-results.csv');
  },
};

export const dashboardApi = {
  staff: () => request<StaffDashboard>({ url: '/dashboard/staff' }),
};

export const auditApi = {
  list: (q: PageQuery & { action?: string; entityType?: string; search?: string; from?: string; to?: string }) =>
    request<Page<AuditLog>>({ url: '/audit-logs', params: clean(q) }),
  emails: (q: PageQuery) => request<Page<EmailNotification>>({ url: '/email-notifications', params: clean(q) }),
};
