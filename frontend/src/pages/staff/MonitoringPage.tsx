import { FlagRounded, InfoOutlined, RefreshRounded, SearchRounded } from '@mui/icons-material';
import {
  Alert, Box, FormControlLabel, IconButton, InputAdornment, LinearProgress, MenuItem, Stack, Switch, TextField, Tooltip, Typography,
} from '@mui/material';
import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { monitoringApi } from '../../api/services';
import type { AttemptStatus, MonitoringAttempt } from '../../api/types';
import { DataTable, sortParam, type Column, type SortState } from '../../components/common/DataTable';
import { ErrorState } from '../../components/common/Feedback';
import { GlassCard } from '../../components/common/GlassCard';
import { PageHeader } from '../../components/common/PageHeader';
import { StatusChip } from '../../components/common/StatusChip';
import { useAsync, useDebounced } from '../../hooks/useAsync';
import { formatClock, formatDateTime, formatMs } from '../../utils/format';
import { ATTEMPT_STATUS_LABEL } from '../../utils/labels';

export default function MonitoringPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<AttemptStatus | ''>(params.get('flagged') ? '' : 'IN_PROGRESS');
  const [flagged, setFlagged] = useState(params.get('flagged') === 'true');
  const [auto, setAuto] = useState(true);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sort, setSort] = useState<SortState>({ field: 'startedAt', direction: 'desc' });
  const [, setTick] = useState(0);
  const q = useDebounced(search);
  const { data, loading, error, reload } = useAsync(
    () => monitoringApi.attempts({ search: q, status, flagged: flagged ? 'true' : undefined, page, size, sort: sortParam(sort) }),
    [q, status, flagged, page, size, sort.field, sort.direction],
  );
  const [loadedAt, setLoadedAt] = useState(Date.now());
  useEffect(() => setLoadedAt(Date.now()), [data]);

  // Auto refresh every 15 s; local countdown ticks every second between refreshes.
  useEffect(() => {
    if (!auto) return;
    const refresh = window.setInterval(reload, 15000);
    const tick = window.setInterval(() => setTick((t) => t + 1), 1000);
    return () => { window.clearInterval(refresh); window.clearInterval(tick); };
  }, [auto, reload]);

  const liveRemaining = (r: MonitoringAttempt) => Math.max(0, r.remainingSeconds - Math.floor((Date.now() - loadedAt) / 1000));

  const columns: Column<MonitoringAttempt>[] = [
    {
      key: 'candidate', label: 'Candidate', render: (r) => (
        <Box>
          <Typography fontWeight={650}>{r.candidateName}</Typography>
          <Typography variant="caption" color="text.secondary">{r.candidateEmail}</Typography>
        </Box>
      ),
    },
    { key: 'assessment', label: 'Assessment', hideBelow: 'md', render: (r) => <Typography variant="body2">{r.assignmentTitle} <Typography component="span" variant="caption" color="text.secondary">· #{r.attemptNumber}</Typography></Typography> },
    { key: 'status', label: 'Status', sortField: 'status', render: (r) => <StatusChip status={r.status} label={ATTEMPT_STATUS_LABEL[r.status]} /> },
    {
      key: 'time', label: 'Time left', render: (r) => {
        if (r.status !== 'IN_PROGRESS') return <Typography variant="body2" color="text.secondary">{formatDateTime(r.submittedAt)}</Typography>;
        const left = liveRemaining(r);
        return (
          <Box sx={{ minWidth: 110 }}>
            <Typography fontWeight={700} sx={{ fontVariantNumeric: 'tabular-nums' }} color={left < 300 ? 'warning.main' : 'text.primary'}>{formatClock(left)}</Typography>
            <LinearProgress variant="determinate" value={Math.min(100, (left / Math.max(1, (new Date(r.deadlineAt).getTime() - new Date(r.startedAt).getTime()) / 1000)) * 100)} sx={{ height: 4 }} />
          </Box>
        );
      },
    },
    { key: 'tabs', label: 'Tab switches', align: 'center', sortField: 'tabSwitchCount', render: (r) => <Count value={r.tabSwitchCount} /> },
    { key: 'focus', label: 'Focus losses', align: 'center', hideBelow: 'sm', sortField: 'focusLossCount', render: (r) => <Count value={r.focusLossCount} /> },
    { key: 'away', label: 'Time away', align: 'center', hideBelow: 'lg', render: (r) => formatMs(r.totalAwayMs) },
    {
      key: 'flag', label: 'Review', align: 'center', render: (r) => r.flagged
        ? <Tooltip title={r.flagReason ?? 'Flagged for review'}><FlagRounded color="error" /></Tooltip>
        : <Typography variant="caption" color="text.secondary">—</Typography>,
    },
    { key: 'saved', label: 'Last autosave', hideBelow: 'lg', render: (r) => (r.lastSavedAt ? formatDateTime(r.lastSavedAt) : '—') },
  ];

  return (
    <>
      <PageHeader title="Live monitoring" subtitle="Running attempts, server-side countdowns and focus activity."
        actions={
          <>
            <FormControlLabel control={<Switch checked={auto} onChange={(e) => setAuto(e.target.checked)} />} label="Auto-refresh" />
            <Tooltip title="Refresh now"><IconButton onClick={reload}><RefreshRounded /></IconButton></Tooltip>
          </>
        } />
      <Alert severity="info" icon={<InfoOutlined />} sx={{ mb: 2.5 }}>
        Tab-switch and focus counts come from the candidate's browser. They show when the assessment tab was hidden or the window lost focus,
        but cannot detect other devices, other browsers or incognito windows, and can be triggered by OS pop-ups. Use them to prioritise
        human review — not as automatic proof of misconduct.
      </Alert>
      <GlassCard noPadding>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ p: 2.5 }} alignItems={{ md: 'center' }}>
          <TextField size="small" placeholder="Search candidate or assessment…" value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }}
            sx={{ flex: 1 }} InputProps={{ startAdornment: <InputAdornment position="start"><SearchRounded /></InputAdornment> }} />
          <TextField select size="small" label="Status" value={status} onChange={(e) => { setStatus(e.target.value as AttemptStatus | ''); setPage(0); }} sx={{ minWidth: 170 }}>
            <MenuItem value="">All</MenuItem>
            {Object.entries(ATTEMPT_STATUS_LABEL).map(([v, l]) => <MenuItem key={v} value={v}>{l}</MenuItem>)}
          </TextField>
          <FormControlLabel control={<Switch checked={flagged} onChange={(e) => { setFlagged(e.target.checked); setPage(0); }} />} label="Flagged only" />
        </Stack>
        {Boolean(error) && <Box sx={{ px: 2.5 }}><ErrorState error={error} onRetry={reload} /></Box>}
        <DataTable columns={columns} rows={data?.content ?? []} getRowId={(r) => r.attemptId} loading={loading && !data}
          total={data?.totalElements ?? 0} page={page} size={size} onPageChange={setPage} onSizeChange={(s) => { setSize(s); setPage(0); }}
          sort={sort} onSortChange={setSort} onRowClick={(r) => navigate(`/attempts/${r.attemptId}`)}
          emptyMessage={status === 'IN_PROGRESS' ? 'No one is taking an assessment right now.' : 'No attempts match.'} />
      </GlassCard>
    </>
  );
}

function Count({ value }: { value: number }) {
  return <Typography fontWeight={700} color={value === 0 ? 'text.secondary' : value < 3 ? 'warning.main' : 'error.main'}>{value}</Typography>;
}
