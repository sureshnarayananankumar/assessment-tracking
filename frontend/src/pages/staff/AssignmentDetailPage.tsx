import {
  ArchiveRounded, AttachFileRounded, CampaignRounded, ContentCopyRounded, DeleteOutlineRounded, DownloadRounded,
  EditRounded, FlagRounded, LockOpenRounded, NotificationsActiveRounded, PersonOffRounded, PublishRounded,
  RateReviewRounded, StopCircleRounded, UndoRounded, UploadFileRounded, VisibilityRounded,
} from '@mui/icons-material';
import {
  Alert, Box, Button, Chip, IconButton, Stack, Tab, Tabs, Tooltip, Typography,
} from '@mui/material';
import { useRef, useState, type ReactNode } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { assignmentApi, candidateApi } from '../../api/services';
import type { Assignee, Assignment, AssignmentStatus, Result } from '../../api/types';
import { DataTable, type Column } from '../../components/common/DataTable';
import { ConfirmDialog, ErrorState } from '../../components/common/Feedback';
import { GlassCard } from '../../components/common/GlassCard';
import { SectionLoader } from '../../components/common/Loaders';
import { useNotify } from '../../components/common/NotificationProvider';
import { PageHeader } from '../../components/common/PageHeader';
import { StatusChip } from '../../components/common/StatusChip';
import { useAsync } from '../../hooks/useAsync';
import { errorMessage } from '../../utils/errors';
import { formatBytes, formatDateTime, formatDuration, formatPercent, formatScore, fromNow } from '../../utils/format';
import {
  ASSESSMENT_TYPE_LABEL, ASSIGNEE_STATUS_LABEL, ASSIGNMENT_STATUS_LABEL, ATTEMPT_STATUS_LABEL, DIFFICULTY_LABEL,
  QUESTION_TYPE_LABEL, VIOLATION_ACTION_LABEL,
} from '../../utils/labels';

type PendingAction = { title: string; message: ReactNode; label: string; destructive?: boolean; run: () => Promise<unknown> };

