import { AddRounded, ContentCopyRounded, DeleteOutlineRounded, EditRounded, SearchRounded } from '@mui/icons-material';
import { Box, Button, IconButton, InputAdornment, MenuItem, Stack, TextField, Tooltip, Typography } from '@mui/material';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { assignmentApi } from '../../api/services';
import type { Assignment, AssignmentStatus } from '../../api/types';
import { DataTable, sortParam, type Column, type SortState } from '../../components/common/DataTable';
import { ConfirmDialog, ErrorState } from '../../components/common/Feedback';
import { GlassCard } from '../../components/common/GlassCard';
import { useNotify } from '../../components/common/NotificationProvider';
import { PageHeader } from '../../components/common/PageHeader';
import { StatusChip } from '../../components/common/StatusChip';
import { useAsync, useDebounced } from '../../hooks/useAsync';
import { errorMessage } from '../../utils/errors';
import { formatDate, formatDuration, fromNow } from '../../utils/format';
import { ASSIGNMENT_STATUS_LABEL, DIFFICULTY_LABEL } from '../../utils/labels';

export default function AssignmentsPage() {
  const navigate = useNavigate();
  const notify = useNotify();
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<AssignmentStatus | ''>('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sort, setSort] = useState<SortState>({ field: 'updatedAt', direction: 'desc' });
  const [toDelete, setToDelete] = useState<Assignment | null>(null);
  const q = useDebounced(search);
  const { data, loading, error, reload } = useAsync(
    () => assignmentApi.list({ search: q, status, page, size, sort: sortParam(sort) }),
    [q, status, page, size, sort.field, sort.direction],
  );

  const duplicate = async (a: Assignment) => {
    try {
      const copy = await assignmentApi.duplicate(a.id);
      notify.success('Assessment duplicated');
      navigate(`/assignments/${copy.id}/edit`);
    } catch (e) {
      notify.error(errorMessage(e));
    }
  };

  const columns: Column<Assignment>[] = [
    {
      key: 'title', label: 'Assessment', sortField: 'title', render: (a) => (
        <Box sx={{ minWidth: 200 }}>
          <Typography fontWeight={700}>{a.title}</Typography>
          <Typography variant="caption" color="text.secondary">
            {[a.skillCategory, DIFFICULTY_LABEL[a.difficulty], formatDuration(a.durationMinutes)].filter(Boolean).join(' · ')}
          </Typography>
        </Box>
      ),
    },
    { key: 'status', label: 'Status', sortField: 'status', render: (a) => <StatusChip status={a.status} label={ASSIGNMENT_STATUS_LABEL[a.status]} /> },
    { key: 'questions', label: 'Questions', align: 'center', render: (a) => a.questionCount, hideBelow: 'sm' },
    {
      key: 'progress', label: 'Completed / invited', align: 'center', hideBelow: 'md',
      render: (a) => <Typography fontWeight={650}>{a.completedCount} / {a.assigneeCount}</Typography>,
    },
    { key: 'window', label: 'Deadline', sortField: 'endAt', hideBelow: 'md', render: (a) => (a.endAt ? formatDate(a.endAt) : 'Open-ended') },
    { key: 'owner', label: 'Owner', hideBelow: 'lg', render: (a) => a.ownerName },
    { key: 'updated', label: 'Updated', sortField: 'updatedAt', hideBelow: 'lg', render: (a) => fromNow(a.updatedAt) },
    {
      key: 'actions', label: '', align: 'right', render: (a) => (
        <Stack direction="row" spacing={0.5} justifyContent="flex-end" onClick={(e) => e.stopPropagation()}>
          <Tooltip title="Edit"><IconButton size="small" onClick={() => navigate(`/assignments/${a.id}/edit`)}><EditRounded fontSize="small" /></IconButton></Tooltip>
          <Tooltip title="Duplicate"><IconButton size="small" onClick={() => duplicate(a)}><ContentCopyRounded fontSize="small" /></IconButton></Tooltip>
          <Tooltip title="Delete"><IconButton size="small" color="error" onClick={() => setToDelete(a)}><DeleteOutlineRounded fontSize="small" /></IconButton></Tooltip>
        </Stack>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Assessments"
        subtitle="Create, schedule and track assignments for any role or tech stack."
        actions={<Button variant="contained" startIcon={<AddRounded />} onClick={() => navigate('/assignments/new')}>New assessment</Button>}
      />
      <GlassCard noPadding>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ p: 2.5 }}>
          <TextField
            placeholder="Search by title…"
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(0); }}
            size="small"
            sx={{ flex: 1, maxWidth: { sm: 420 } }}
            InputProps={{ startAdornment: <InputAdornment position="start"><SearchRounded /></InputAdornment> }}
          />
          <TextField select size="small" label="Status" value={status} onChange={(e) => { setStatus(e.target.value as AssignmentStatus | ''); setPage(0); }} sx={{ minWidth: 180 }}>
            <MenuItem value="">All statuses</MenuItem>
            {Object.entries(ASSIGNMENT_STATUS_LABEL).map(([v, l]) => <MenuItem key={v} value={v}>{l}</MenuItem>)}
          </TextField>
        </Stack>
        {Boolean(error) && <Box sx={{ px: 2.5 }}><ErrorState error={error} onRetry={reload} /></Box>}
        <DataTable
          columns={columns}
          rows={data?.content ?? []}
          getRowId={(a) => a.id}
          loading={loading}
          total={data?.totalElements ?? 0}
          page={page}
          size={size}
          onPageChange={setPage}
          onSizeChange={(s) => { setSize(s); setPage(0); }}
          sort={sort}
          onSortChange={setSort}
          onRowClick={(a) => navigate(`/assignments/${a.id}`)}
          emptyMessage="No assessments yet — create your first one."
        />
      </GlassCard>
      <ConfirmDialog
        open={Boolean(toDelete)}
        title="Delete assessment?"
        destructive
        confirmLabel="Delete"
        message={<>“{toDelete?.title}” will be permanently removed. Assessments with candidate attempts cannot be deleted — archive them instead.</>}
        onClose={() => setToDelete(null)}
        onConfirm={async () => {
          try {
            await assignmentApi.remove(toDelete!.id);
            notify.success('Assessment deleted');
            reload();
          } catch (e) {
            notify.error(errorMessage(e));
          }
        }}
      />
    </>
  );
}
