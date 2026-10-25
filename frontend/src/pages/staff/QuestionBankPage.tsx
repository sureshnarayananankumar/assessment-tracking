import { AddRounded, ContentCopyRounded, DeleteOutlineRounded, EditRounded, PublicRounded, SearchRounded } from '@mui/icons-material';
import { Box, Button, Chip, IconButton, InputAdornment, MenuItem, Stack, TextField, Tooltip, Typography } from '@mui/material';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { questionApi } from '../../api/services';
import type { Difficulty, Question, QuestionType } from '../../api/types';
import { DataTable, sortParam, type Column, type SortState } from '../../components/common/DataTable';
import { ConfirmDialog, ErrorState } from '../../components/common/Feedback';
import { GlassCard } from '../../components/common/GlassCard';
import { useNotify } from '../../components/common/NotificationProvider';
import { PageHeader } from '../../components/common/PageHeader';
import { useAsync, useDebounced } from '../../hooks/useAsync';
import { errorMessage } from '../../utils/errors';
import { fromNow } from '../../utils/format';
import { DIFFICULTY_LABEL, QUESTION_TYPE_LABEL } from '../../utils/labels';

export default function QuestionBankPage() {
  const navigate = useNavigate();
  const notify = useNotify();
  const [search, setSearch] = useState('');
  const [type, setType] = useState<QuestionType | ''>('');
  const [difficulty, setDifficulty] = useState<Difficulty | ''>('');
  const [skill, setSkill] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sort, setSort] = useState<SortState>({ field: 'updatedAt', direction: 'desc' });
  const [toDelete, setToDelete] = useState<Question | null>(null);
  const q = useDebounced(search);
  const skills = useAsync(() => questionApi.skills(), []);
  const { data, loading, error, reload } = useAsync(
    () => questionApi.list({ search: q, type, difficulty, skill, page, size, sort: sortParam(sort) }),
    [q, type, difficulty, skill, page, size, sort.field, sort.direction],
  );

  const columns: Column<Question>[] = [
    {
      key: 'title', label: 'Question', sortField: 'title', render: (r) => (
        <Box sx={{ minWidth: 220 }}>
          <Stack direction="row" spacing={0.75} alignItems="center">
            <Typography fontWeight={700}>{r.title}</Typography>
            {r.visibility === 'SHARED' && <Tooltip title="Shared with all assessors"><PublicRounded fontSize="inherit" color="info" /></Tooltip>}
          </Stack>
          <Typography variant="caption" color="text.secondary" sx={{ display: '-webkit-box', WebkitLineClamp: 1, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
            {r.body}
          </Typography>
        </Box>
      ),
    },
    { key: 'type', label: 'Type', sortField: 'type', render: (r) => <Chip size="small" label={QUESTION_TYPE_LABEL[r.type]} /> },
    { key: 'skill', label: 'Skill', hideBelow: 'md', render: (r) => r.skillCategory ?? '—' },
    { key: 'difficulty', label: 'Difficulty', sortField: 'difficulty', hideBelow: 'sm', render: (r) => DIFFICULTY_LABEL[r.difficulty] },
    { key: 'marks', label: 'Marks', align: 'center', sortField: 'defaultMarks', render: (r) => r.defaultMarks },
    { key: 'owner', label: 'Owner', hideBelow: 'lg', render: (r) => r.ownerName },
    { key: 'updated', label: 'Updated', sortField: 'updatedAt', hideBelow: 'lg', render: (r) => fromNow(r.updatedAt) },
    {
      key: 'actions', label: '', align: 'right', render: (r) => (
        <Stack direction="row" spacing={0.5} justifyContent="flex-end" onClick={(e) => e.stopPropagation()}>
          {r.editable && <Tooltip title="Edit"><IconButton size="small" onClick={() => navigate(`/questions/${r.id}/edit`)}><EditRounded fontSize="small" /></IconButton></Tooltip>}
          <Tooltip title="Duplicate"><IconButton size="small" onClick={async () => {
            try {
              const copy = await questionApi.duplicate(r.id);
              notify.success('Question duplicated');
              navigate(`/questions/${copy.id}/edit`);
            } catch (e) { notify.error(errorMessage(e)); }
          }}><ContentCopyRounded fontSize="small" /></IconButton></Tooltip>
          {r.editable && <Tooltip title="Delete"><IconButton size="small" color="error" onClick={() => setToDelete(r)}><DeleteOutlineRounded fontSize="small" /></IconButton></Tooltip>}
        </Stack>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Question bank"
        subtitle="Reusable questions for every role: multiple choice, true/false, short & long answers, coding and file uploads."
        actions={<Button variant="contained" startIcon={<AddRounded />} onClick={() => navigate('/questions/new')}>New question</Button>}
      />
      <GlassCard noPadding>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ p: 2.5 }}>
          <TextField placeholder="Search title or tags…" size="small" value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }}
            sx={{ flex: 1 }} InputProps={{ startAdornment: <InputAdornment position="start"><SearchRounded /></InputAdornment> }} />
          <TextField select size="small" label="Type" value={type} onChange={(e) => { setType(e.target.value as QuestionType | ''); setPage(0); }} sx={{ minWidth: 170 }}>
            <MenuItem value="">All types</MenuItem>
            {Object.entries(QUESTION_TYPE_LABEL).map(([v, l]) => <MenuItem key={v} value={v}>{l}</MenuItem>)}
          </TextField>
          <TextField select size="small" label="Difficulty" value={difficulty} onChange={(e) => { setDifficulty(e.target.value as Difficulty | ''); setPage(0); }} sx={{ minWidth: 150 }}>
            <MenuItem value="">Any</MenuItem>
            {Object.entries(DIFFICULTY_LABEL).map(([v, l]) => <MenuItem key={v} value={v}>{l}</MenuItem>)}
          </TextField>
          <TextField select size="small" label="Skill" value={skill} onChange={(e) => { setSkill(e.target.value); setPage(0); }} sx={{ minWidth: 150 }}>
            <MenuItem value="">Any</MenuItem>
            {(skills.data ?? []).map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}
          </TextField>
        </Stack>
        {Boolean(error) && <Box sx={{ px: 2.5 }}><ErrorState error={error} onRetry={reload} /></Box>}
        <DataTable columns={columns} rows={data?.content ?? []} getRowId={(r) => r.id} loading={loading}
          total={data?.totalElements ?? 0} page={page} size={size} onPageChange={setPage} onSizeChange={(s) => { setSize(s); setPage(0); }}
          sort={sort} onSortChange={setSort}
          onRowClick={(r) => r.editable && navigate(`/questions/${r.id}/edit`)}
          emptyMessage="No questions yet. Create your first question." />
      </GlassCard>
      <ConfirmDialog open={Boolean(toDelete)} title="Delete question?" destructive confirmLabel="Delete"
        message="Questions already used in assessments are archived instead of deleted, so past results stay intact."
        onClose={() => setToDelete(null)}
        onConfirm={async () => {
          try { notify.success((await questionApi.remove(toDelete!.id)) ?? 'Deleted'); reload(); } catch (e) { notify.error(errorMessage(e)); }
        }} />
    </>
  );
}