export default function AssignmentDetailPage() {
  const { id } = useParams();
  const assignmentId = Number(id);
  const navigate = useNavigate();
  const notify = useNotify();
  const [tab, setTab] = useState(0);
  const [pending, setPending] = useState<PendingAction | null>(null);
  const { data: a, loading, error, reload, setData } = useAsync(() => assignmentApi.get(assignmentId), [assignmentId]);

  const run = async (fn: () => Promise<unknown>, success: string) => {
    try {
      const res = await fn();
      if (res && typeof res === 'object' && 'id' in (res as object)) setData(res as Assignment);
      else reload();
      notify.success(typeof res === 'string' ? res : success);
    } catch (e) {
      notify.error(errorMessage(e));
    }
  };

  const changeStatus = (status: AssignmentStatus, title: string, message: ReactNode, label: string, destructive = false) =>
    setPending({ title, message, label, destructive, run: () => run(() => assignmentApi.changeStatus(assignmentId, status), 'Status updated') });

  if (loading && !a) return <SectionLoader rows={8} />;
  if (error || !a) return <ErrorState error={error} onRetry={reload} />;

  const open = a.status === 'PUBLISHED' || a.status === 'ACTIVE';
  const actions = (
    <>
      {a.status === 'DRAFT' && (
        <Button variant="contained" startIcon={<PublishRounded />} onClick={() => setPending({
          title: 'Publish assessment?', label: 'Publish',
          message: 'Candidates can be invited once it is published. Questions stay editable until someone starts an attempt.',
          run: () => run(() => assignmentApi.publish(assignmentId), 'Assessment published'),
        })}>Publish</Button>
      )}
      {open && <Button variant="contained" startIcon={<CampaignRounded />} onClick={() => navigate(`/assignments/${assignmentId}/invite`)}>Invite people</Button>}
      {a.status !== 'ARCHIVED' && <Button variant="outlined" startIcon={<EditRounded />} onClick={() => navigate(`/assignments/${assignmentId}/edit`)}>Edit</Button>}
      {open && (
        <Button variant="outlined" startIcon={<StopCircleRounded />} onClick={() => changeStatus('COMPLETED', 'Close assessment?',
          'No new attempts can start. Running attempts must finish first.', 'Close')}>Close</Button>
      )}
      {a.status === 'COMPLETED' && (
        <Button variant="outlined" startIcon={<LockOpenRounded />} onClick={() => changeStatus('ACTIVE', 'Reopen assessment?',
          'Candidates with remaining attempts can start again until the deadline.', 'Reopen')}>Reopen</Button>
      )}
      {a.attemptCount > 0 && (a.resultsReleasePolicy === 'MANUAL' || !a.resultsReleased) && (
        <Button variant="outlined" startIcon={<VisibilityRounded />} onClick={() => setPending({
          title: 'Release results?', label: 'Release',
          message: 'All graded results become visible to candidates and they are notified by e-mail. Results still being graded are released automatically once graded.',
          run: () => run(() => assignmentApi.releaseResults(assignmentId), 'Results released'),
        })}>Release results</Button>
      )}
      <Tooltip title="Duplicate">
        <IconButton onClick={() => run(async () => {
          const copy = await assignmentApi.duplicate(assignmentId);
          navigate(`/assignments/${copy.id}/edit`);
          return 'Duplicated';
        }, 'Duplicated')}><ContentCopyRounded /></IconButton>
      </Tooltip>
      {a.status !== 'ARCHIVED' ? (
        <Tooltip title="Archive">
          <IconButton onClick={() => changeStatus('ARCHIVED', 'Archive assessment?', 'It will be hidden from candidates. Results are kept.', 'Archive', true)}><ArchiveRounded /></IconButton>
        </Tooltip>
      ) : !a.hasAttempts ? (
        <Tooltip title="Restore to draft">
          <IconButton onClick={() => changeStatus('DRAFT', 'Restore to draft?', 'The assessment becomes an editable draft again.', 'Restore')}><UndoRounded /></IconButton>
        </Tooltip>
      ) : null}
      {!a.hasAttempts && (
        <Tooltip title="Delete">
          <IconButton color="error" onClick={() => setPending({
            title: 'Delete assessment?', label: 'Delete', destructive: true,
            message: 'This permanently removes the assessment, its invitations and attachments.',
            run: () => run(async () => { await assignmentApi.remove(assignmentId); navigate('/assignments'); return 'Deleted'; }, 'Deleted'),
          })}><DeleteOutlineRounded /></IconButton>
        </Tooltip>
      )}
    </>
  );

  return (
    <>
      <PageHeader
        backTo="/assignments"
        overline={`${ASSESSMENT_TYPE_LABEL[a.assessmentType]} · ${DIFFICULTY_LABEL[a.difficulty]}`}
        title={<Stack direction="row" spacing={1.5} alignItems="center" flexWrap="wrap" useFlexGap>{a.title}<StatusChip status={a.status} label={ASSIGNMENT_STATUS_LABEL[a.status]} /></Stack>}
        subtitle={a.description}
        actions={actions}
      />
      <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" sx={{ mb: 2.5 }}>
        <Tab label="Overview" />
        <Tab label={`Questions (${a.questionCount})`} />
        <Tab label={`People (${a.assigneeCount})`} />
        <Tab label={`Results (${a.attemptCount})`} />
      </Tabs>
      {tab === 0 && <Overview a={a} onChanged={reload} />}
      {tab === 1 && <QuestionsTab a={a} />}
      {tab === 2 && <AssigneesTab a={a} />}
      {tab === 3 && <ResultsTab a={a} />}
      <ConfirmDialog
        open={Boolean(pending)}
        title={pending?.title ?? ''}
        message={pending?.message}
        confirmLabel={pending?.label}
        destructive={pending?.destructive}
        onClose={() => setPending(null)}
        onConfirm={async () => { await pending?.run(); }}
      />
    </>
  );
}

function Fact({ label, value }: { label: string; value: ReactNode }) {
  return (
    <Box sx={{ p: 1.75, borderRadius: 3, border: 1, borderColor: 'divider', bgcolor: 'action.hover' }}>
      <Typography variant="caption" color="text.secondary">{label}</Typography>
      <Typography fontWeight={700}>{value}</Typography>
    </Box>
  );
}

