import {
  AccessTimeRounded, CheckCircleRounded, EventRounded, HourglassTopRounded, PlayArrowRounded, QuizRounded,
  ScheduleRounded, TimerOffRounded, VisibilityRounded,
} from '@mui/icons-material';
import { Box, Button, Card, Chip, LinearProgress, Stack, Tab, Tabs, Typography } from '@mui/material';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { candidateApi } from '../../api/services';
import type { CandidateAssessmentState, MyAssessment } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { EmptyState, ErrorState } from '../../components/common/Feedback';
import { CardsSkeleton } from '../../components/common/Loaders';
import { PageHeader } from '../../components/common/PageHeader';
import { StatCard } from '../../components/common/StatCard';
import { StatusChip } from '../../components/common/StatusChip';
import { useAsync } from '../../hooks/useAsync';
import { formatDateTime, formatDuration, formatPercent, fromNow } from '../../utils/format';
import { ASSESSMENT_TYPE_LABEL, STATE_LABEL } from '../../utils/labels';

const FILTERS: { label: string; states: CandidateAssessmentState[] | null }[] = [
  { label: 'All', states: null },
  { label: 'To do', states: ['AVAILABLE', 'IN_PROGRESS'] },
  { label: 'Upcoming', states: ['UPCOMING'] },
  { label: 'Completed', states: ['COMPLETED'] },
  { label: 'Expired', states: ['EXPIRED'] },
];

export default function CandidateDashboardPage() {
  const { user } = useAuth();
  const [filter, setFilter] = useState(0);
  const { data, loading, error, reload } = useAsync(() => candidateApi.my(), []);
  const list = data ?? [];
  const count = (...states: CandidateAssessmentState[]) => list.filter((a) => states.includes(a.state)).length;
  const visible = FILTERS[filter].states ? list.filter((a) => FILTERS[filter].states!.includes(a.state)) : list;
  const order: CandidateAssessmentState[] = ['IN_PROGRESS', 'AVAILABLE', 'UPCOMING', 'COMPLETED', 'EXPIRED'];
  const sorted = [...visible].sort((a, b) => order.indexOf(a.state) - order.indexOf(b.state));

  return (
    <>
      <PageHeader overline="My assessments" title={`Welcome, ${user?.fullName.split(' ')[0] ?? ''}`}
        subtitle="Everything you've been invited to, with deadlines shown in your local time." />
      {Boolean(error) && <ErrorState error={error} onRetry={reload} />}
      {loading && !data ? <CardsSkeleton count={4} /> : (
        <Stack spacing={3}>
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: 'repeat(2, 1fr)', md: 'repeat(5, 1fr)' } }}>
            <StatCard label="Assigned" value={list.length} icon={<QuizRounded />} />
            <StatCard label="Available" value={count('AVAILABLE')} icon={<PlayArrowRounded />} tone="success" />
            <StatCard label="In progress" value={count('IN_PROGRESS')} icon={<HourglassTopRounded />} tone="warning" />
            <StatCard label="Upcoming" value={count('UPCOMING')} icon={<ScheduleRounded />} tone="info" />
            <StatCard label="Completed" value={count('COMPLETED')} icon={<CheckCircleRounded />} tone="secondary" />
          </Box>
          <Tabs value={filter} onChange={(_, v) => setFilter(v)} variant="scrollable">
            {FILTERS.map((f) => <Tab key={f.label} label={f.label} />)}
          </Tabs>
          {sorted.length === 0 ? (
            <EmptyState title="Nothing here yet" description="When someone assigns you an assessment, it will appear here and you'll get an e-mail." />
          ) : (
            <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)', xl: 'repeat(3, 1fr)' } }}>
              {sorted.map((a) => <AssessmentCard key={a.assigneeId} a={a} />)}
            </Box>
          )}
        </Stack>
      )}
    </>
  );
}

