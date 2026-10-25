import { CheckCircleRounded, SaveRounded } from '@mui/icons-material';
import {
  Alert, Box, Button, Checkbox, FormControlLabel, InputAdornment, Stack, TextField, Typography,
} from '@mui/material';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { attemptApi } from '../../api/services';
import type { AttemptReview } from '../../api/types';
import { ErrorState } from '../../components/common/Feedback';
import { GlassCard } from '../../components/common/GlassCard';
import { SectionLoader } from '../../components/common/Loaders';
import { useNotify } from '../../components/common/NotificationProvider';
import { PageHeader } from '../../components/common/PageHeader';
import { useAsync } from '../../hooks/useAsync';
import { errorMessage } from '../../utils/errors';
import { formatScore } from '../../utils/format';
import { AnswerSummary } from './AttemptDetailPage';

interface Draft {
  marks: string;
  comment: string;
  touched: boolean;
}

/** Manual grading of descriptive, coding and file answers (and optional override of auto-graded marks). */
export default function EvaluationPage() {
  const { attemptId } = useParams();
  const id = Number(attemptId);
  const navigate = useNavigate();
  const notify = useNotify();
  const { data, loading, error, reload, setData } = useAsync(() => attemptApi.review(id), [id]);
  const [drafts, setDrafts] = useState<Record<number, Draft>>({});
  const [feedback, setFeedback] = useState('');
  const [release, setRelease] = useState(false);
  const [saving, setSaving] = useState(false);

  const init = (r: AttemptReview) => {
    const next: Record<number, Draft> = {};
    r.answers.forEach((a) => {
      next[a.answerId] = { marks: a.marksAwarded === null || a.marksAwarded === undefined ? '' : String(a.marksAwarded), comment: a.evaluatorComment ?? '', touched: false };
    });
    setDrafts(next);
    setFeedback(r.result?.feedback ?? '');
  };
  useEffect(() => {
    if (data) init(data);
  }, [data]);

  const pendingCount = useMemo(() => data?.answers.filter((a) => a.gradingStatus === 'PENDING').length ?? 0, [data]);
  const projected = useMemo(() => {
    if (!data) return 0;
    return data.answers.reduce((sum, a) => {
      const d = drafts[a.answerId];
      const v = d && d.marks !== '' ? Number(d.marks) : Number(a.marksAwarded ?? 0);
      return sum + (Number.isFinite(v) ? v : 0);
    }, 0);
  }, [data, drafts]);

  if (loading && !data) return <SectionLoader rows={8} />;
  if (error || !data) return <ErrorState error={error} onRetry={reload} />;

  const invalid = data.answers.some((a) => {
    const d = drafts[a.answerId];
    if (!d || d.marks === '') return false;
    const n = Number(d.marks);
    return !Number.isFinite(n) || n < 0 || n > Number(a.maxMarks);
  });

  const save = async () => {
    const grades = data.answers
      .filter((a) => {
        const d = drafts[a.answerId];
        return d && d.marks !== '' && (d.touched || a.gradingStatus === 'PENDING');
      })
      .map((a) => ({ answerId: a.answerId, marks: Number(drafts[a.answerId].marks), comment: drafts[a.answerId].comment || undefined }));
    setSaving(true);
    try {
      const updated = await attemptApi.grade(id, grades, feedback, release);
      setData(updated);
      notify.success(updated.result?.gradingStatus === 'GRADED' ? 'Grading complete' : 'Grades saved');
    } catch (e) {
      notify.error(errorMessage(e));
    } finally {
      setSaving(false);
    }
  };

  const maxScore = Number(data.result?.maxScore ?? data.answers.reduce((s, a) => s + Number(a.maxMarks), 0));
  const update = (answerId: number, patch: Partial<Draft>) =>
    setDrafts((d) => ({ ...d, [answerId]: { ...d[answerId], ...patch, touched: true } }));

  return (
    <>
      <PageHeader
        backTo={`/attempts/${id}`}
        overline={`Evaluate · ${data.assignmentTitle}`}
        title={data.candidateName}
        subtitle={`Projected score ${formatScore(projected, maxScore)} · pass mark ${data.passingMarks}`}
        actions={
          <Button variant="contained" startIcon={<SaveRounded />} onClick={save} disabled={saving || invalid || data.status === 'IN_PROGRESS'}>
            {saving ? 'Saving…' : 'Save grades'}
          </Button>
        }
      />
      {data.status === 'IN_PROGRESS' && <Alert severity="info" sx={{ mb: 2 }}>The attempt is still in progress. Grading is available after submission.</Alert>}
      {pendingCount === 0 && data.result?.gradingStatus === 'GRADED' && (
        <Alert severity="success" icon={<CheckCircleRounded />} sx={{ mb: 2 }}>All answers are graded. You can still adjust marks or comments.</Alert>
      )}
      <Stack spacing={2}>
        {data.answers.map((a) => {
          const d = drafts[a.answerId] ?? { marks: '', comment: '', touched: false };
          const n = Number(d.marks);
          const err = d.marks !== '' && (!Number.isFinite(n) || n < 0 || n > Number(a.maxMarks));
          const manual = a.gradingStatus === 'PENDING' || a.gradingStatus === 'MANUALLY_GRADED';
          return (
            <GlassCard key={a.answerId} sx={a.gradingStatus === 'PENDING' ? { outline: (t) => `1px solid ${t.palette.warning.main}` } : undefined}>
              <Box sx={{ display: 'grid', gap: 2.5, gridTemplateColumns: { xs: '1fr', md: '1.6fr 1fr' } }}>
                <AnswerSummary a={a} attemptId={id} />
                <Stack spacing={1.5}>
                  {(a.referenceAnswer || a.evaluationGuidelines || a.acceptedAnswers.length > 0) && (
                    <Box sx={{ p: 1.5, borderRadius: 3, border: 1, borderColor: 'divider' }}>
                      <Typography variant="overline" color="text.secondary">Answer key (staff only)</Typography>
                      {a.acceptedAnswers.length > 0 && <Typography variant="body2">Accepted: {a.acceptedAnswers.join(' | ')}</Typography>}
                      {a.referenceAnswer && <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', fontFamily: a.type === 'CODING' ? '"JetBrains Mono", monospace' : undefined }}>{a.referenceAnswer}</Typography>}
                      {a.evaluationGuidelines && <Typography variant="body2" color="text.secondary" sx={{ mt: 1, whiteSpace: 'pre-wrap' }}>{a.evaluationGuidelines}</Typography>}
                    </Box>
                  )}
                  <TextField
                    label={manual ? 'Marks' : 'Marks (auto-graded — override if needed)'}
                    type="number"
                    value={d.marks}
                    onChange={(e) => update(a.answerId, { marks: e.target.value })}
                    error={err}
                    helperText={err ? `Between 0 and ${a.maxMarks}` : a.gradingStatus === 'PENDING' ? 'Required to complete grading' : undefined}
                    inputProps={{ min: 0, max: Number(a.maxMarks), step: 0.5 }}
                    InputProps={{ endAdornment: <InputAdornment position="end">/ {a.maxMarks}</InputAdornment> }}
                  />
                  <Stack direction="row" spacing={1}>
                    <Button size="small" variant="outlined" onClick={() => update(a.answerId, { marks: '0' })}>0</Button>
                    <Button size="small" variant="outlined" onClick={() => update(a.answerId, { marks: String(Number(a.maxMarks) / 2) })}>Half</Button>
                    <Button size="small" variant="outlined" onClick={() => update(a.answerId, { marks: String(a.maxMarks) })}>Full</Button>
                  </Stack>
                  <TextField label="Comment for the candidate" multiline minRows={2} value={d.comment}
                    onChange={(e) => update(a.answerId, { comment: e.target.value })} inputProps={{ maxLength: 5000 }} />
                </Stack>
              </Box>
            </GlassCard>
          );
        })}
        <GlassCard title="Overall feedback">
          <Stack spacing={1.5}>
            <TextField multiline minRows={3} value={feedback} onChange={(e) => setFeedback(e.target.value)} placeholder="Strengths, areas to improve…" inputProps={{ maxLength: 5000 }} />
            <FormControlLabel control={<Checkbox checked={release} onChange={(e) => setRelease(e.target.checked)} />}
              label="Release the result to the candidate once grading is complete (even if the assessment uses manual release)" />
            <Stack direction="row" spacing={1.5} justifyContent="flex-end">
              <Button onClick={() => navigate(`/attempts/${id}`)}>Back</Button>
              <Button variant="contained" startIcon={<SaveRounded />} onClick={save} disabled={saving || invalid || data.status === 'IN_PROGRESS'}>Save grades</Button>
            </Stack>
          </Stack>
        </GlassCard>
      </Stack>
    </>
  );
}