function Overview({ a, onChanged }: { a: Assignment; onChanged: () => void }) {
  const notify = useNotify();
  const fileRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);

  const upload = async (file?: File) => {
    if (!file) return;
    setUploading(true);
    try {
      await assignmentApi.uploadAttachment(a.id, file);
      notify.success('Attachment uploaded');
      onChanged();
    } catch (e) {
      notify.error(errorMessage(e));
    } finally {
      setUploading(false);
      if (fileRef.current) fileRef.current.value = '';
    }
  };

  return (
    <Box sx={{ display: 'grid', gap: 2.5, gridTemplateColumns: { xs: '1fr', lg: '1.4fr 1fr' }, alignItems: 'start' }}>
      <Stack spacing={2.5}>
        <GlassCard title="At a glance">
          <Box sx={{ display: 'grid', gap: 1.5, gridTemplateColumns: { xs: 'repeat(2, 1fr)', md: 'repeat(4, 1fr)' } }}>
            <Fact label="Duration" value={formatDuration(a.durationMinutes)} />
            <Fact label="Total / pass marks" value={`${a.totalMarks} / ${a.passingMarks}`} />
            <Fact label="Attempts allowed" value={a.maxAttempts} />
            <Fact label="Grace period" value={`${a.gracePeriodSeconds}s`} />
            <Fact label="Opens" value={a.startAt ? formatDateTime(a.startAt) : 'On publish'} />
            <Fact label="Deadline" value={a.endAt ? formatDateTime(a.endAt) : 'None'} />
            <Fact label="Invited" value={a.assigneeCount} />
            <Fact label="Completed" value={a.completedCount} />
          </Box>
        </GlassCard>
        <GlassCard title="Instructions">
          <Typography sx={{ whiteSpace: 'pre-wrap' }} color={a.instructions ? 'text.primary' : 'text.secondary'}>
            {a.instructions || 'No instructions provided.'}
          </Typography>
          {a.evaluationCriteria && (
            <>
              <Typography variant="subtitle2" sx={{ mt: 2.5, mb: 0.5 }}>Evaluation criteria</Typography>
              <Typography sx={{ whiteSpace: 'pre-wrap' }}>{a.evaluationCriteria}</Typography>
            </>
          )}
        </GlassCard>
      </Stack>
      <Stack spacing={2.5}>
        <GlassCard title="Policies">
          <Stack spacing={1}>
            <Row label="Question order" value={a.shuffleQuestions ? 'Randomised' : 'Fixed'} />
            <Row label="Option order" value={a.shuffleOptions ? 'Randomised' : 'Fixed'} />
            <Row label="Results" value={a.resultsReleasePolicy === 'MANUAL' ? 'Released manually' : 'Released once graded'} />
            <Row label="Correct answers" value={a.showCorrectAnswers ? 'Shown after release' : 'Hidden'} />
            <Row label="Focus monitoring" value={a.monitoringEnabled ? `On · warn at ${a.warningThreshold}, act at ${a.maxViolations}` : 'Off'} />
            {a.monitoringEnabled && <Row label="At the limit" value={VIOLATION_ACTION_LABEL[a.violationAction]} />}
            <Row label="Owner" value={a.ownerName} />
            <Row label="Published" value={a.publishedAt ? formatDateTime(a.publishedAt) : '—'} />
          </Stack>
        </GlassCard>
        <GlassCard
          title="Reference materials"
          subtitle="Downloadable by assigned candidates"
          action={a.status !== 'ARCHIVED' && (
            <Button size="small" variant="outlined" startIcon={<UploadFileRounded />} disabled={uploading} onClick={() => fileRef.current?.click()}>
              {uploading ? 'Uploading…' : 'Upload'}
            </Button>
          )}
        >
          <input ref={fileRef} type="file" hidden onChange={(e) => upload(e.target.files?.[0])} />
          {(a.attachments ?? []).length === 0 ? (
            <Typography color="text.secondary">No attachments.</Typography>
          ) : (
            <Stack spacing={1}>
              {a.attachments!.map((att) => (
                <Stack key={att.id} direction="row" alignItems="center" spacing={1.25}>
                  <AttachFileRounded fontSize="small" color="primary" />
                  <Typography sx={{ flex: 1, minWidth: 0 }} noWrap>{att.originalName}</Typography>
                  <Typography variant="caption" color="text.secondary">{formatBytes(att.sizeBytes)}</Typography>
                  <IconButton size="small" onClick={() => candidateApi.downloadAttachment(a.id, att).catch((e) => notify.error(errorMessage(e)))} aria-label="Download"><DownloadRounded fontSize="small" /></IconButton>
                  <IconButton size="small" color="error" aria-label="Remove" onClick={async () => {
                    try { await assignmentApi.deleteAttachment(a.id, att.id); onChanged(); } catch (e) { notify.error(errorMessage(e)); }
                  }}><DeleteOutlineRounded fontSize="small" /></IconButton>
                </Stack>
              ))}
            </Stack>
          )}
        </GlassCard>
      </Stack>
    </Box>
  );
}

function Row({ label, value }: { label: string; value: ReactNode }) {
  return (
    <Stack direction="row" justifyContent="space-between" spacing={2}>
      <Typography color="text.secondary">{label}</Typography>
      <Typography fontWeight={600} textAlign="right">{value}</Typography>
    </Stack>
  );
}

