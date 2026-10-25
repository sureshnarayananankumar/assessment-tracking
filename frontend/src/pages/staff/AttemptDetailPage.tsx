import { DownloadRounded, FlagRounded, OutlinedFlagRounded, RateReviewRounded, VisibilityRounded } from '@mui/icons-material';
import {
  Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField, Typography,
} from '@mui/material';
import { useState, type ReactNode } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { attemptApi } from '../../api/services';
import type { ReviewAnswer } from '../../api/types';
import { ActivityTimeline } from '../../components/assessment/ActivityTimeline';
import { ErrorState } from '../../components/common/Feedback';
import { GlassCard } from '../../components/common/GlassCard';
import { SectionLoader } from '../../components/common/Loaders';
import { useNotify } from '../../components/common/NotificationProvider';
import { PageHeader } from '../../components/common/PageHeader';
import { StatusChip } from '../../components/common/StatusChip';
import { useAsync } from '../../hooks/useAsync';
import { errorMessage } from '../../utils/errors';
import { formatBytes, formatDateTime, formatMs, formatPercent, formatScore } from '../../utils/format';
import { ATTEMPT_STATUS_LABEL, QUESTION_TYPE_LABEL } from '../../utils/labels';

export default function AttemptDetailPage() {
  const { attemptId } = useParams();
  const id = Number(attemptId);
  const navigate = useNavigate();
  const notify = useNotify();
  const review = useAsync(() => attemptApi.review(id), [id]);
  const activity = useAsync(() => attemptApi.activityLog(id), [id]);
  const [flagOpen, setFlagOpen] = useState(false);
  const [reason, setReason] = useState('');

  if (review.loading && !review.data) return <SectionLoader rows={8} />;
  if (review.error || !review.data) return <ErrorState error={review.error} onRetry={review.reload} />;
  const r = review.data;
  const result = r.result;

  const toggleFlag = async (flagged: boolean) => {
    try {
      review.setData(await attemptApi.flag(id, flagged, flagged ? reason : undefined));
      activity.reload();
      notify.success(flagged ? 'Attempt flagged for review' : 'Flag cleared');
      setFlagOpen(false);
    } catch (e) {
      notify.error(errorMessage(e));
    }
  };

  return (
    <>
      <PageHeader
        backTo={`/assignments/${r.assignmentId}`}
        overline={`${r.assignmentTitle} · attempt ${r.attemptNumber}`}
        title={r.candidateName}
        subtitle={r.candidateEmail}
        actions={
          <>
            {r.flagged
              ? <Button variant="outlined" startIcon={<OutlinedFlagRounded />} onClick={() => toggleFlag(false)}>Clear flag</Button>
              : <Button variant="outlined" color="warning" startIcon={<FlagRounded />} onClick={() => setFlagOpen(true)}>Flag for review</Button>}
            {result?.gradingStatus === 'GRADED' && !result.released && (
              <Button variant="outlined" startIcon={<VisibilityRounded />} onClick={async () => {
                try { await attemptApi.release(id); review.reload(); notify.success('Result released to candidate'); } catch (e) { notify.error(errorMessage(e)); }
              }}>Release result</Button>
            )}
            <Button variant="contained" startIcon={<RateReviewRounded />} disabled={r.status === 'IN_PROGRESS'} onClick={() => navigate(`/attempts/${id}/evaluate`)}>
              Evaluate answers
            </Button>
          </>
        }
      />
      {r.flagged && (
        <Alert severity="warning" icon={<FlagRounded />} sx={{ mb: 2 }}>
          Flagged for review{r.flagReason ? `: ${r.flagReason}` : ''}. Focus events can have innocent causes — review the context before deciding.
        </Alert>
      )}
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: 'repeat(2, 1fr)', md: 'repeat(4, 1fr)' }, mb: 2.5 }}>
        <Metric label="Status" value={<StatusChip status={r.status} label={ATTEMPT_STATUS_LABEL[r.status]} />} />
        <Metric label="Score" value={result ? `${formatScore(result.totalScore, result.maxScore)} (${formatPercent(result.percentage)})` : '—'}
          hint={result ? (result.gradingStatus === 'PENDING_REVIEW' ? 'Awaiting manual grading' : result.passed ? 'Passed' : 'Not passed') : undefined} />
        <Metric label="Tab switches / focus losses" value={`${r.tabSwitchCount} / ${r.focusLossCount}`} hint={`${r.violationCount} counted · ${formatMs(r.totalAwayMs)} away`} />
        <Metric label="Result visibility" value={result?.released ? 'Released' : 'Hidden'} hint={result?.releasedAt ? formatDateTime(result.releasedAt) : undefined} />
      </Box>
      <Box sx={{ display: 'grid', gap: 2.5, gridTemplateColumns: { xs: '1fr', lg: '1.6fr 1fr' }, alignItems: 'start' }}>
        <GlassCard title="Answers" subtitle={`${r.answers.length} questions`}>
          <Stack spacing={2}>
            {r.answers.map((a) => <AnswerSummary key={a.answerId} a={a} attemptId={id} />)}
          </Stack>
        </GlassCard>
        <Stack spacing={2.5}>
          <GlassCard title="Timing">
            <Stack spacing={1}>
              <Row label="Started" value={formatDateTime(r.startedAt)} />
              <Row label="Server deadline" value={formatDateTime(r.deadlineAt)} />
              <Row label="Submitted" value={formatDateTime(r.submittedAt)} />
              {r.submissionReason && <Row label="How" value={r.submissionReason} />}
              <Row label="IP address" value={r.ipAddress ?? '—'} />
              <Typography variant="caption" color="text.secondary" sx={{ wordBreak: 'break-word' }}>{r.userAgent}</Typography>
            </Stack>
          </GlassCard>
          <GlassCard title="Activity log" subtitle="Browser focus events and server lifecycle events">
            {activity.loading ? <SectionLoader rows={3} /> : <ActivityTimeline events={activity.data ?? []} />}
          </GlassCard>
          {result?.feedback && <GlassCard title="Feedback to candidate"><Typography sx={{ whiteSpace: 'pre-wrap' }}>{result.feedback}</Typography></GlassCard>}
        </Stack>
      </Box>
      <Dialog open={flagOpen} onClose={() => setFlagOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Flag attempt for review</DialogTitle>
        <DialogContent>
          <TextField autoFocus fullWidth multiline minRows={3} label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} sx={{ mt: 1 }} inputProps={{ maxLength: 255 }} />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setFlagOpen(false)}>Cancel</Button>
          <Button variant="contained" color="warning" onClick={() => toggleFlag(true)}>Flag</Button>
        </DialogActions>
      </Dialog>
    </>
  );
}

