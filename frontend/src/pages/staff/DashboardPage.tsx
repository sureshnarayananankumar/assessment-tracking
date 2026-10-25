import {
  AddRounded, AssignmentRounded, CheckCircleRounded, FlagRounded, GroupAddRounded, HourglassTopRounded,
  PendingActionsRounded, PlayCircleRounded, RateReviewRounded, TabRounded, TimerOffRounded, TrendingUpRounded,
} from '@mui/icons-material';
import { Box, Button, Stack, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { Bar, BarChart, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useNavigate } from 'react-router-dom';
import { dashboardApi } from '../../api/services';
import type { MonitoringAttempt } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { DataTable, type Column } from '../../components/common/DataTable';
import { ErrorState } from '../../components/common/Feedback';
import { GlassCard } from '../../components/common/GlassCard';
import { CardsSkeleton } from '../../components/common/Loaders';
import { PageHeader } from '../../components/common/PageHeader';
import { StatCard } from '../../components/common/StatCard';
import { StatusChip } from '../../components/common/StatusChip';
import { useAsync } from '../../hooks/useAsync';
import { formatClock, formatDateTime, fromNow } from '../../utils/format';
import { ASSIGNMENT_STATUS_LABEL } from '../../utils/labels';

export default function DashboardPage() {
  const theme = useTheme();
  const navigate = useNavigate();
  const { user, isAdmin } = useAuth();
  const { data, loading, error, reload } = useAsync(() => dashboardApi.staff(), []);

  const attemptColumns: Column<MonitoringAttempt>[] = [
    {
      key: 'candidate', label: 'Candidate', render: (r) => (
        <Box>
          <Typography fontWeight={650}>{r.candidateName}</Typography>
          <Typography variant="caption" color="text.secondary">{r.candidateEmail}</Typography>
        </Box>
      ),
    },
    { key: 'assessment', label: 'Assessment', render: (r) => r.assignmentTitle, hideBelow: 'md' },
    { key: 'status', label: 'Status', render: (r) => <StatusChip status={r.status} /> },
    {
      key: 'time', label: 'Time', render: (r) => (r.status === 'IN_PROGRESS' ? `${formatClock(r.remainingSeconds)} left` : fromNow(r.submittedAt ?? r.startedAt)),
    },
    {
      key: 'focus', label: 'Tab switches', align: 'center', render: (r) => (
        <Stack direction="row" spacing={0.75} justifyContent="center" alignItems="center">
          <Typography fontWeight={700} color={r.tabSwitchCount > 0 ? 'warning.main' : 'text.secondary'}>{r.tabSwitchCount}</Typography>
          {r.flagged && <FlagRounded fontSize="small" color="error" />}
        </Stack>
      ),
    },
  ];

  const statusData = data
    ? Object.entries(data.assignmentsByStatus).map(([name, value]) => ({ name: ASSIGNMENT_STATUS_LABEL[name as keyof typeof ASSIGNMENT_STATUS_LABEL] ?? name, value }))
    : [];
  const passFail = data ? [
    { name: 'Passed', value: data.passedCount, color: theme.palette.success.main },
    { name: 'Failed', value: data.failedCount, color: theme.palette.error.main },
    { name: 'Pending review', value: data.pendingReviewCount, color: theme.palette.warning.main },
  ] : [];
  const passRate = data && data.passedCount + data.failedCount > 0
    ? Math.round((data.passedCount / (data.passedCount + data.failedCount)) * 100) : null;
  const tooltipStyle = {
    background: theme.glass.backgroundStrong,
    border: `1px solid ${theme.glass.border}`,
    borderRadius: 12,
    backdropFilter: 'blur(12px)',
    color: theme.palette.text.primary,
  };

  return (
    <>
      <PageHeader
        overline={isAdmin ? 'Organisation overview' : 'Your assessments'}
        title={`Hello, ${user?.fullName.split(' ')[0] ?? ''}`}
        subtitle="Here's what is happening across your assessments today."
        actions={
          <>
            <Button variant="outlined" startIcon={<PlayCircleRounded />} onClick={() => navigate('/monitoring')}>Live monitoring</Button>
            <Button variant="contained" startIcon={<AddRounded />} onClick={() => navigate('/assignments/new')}>New assessment</Button>
          </>
        }
      />
      {Boolean(error) && <ErrorState error={error} onRetry={reload} />}
      {loading && !data ? (
        <CardsSkeleton count={4} />
      ) : data ? (
        <Stack spacing={3}>
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(4, 1fr)' } }}>
            <StatCard label="Assessments created" value={data.totalAssignments} icon={<AssignmentRounded />} onClick={() => navigate('/assignments')} />
            <StatCard label="People invited" value={data.totalInvited} icon={<GroupAddRounded />} tone="info" />
            <StatCard label="Pending (not started)" value={data.pendingAssessments} icon={<PendingActionsRounded />} tone="secondary" />
            <StatCard label="In progress now" value={data.inProgressAssessments} icon={<HourglassTopRounded />} tone="warning" onClick={() => navigate('/monitoring')} />
            <StatCard label="Completed" value={data.completedAssessments} icon={<CheckCircleRounded />} tone="success" />
            <StatCard label="Expired" value={data.expiredAssessments} icon={<TimerOffRounded />} tone="error" />
            <StatCard label="Average score" value={`${data.averageScore}%`} icon={<TrendingUpRounded />} tone="info" hint={passRate !== null ? `${passRate}% pass rate` : 'No graded results yet'} />
            <StatCard label="Awaiting review" value={data.pendingReviewCount} icon={<RateReviewRounded />} tone="warning" onClick={() => navigate('/results?gradingStatus=PENDING_REVIEW')} />
          </Box>

          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', lg: '1.4fr 1fr 1fr' } }}>
            <GlassCard title="Score distribution" subtitle="Graded attempts by percentage">
              <Box sx={{ height: 240 }}>
                <ResponsiveContainer>
                  <BarChart data={data.scoreDistribution} margin={{ top: 8, right: 8, left: -18, bottom: 0 }}>
                    <defs>
                      <linearGradient id="barGrad" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor={theme.palette.secondary.main} />
                        <stop offset="100%" stopColor={theme.palette.primary.main} />
                      </linearGradient>
                    </defs>
                    <XAxis dataKey="label" tick={{ fill: theme.palette.text.secondary, fontSize: 12 }} axisLine={false} tickLine={false} />
                    <YAxis allowDecimals={false} tick={{ fill: theme.palette.text.secondary, fontSize: 12 }} axisLine={false} tickLine={false} />
                    <Tooltip cursor={{ fill: alpha(theme.palette.primary.main, 0.08) }} contentStyle={tooltipStyle} />
                    <Bar dataKey="count" name="Attempts" fill="url(#barGrad)" radius={[8, 8, 2, 2]} />
                  </BarChart>
                </ResponsiveContainer>
              </Box>
            </GlassCard>
            <GlassCard title="Pass / fail" subtitle={`${data.totalAttempts} attempts in total`}>
              <Box sx={{ height: 240 }}>
                <ResponsiveContainer>
                  <PieChart>
                    <Pie data={passFail} dataKey="value" nameKey="name" innerRadius={58} outerRadius={88} paddingAngle={3} stroke="none">
                      {passFail.map((d) => <Cell key={d.name} fill={d.color} />)}
                    </Pie>
                    <Tooltip contentStyle={tooltipStyle} />
                  </PieChart>
                </ResponsiveContainer>
              </Box>
              <Stack direction="row" spacing={2} justifyContent="center" flexWrap="wrap">
                {passFail.map((d) => (
                  <Stack key={d.name} direction="row" spacing={0.75} alignItems="center">
                    <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: d.color }} />
                    <Typography variant="caption">{d.name} ({d.value})</Typography>
                  </Stack>
                ))}
              </Stack>
            </GlassCard>
            <GlassCard title="Focus activity" subtitle="Indicators for human review, not verdicts">
              <Stack spacing={2}>
                <StatCard label="Tab switches recorded" value={data.totalTabSwitches} icon={<TabRounded />} tone="warning" />
                <StatCard label="Flagged for review" value={data.flaggedAttempts} icon={<FlagRounded />} tone="error" onClick={() => navigate('/monitoring?flagged=true')} />
                <Typography variant="caption" color="text.secondary">Assessments by status</Typography>
                <Box sx={{ height: Math.max(60, statusData.length * 28) }}>
                  <ResponsiveContainer>
                    <BarChart data={statusData} layout="vertical" margin={{ left: 0, right: 8 }}>
                      <XAxis type="number" hide allowDecimals={false} />
                      <YAxis type="category" dataKey="name" width={80} tick={{ fill: theme.palette.text.secondary, fontSize: 11 }} axisLine={false} tickLine={false} />
                      <Tooltip contentStyle={tooltipStyle} cursor={false} />
                      <Bar dataKey="value" name="Assessments" fill={theme.palette.primary.main} radius={6} barSize={10} />
                    </BarChart>
                  </ResponsiveContainer>
                </Box>
              </Stack>
            </GlassCard>
          </Box>

          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', xl: '1.6fr 1fr' } }}>
            <GlassCard title="Recent attempts" action={<Button size="small" onClick={() => navigate('/monitoring')}>View all</Button>} noPadding>
              <DataTable columns={attemptColumns} rows={data.recentAttempts} getRowId={(r) => r.attemptId}
                onRowClick={(r) => navigate(`/attempts/${r.attemptId}`)} emptyMessage="No attempts yet." dense />
            </GlassCard>
            <GlassCard title="Flagged for review" subtitle="Repeated focus loss — review before drawing conclusions" noPadding>
              <Stack sx={{ px: 2.5, pb: 2.5 }} spacing={1.25}>
                {data.flaggedRecent.length === 0 && <Typography color="text.secondary" sx={{ py: 3 }}>Nothing flagged.</Typography>}
                {data.flaggedRecent.map((r) => (
                  <Stack key={r.attemptId} direction="row" alignItems="center" spacing={1.5}
                    onClick={() => navigate(`/attempts/${r.attemptId}`)}
                    sx={{ p: 1.5, borderRadius: 3, cursor: 'pointer', border: 1, borderColor: 'divider', '&:hover': { bgcolor: 'action.hover' } }}>
                    <FlagRounded color="error" />
                    <Box sx={{ flex: 1, minWidth: 0 }}>
                      <Typography fontWeight={650} noWrap>{r.candidateName}</Typography>
                      <Typography variant="caption" color="text.secondary" noWrap component="div">{r.assignmentTitle} · {formatDateTime(r.startedAt)}</Typography>
                    </Box>
                    <StatusChip status="PENDING_REVIEW" label={`${r.violationCount} events`} />
                  </Stack>
                ))}
              </Stack>
            </GlassCard>
          </Box>
        </Stack>
      ) : null}
    </>
  );
}