function QuestionsTab({ a }: { a: Assignment }) {
  return (
    <Stack spacing={1.5}>
      {(a.questions ?? []).map((aq, i) => (
        <GlassCard key={aq.id}>
          <Stack direction="row" spacing={2} alignItems="flex-start">
            <Chip label={i + 1} color="primary" />
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                <Typography fontWeight={700}>{aq.question.title}</Typography>
                <Chip size="small" label={QUESTION_TYPE_LABEL[aq.question.type]} />
                <Chip size="small" variant="outlined" label={`${aq.marks} marks`} />
              </Stack>
              <Typography sx={{ mt: 1, whiteSpace: 'pre-wrap' }} color="text.secondary">{aq.question.body}</Typography>
              {aq.question.options.length > 0 && (
                <Stack spacing={0.5} sx={{ mt: 1.5 }}>
                  {aq.question.options.map((o) => (
                    <Typography key={o.id} variant="body2" sx={{ color: o.correct ? 'success.main' : 'text.secondary', fontWeight: o.correct ? 700 : 400 }}>
                      {o.correct ? '✓' : '•'} {o.text}
                    </Typography>
                  ))}
                </Stack>
              )}
            </Box>
          </Stack>
        </GlassCard>
      ))}
      {(a.questions ?? []).length === 0 && <Alert severity="info">No questions yet. Edit the assessment to add questions.</Alert>}
    </Stack>
  );
}

function AssigneesTab({ a }: { a: Assignment }) {
  const navigate = useNavigate();
  const notify = useNotify();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [revoke, setRevoke] = useState<Assignee | null>(null);
  const { data, loading, error, reload } = useAsync(() => assignmentApi.assignees(a.id, { page, size }), [a.id, page, size]);
  const open = a.status === 'PUBLISHED' || a.status === 'ACTIVE';

  const columns: Column<Assignee>[] = [
    {
      key: 'person', label: 'Person', render: (r) => (
        <Box>
          <Typography fontWeight={650}>{r.fullName}</Typography>
          <Typography variant="caption" color="text.secondary">{r.email}</Typography>
        </Box>
      ),
    },
    { key: 'status', label: 'Status', render: (r) => <StatusChip status={r.status} label={ASSIGNEE_STATUS_LABEL[r.status]} /> },
    { key: 'invite', label: 'Invitation', hideBelow: 'md', render: (r) => (r.invitationStatus ? <StatusChip status={r.invitationStatus} variant="outlined" /> : '—') },
    { key: 'attempts', label: 'Attempts', align: 'center', render: (r) => `${r.attemptsUsed}/${r.maxAttempts}` },
    {
      key: 'score', label: 'Score', align: 'center', render: (r) =>
        r.latestPercentage !== undefined && r.latestPercentage !== null
          ? <Typography fontWeight={700} color={r.passed ? 'success.main' : r.passed === false ? 'error.main' : 'text.primary'}>{formatPercent(r.latestPercentage)}</Typography>
          : r.gradingStatus === 'PENDING_REVIEW' ? <StatusChip status="PENDING_REVIEW" label="To review" /> : '—',
    },
    {
      key: 'focus', label: 'Tab switches', align: 'center', hideBelow: 'sm', render: (r) => (
        <Stack direction="row" spacing={0.5} justifyContent="center" alignItems="center">
          <span>{r.tabSwitchCount}</span>{r.flagged && <FlagRounded fontSize="small" color="error" />}
        </Stack>
      ),
    },
    { key: 'deadline', label: 'Deadline', hideBelow: 'lg', render: (r) => (r.effectiveDeadline ? formatDateTime(r.effectiveDeadline) : '—') },
    {
      key: 'actions', label: '', align: 'right', render: (r) => (
        <Stack direction="row" spacing={0.5} justifyContent="flex-end">
          {r.latestAttemptId && (
            <Tooltip title="View attempt"><IconButton size="small" onClick={() => navigate(`/attempts/${r.latestAttemptId}`)}><VisibilityRounded fontSize="small" /></IconButton></Tooltip>
          )}
          {open && (r.status === 'ASSIGNED' || r.status === 'IN_PROGRESS') && (
            <Tooltip title={r.lastReminderAt ? `Last reminded ${fromNow(r.lastReminderAt)}` : 'Send reminder (new link)'}>
              <IconButton size="small" onClick={async () => {
                try { notify.success((await assignmentApi.remind(a.id, r.id)) ?? 'Reminder sent'); reload(); } catch (e) { notify.error(errorMessage(e)); }
              }}><NotificationsActiveRounded fontSize="small" /></IconButton>
            </Tooltip>
          )}
          {r.status !== 'REVOKED' && (
            <Tooltip title="Revoke access"><IconButton size="small" color="error" onClick={() => setRevoke(r)}><PersonOffRounded fontSize="small" /></IconButton></Tooltip>
          )}
        </Stack>
      ),
    },
  ];

  return (
    <GlassCard noPadding title="People assigned" subtitle="Duplicate invitations reuse the same assignment and revoke older links"
      action={open && <Button variant="contained" size="small" startIcon={<CampaignRounded />} onClick={() => navigate(`/assignments/${a.id}/invite`)}>Invite</Button>}>
      {Boolean(error) && <Box sx={{ px: 2.5 }}><ErrorState error={error} onRetry={reload} /></Box>}
      <DataTable columns={columns} rows={data?.content ?? []} getRowId={(r) => r.id} loading={loading}
        total={data?.totalElements ?? 0} page={page} size={size} onPageChange={setPage} onSizeChange={(s) => { setSize(s); setPage(0); }}
        emptyMessage={open ? 'Nobody invited yet.' : 'Publish the assessment to invite people.'} />
      <ConfirmDialog open={Boolean(revoke)} title="Revoke access?" destructive confirmLabel="Revoke"
        message={<>{revoke?.fullName} will no longer be able to open the assessment. A running attempt is ended and kept for review.</>}
        onClose={() => setRevoke(null)}
        onConfirm={async () => {
          try { await assignmentApi.revoke(a.id, revoke!.id); notify.success('Access revoked'); reload(); } catch (e) { notify.error(errorMessage(e)); }
        }} />
    </GlassCard>
  );
}

