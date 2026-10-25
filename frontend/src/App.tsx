import { lazy, Suspense, type ReactNode } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { HomeRedirect, RequireAuth } from './auth/RequireAuth';
import { FullPageLoader, SectionLoader } from './components/common/Loaders';
import { AppShell } from './components/layout/AppShell';

const LoginPage = lazy(() => import('./pages/auth/LoginPage'));
const ForgotPasswordPage = lazy(() => import('./pages/auth/ForgotPasswordPage'));
const ResetPasswordPage = lazy(() => import('./pages/auth/ResetPasswordPage'));
const InvitationPage = lazy(() => import('./pages/auth/InvitationPage'));

const DashboardPage = lazy(() => import('./pages/staff/DashboardPage'));
const UsersPage = lazy(() => import('./pages/staff/UsersPage'));
const AssignmentsPage = lazy(() => import('./pages/staff/AssignmentsPage'));
const AssignmentEditorPage = lazy(() => import('./pages/staff/AssignmentEditorPage'));
const AssignmentDetailPage = lazy(() => import('./pages/staff/AssignmentDetailPage'));
const InvitePage = lazy(() => import('./pages/staff/InvitePage'));
const QuestionBankPage = lazy(() => import('./pages/staff/QuestionBankPage'));
const QuestionEditorPage = lazy(() => import('./pages/staff/QuestionEditorPage'));
const MonitoringPage = lazy(() => import('./pages/staff/MonitoringPage'));
const AttemptDetailPage = lazy(() => import('./pages/staff/AttemptDetailPage'));
const EvaluationPage = lazy(() => import('./pages/staff/EvaluationPage'));
const ResultsPage = lazy(() => import('./pages/staff/ResultsPage'));
const AuditLogsPage = lazy(() => import('./pages/staff/AuditLogsPage'));
const SettingsPage = lazy(() => import('./pages/common/SettingsPage'));
const ProfilePage = lazy(() => import('./pages/common/ProfilePage'));
const StatusPage = lazy(() => import('./pages/common/StatusPage'));

const CandidateDashboardPage = lazy(() => import('./pages/candidate/CandidateDashboardPage'));
const InstructionsPage = lazy(() => import('./pages/candidate/InstructionsPage'));
const TakeAssessmentPage = lazy(() => import('./pages/candidate/TakeAssessmentPage'));
const SubmittedPage = lazy(() => import('./pages/candidate/SubmittedPage'));
const CandidateResultPage = lazy(() => import('./pages/candidate/CandidateResultPage'));

const STAFF = ['SUPER_ADMIN', 'ADMIN', 'RECRUITER'] as const;
const ADMINS = ['SUPER_ADMIN', 'ADMIN'] as const;

const page = (node: ReactNode) => <Suspense fallback={<SectionLoader rows={6} />}>{node}</Suspense>;

export default function App() {
  return (
    <Suspense fallback={<FullPageLoader />}>
      <Routes>
        {/* Public */}
        <Route path="/login" element={<LoginPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/invite/:token" element={<InvitationPage />} />
        <Route path="/" element={<HomeRedirect />} />

        <Route element={<RequireAuth />}>
          {/* Distraction-free timed assessment: rendered without the app shell */}
          <Route path="/candidate/attempts/:attemptId/take" element={<TakeAssessmentPage />} />

          <Route element={<AppShell />}>
            {/* Candidate (any signed-in user can be assigned an assessment) */}
            <Route path="/candidate" element={page(<CandidateDashboardPage />)} />
            <Route path="/candidate/assessments/:assignmentId" element={page(<InstructionsPage />)} />
            <Route path="/candidate/attempts/:attemptId/submitted" element={page(<SubmittedPage />)} />
            <Route path="/candidate/attempts/:attemptId/result" element={page(<CandidateResultPage />)} />
            <Route path="/settings" element={page(<SettingsPage />)} />
            <Route path="/profile" element={page(<ProfilePage />)} />

            {/* Staff */}
            <Route element={<RequireAuth roles={[...STAFF]} />}>
              <Route path="/dashboard" element={page(<DashboardPage />)} />
              <Route path="/assignments" element={page(<AssignmentsPage />)} />
              <Route path="/assignments/new" element={page(<AssignmentEditorPage />)} />
              <Route path="/assignments/:id" element={page(<AssignmentDetailPage />)} />
              <Route path="/assignments/:id/edit" element={page(<AssignmentEditorPage />)} />
              <Route path="/assignments/:id/invite" element={page(<InvitePage />)} />
              <Route path="/questions" element={page(<QuestionBankPage />)} />
              <Route path="/questions/new" element={page(<QuestionEditorPage />)} />
              <Route path="/questions/:id/edit" element={page(<QuestionEditorPage />)} />
              <Route path="/monitoring" element={page(<MonitoringPage />)} />
              <Route path="/attempts/:attemptId" element={page(<AttemptDetailPage />)} />
              <Route path="/attempts/:attemptId/evaluate" element={page(<EvaluationPage />)} />
              <Route path="/results" element={page(<ResultsPage />)} />
            </Route>

            {/* Administrators */}
            <Route element={<RequireAuth roles={[...ADMINS]} />}>
              <Route path="/users" element={page(<UsersPage />)} />
              <Route path="/audit-logs" element={page(<AuditLogsPage />)} />
            </Route>

            <Route path="/forbidden" element={page(<StatusPage code={403} />)} />
            <Route path="*" element={page(<StatusPage code={404} />)} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  );
}