function Metric({ label, value, hint }: { label: string; value: ReactNode; hint?: string }) {
  return (
    <GlassCard>
      <Typography variant="caption" color="text.secondary">{label}</Typography>
      <Typography variant="h6" component="div" sx={{ mt: 0.5 }}>{value}</Typography>
      {hint && <Typography variant="caption" color="text.secondary">{hint}</Typography>}
    </GlassCard>
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

export function AnswerSummary({ a, attemptId }: { a: ReviewAnswer; attemptId: number }) {
  const notify = useNotify();
  const answered = Boolean(a.textAnswer || a.codeAnswer || a.fileName || a.options?.some((o) => o.selected));
  return (
    <Box sx={{ p: 2, borderRadius: 3, border: 1, borderColor: 'divider', bgcolor: 'action.hover' }}>
      <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap sx={{ mb: 1 }}>
        <Chip size="small" color="primary" label={`Q${a.index}`} />
        <Typography fontWeight={700} sx={{ flex: 1, minWidth: 160 }}>{a.title}</Typography>
        <Chip size="small" variant="outlined" label={QUESTION_TYPE_LABEL[a.type]} />
        <StatusChip status={a.gradingStatus === 'PENDING' ? 'PENDING_REVIEW' : a.correct === false ? 'FAILED' : 'GRADED'}
          label={a.gradingStatus === 'PENDING' ? 'Needs grading' : `${formatScore(a.marksAwarded, a.maxMarks)}`} />
      </Stack>
      <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'pre-wrap', mb: 1.25 }}>{a.body}</Typography>
      {!answered && <Typography variant="body2" color="text.secondary" fontStyle="italic">Not answered</Typography>}
      {a.options && (
        <Stack spacing={0.5}>
          {a.options.map((o) => (
            <Typography key={o.id} variant="body2" sx={{ color: o.correct ? 'success.main' : o.selected ? 'error.main' : 'text.secondary', fontWeight: o.selected || o.correct ? 700 : 400 }}>
              {o.selected ? '◉' : '○'} {o.text} {o.correct ? '✓ correct' : ''}
            </Typography>
          ))}
        </Stack>
      )}
      {a.textAnswer && <Typography sx={{ whiteSpace: 'pre-wrap', p: 1.5, borderRadius: 2, bgcolor: 'background.default' }}>{a.textAnswer}</Typography>}
      {a.codeAnswer && (
        <Box component="pre" sx={{ m: 0, p: 1.5, borderRadius: 2, bgcolor: 'background.default', overflowX: 'auto', fontFamily: '"JetBrains Mono", monospace', fontSize: 13 }}>
          {a.codeAnswer}
        </Box>
      )}
      {a.fileName && (
        <Button size="small" startIcon={<DownloadRounded />} onClick={() => attemptApi.downloadFile(attemptId, a.questionId, a.fileName!).catch((e) => notify.error(errorMessage(e)))}>
          {a.fileName} ({formatBytes(a.fileSize)})
        </Button>
      )}
      {a.evaluatorComment && <Typography variant="body2" sx={{ mt: 1 }} color="info.main">Comment: {a.evaluatorComment}</Typography>}
    </Box>
  );
}