function ResultsTab({ a }: { a: Assignment }) {
  const navigate = useNavigate();
  const notify = useNotify();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const { data, loading, error, reload } = useAsync(() => assignmentApi.results(a.id, { page, size }), [a.id, page, size]);

  const columns: Column<Result>[] = [
    {
      key: 'candidate', label: 'Candidate', render: (r) => (
        <Box>
          <Typography fontWeight={650}>{r.candidateName}</Typography>
          <Typography variant="caption" color="text.secondary">{r.candidateEmail} · attempt {r.attemptNumber}</Typography>
        </Box>
      ),
    },
    { key: 'status', label: 'Attempt', hideBelow: 'sm', render: (r) => <StatusChip status={r.attemptStatus} label={ATTEMPT_STATUS_LABEL[r.attemptStatus]} /> },
    { key: 'score', label: 'Score', render: (r) => formatScore(r.totalScore, r.maxScore) },
    { key: 'pct', label: '%', align: 'center', render: (r) => formatPercent(r.percentage) },
    {
      key: 'outcome', label: 'Outcome', render: (r) => r.gradingStatus === 'PENDING_REVIEW'
        ? <StatusChip status="PENDING_REVIEW" label="Needs review" />
        : <StatusChip status={r.passed ? 'GRADED' : 'FAILED'} label={r.passed ? 'Passed' : 'Not passed'} />,
    },
    { key: 'released', label: 'Released', hideBelow: 'md', render: (r) => (r.released ? 'Yes' : 'No') },
    { key: 'focus', label: 'Tab switches', align: 'center', hideBelow: 'md', render: (r) => <>{r.tabSwitchCount} {r.flagged && <FlagRounded fontSize="small" color="error" sx={{ verticalAlign: 'middle' }} />}</> },
    { key: 'submitted', label: 'Submitted', hideBelow: 'lg', render: (r) => formatDateTime(r.submittedAt) },
    {
      key: 'actions', label: '', align: 'right', render: (r) => (
        <Tooltip title="Grade / review">
          <IconButton size="small" onClick={(e) => { e.stopPropagation(); navigate(`/attempts/${r.attemptId}/evaluate`); }}><RateReviewRounded fontSize="small" /></IconButton>
        </Tooltip>
      ),
    },
  ];

  return (
    <GlassCard noPadding title="Results" action={
      <Button size="small" variant="outlined" startIcon={<DownloadRounded />} onClick={() => assignmentApi.exportResults(a.id).catch((e) => notify.error(errorMessage(e)))}>
        Export CSV
      </Button>
    }>
      {Boolean(error) && <Box sx={{ px: 2.5 }}><ErrorState error={error} onRetry={reload} /></Box>}
      <DataTable columns={columns} rows={data?.content ?? []} getRowId={(r) => r.id} loading={loading}
        total={data?.totalElements ?? 0} page={page} size={size} onPageChange={setPage} onSizeChange={(s) => { setSize(s); setPage(0); }}
        onRowClick={(r) => navigate(`/attempts/${r.attemptId}`)} emptyMessage="No submissions yet." />
    </GlassCard>
  );
}