function AssessmentCard({ a }: { a: MyAssessment }) {
  const navigate = useNavigate();
  const urgent = a.deadline && new Date(a.deadline).getTime() - Date.now() < 24 * 3600 * 1000 && (a.state === 'AVAILABLE' || a.state === 'IN_PROGRESS');
  const action = (() => {
    switch (a.state) {
      case 'IN_PROGRESS':
        return <Button variant="contained" fullWidth startIcon={<PlayArrowRounded />} onClick={() => navigate(`/candidate/attempts/${a.activeAttemptId}/take`)}>Resume</Button>;
      case 'AVAILABLE':
        return <Button variant="contained" fullWidth startIcon={<PlayArrowRounded />} onClick={() => navigate(`/candidate/assessments/${a.assignmentId}`)}>{a.attemptsUsed > 0 ? 'Start another attempt' : 'View & start'}</Button>;
      case 'COMPLETED':
        return a.latestAttemptId
          ? <Button variant="outlined" fullWidth startIcon={<VisibilityRounded />} onClick={() => navigate(`/candidate/attempts/${a.latestAttemptId}/result`)}>{a.resultReleased ? 'View result' : 'View submission'}</Button>
          : null;
      case 'UPCOMING':
        return <Button variant="outlined" fullWidth onClick={() => navigate(`/candidate/assessments/${a.assignmentId}`)}>View details</Button>;
      default:
        return <Button variant="outlined" fullWidth disabled startIcon={<TimerOffRounded />}>Deadline passed</Button>;
    }
  })();

  return (
    <Card sx={{ p: 2.5, display: 'flex', flexDirection: 'column', gap: 2, transition: 'transform .2s ease', '&:hover': { transform: 'translateY(-3px)' } }}>
      <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={1} sx={{ position: 'relative' }}>
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="overline" color="secondary.main">{ASSESSMENT_TYPE_LABEL[a.assessmentType]}{a.skillCategory ? ` · ${a.skillCategory}` : ''}</Typography>
          <Typography variant="h6" sx={{ lineHeight: 1.3 }}>{a.title}</Typography>
        </Box>
        <StatusChip status={a.state} label={STATE_LABEL[a.state]} />
      </Stack>
      {a.description && <Typography variant="body2" color="text.secondary" sx={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden', position: 'relative' }}>{a.description}</Typography>}
      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ position: 'relative' }}>
        <Chip size="small" icon={<AccessTimeRounded />} label={formatDuration(a.durationMinutes)} />
        <Chip size="small" icon={<QuizRounded />} label={`${a.questionCount} questions`} />
        <Chip size="small" variant="outlined" label={`Attempts ${a.attemptsUsed}/${a.maxAttempts}`} />
      </Stack>
      <Stack spacing={0.5} sx={{ position: 'relative' }}>
        {a.state === 'UPCOMING' && a.startAt && <Typography variant="body2"><EventRounded sx={{ fontSize: 16, verticalAlign: 'text-bottom', mr: 0.5 }} />Opens {formatDateTime(a.startAt)} ({fromNow(a.startAt)})</Typography>}
        {a.deadline && (
          <Typography variant="body2" color={urgent ? 'warning.main' : 'text.secondary'} fontWeight={urgent ? 700 : 400}>
            Due {formatDateTime(a.deadline)} ({fromNow(a.deadline)})
          </Typography>
        )}
        {a.state === 'COMPLETED' && a.resultReleased && a.percentage !== undefined && a.percentage !== null && (
          <Box>
            <Stack direction="row" justifyContent="space-between"><Typography variant="body2">Score</Typography><Typography fontWeight={800}>{formatPercent(a.percentage)}</Typography></Stack>
            <LinearProgress variant="determinate" value={Number(a.percentage)} sx={{ mt: 0.5 }} />
          </Box>
        )}
        {a.state === 'COMPLETED' && !a.resultReleased && <Typography variant="body2" color="text.secondary">Submitted {fromNow(a.latestSubmittedAt)} · result pending</Typography>}
      </Stack>
      <Box sx={{ mt: 'auto', position: 'relative' }}>{action}</Box>
    </Card>
  );
}
