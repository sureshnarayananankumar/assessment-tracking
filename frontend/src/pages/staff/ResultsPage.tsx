import { DownloadRounded, FlagRounded, RateReviewRounded, SearchRounded } from '@mui/icons-material';
import { Box, Button, IconButton, InputAdornment, MenuItem, Stack, TextField, Tooltip, Typography } from '@mui/material';
import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { reportApi } from '../../api/services';
import type { Result, ResultGradingStatus } from '../../api/types';
import { DataTable, sortParam, type Column, type SortState } from '../../components/common/DataTable';
import { ErrorState } from '../../components/common/Feedback';
import { GlassCard } from '../../components/common/GlassCard';
import { useNotify } from '../../components/common/NotificationProvider';
import { PageHeader } from '../../components/common/PageHeader';
import { StatusChip } from '../../components/common/StatusChip';
import { useAsync, useDebounced } from '../../hooks/useAsync';
import { errorMessage } from '../../utils/errors';
import { formatDateTime, formatPercent, formatScore } from '../../utils/format';

export default function ResultsPage() {
  const navigate = useNavigate();
  const notify = useNotify();
  const [params] = useSearchParams();
  const [search, setSearch] = useState('');
  const [passed, setPassed] = useState('');
  const [gradingStatus, setGradingStatus] = useState<ResultGradingStatus | ''>((params.get('gradingStatus') as ResultGradingStatus) ?? '');
  const [flagged, setFlagged] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sort, setSort] = useState<SortState>({ field: 'createdAt', direction: 'desc' });
  const q = useDebounced(search);
  const filters = { search: q, passed, gradingStatus, flagged };
  const { data, loading, error, reload } = useAsync(
    () => reportApi.results({ ...filters, page, size, sort: sortParam(sort) }),
    [q, passed, gradingStatus, flagged, page, size, sort.field, sort.direction],
  );

  const columns: Column<Result>[] = [
    {
      key: 'candidate', label: 'Candidate', render: (r) => (
        <Box>
          <Typography fontWeight={650}>{r.candidateName}</Typography>
          <Typography variant="caption" color="text.secondary">{r.candidateEmail}</Typography>
        </Box>
      ),
    },
    { key: 'assessment', label: 'Assessment', hideBelow: 'md', render: (r) => r.assignmentTitle },
    { key: 'score', label: 'Score', sortField: 'totalScore', render: (r) => formatScore(r.totalScore, r.maxScore) },
    { key: 'pct', label: '%', align: 'center', sortField: 'percentage', render: (r) => <Typography fontWeight={700}>{formatPercent(r.percentage)}</Typography> },
    {
      key: 'outcome', label: 'Outcome', render: (r) => r.gradingStatus === 'PENDING_REVIEW'
        ? <StatusChip status="PENDING_REVIEW" label="Needs review" />
        : <StatusChip status={r.passed ? 'GRADED' : 'FAILED'} label={r.passed ? 'Passed' : 'Not passed'} />,
    },
    { key: 'released', label: 'Released', hideBelow: 'sm', render: (r) => (r.released ? 'Yes' : 'No') },
    { key: 'focus', label: 'Tab switches', align: 'center', hideBelow: 'md', sortField: 'tabSwitchCount', render: (r) => <>{r.tabSwitchCount} {r.flagged && <FlagRounded fontSize="small" color="error" sx={{ verticalAlign: 'middle' }} />}</> },
    { key: 'submitted', label: 'Submitted', hideBelow: 'lg', render: (r) => formatDateTime(r.submittedAt) },
    {
      key: 'actions', label: '', align: 'right', render: (r) => (
        <Tooltip title="Evaluate"><IconButton size="small" onClick={(e) => { e.stopPropagation(); navigate(`/attempts/${r.attemptId}/evaluate`); }}><RateReviewRounded fontSize="small" /></IconButton></Tooltip>
      ),
    },
  ];

  return (
    <>
      <PageHeader title="Results & reports" subtitle="Scores, pass/fail outcomes and review status across your assessments."
        actions={<Button variant="contained" startIcon={<DownloadRounded />}
          onClick={() => reportApi.exportResults(filters).catch((e) => notify.error(errorMessage(e)))}>Export CSV</Button>} />
      <GlassCard noPadding>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ p: 2.5 }}>
          <TextField size="small" placeholder="Candidate or assessment…" value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }} sx={{ flex: 1 }}
            InputProps={{ startAdornment: <InputAdornment position="start"><SearchRounded /></InputAdornment> }} />
          <TextField select size="small" label="Outcome" value={passed} onChange={(e) => { setPassed(e.target.value); setPage(0); }} sx={{ minWidth: 140 }}>
            <MenuItem value="">Any</MenuItem><MenuItem value="true">Passed</MenuItem><MenuItem value="false">Not passed</MenuItem>
          </TextField>
          <TextField select size="small" label="Grading" value={gradingStatus} onChange={(e) => { setGradingStatus(e.target.value as ResultGradingStatus | ''); setPage(0); }} sx={{ minWidth: 160 }}>
            <MenuItem value="">Any</MenuItem><MenuItem value="PENDING_REVIEW">Needs review</MenuItem><MenuItem value="GRADED">Graded</MenuItem>
          </TextField>
          <TextField select size="small" label="Flagged" value={flagged} onChange={(e) => { setFlagged(e.target.value); setPage(0); }} sx={{ minWidth: 130 }}>
            <MenuItem value="">Any</MenuItem><MenuItem value="true">Flagged</MenuItem><MenuItem value="false">Not flagged</MenuItem>
          </TextField>
        </Stack>
        {Boolean(error) && <Box sx={{ px: 2.5 }}><ErrorState error={error} onRetry={reload} /></Box>}
        <DataTable columns={columns} rows={data?.content ?? []} getRowId={(r) => r.id} loading={loading}
          total={data?.totalElements ?? 0} page={page} size={size} onPageChange={setPage} onSizeChange={(s) => { setSize(s); setPage(0); }}
          sort={sort} onSortChange={setSort} onRowClick={(r) => navigate(`/attempts/${r.attemptId}`)} emptyMessage="No results yet." />
      </GlassCard>
    </>
  );
}
