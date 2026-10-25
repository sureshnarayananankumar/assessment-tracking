import { CancelRounded, CheckCircleRounded, HourglassTopRounded } from '@mui/icons-material';
import { Alert, Box, Chip, CircularProgress, Stack, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useParams } from 'react-router-dom';
import { attemptApi } from '../../api/services';
import type { CandidateQuestionResult } from '../../api/types';
import { ErrorState } from '../../components/common/Feedback';
import { GlassCard } from '../../components/common/GlassCard';
import { SectionLoader } from '../../components/common/Loaders';
import { PageHeader } from '../../components/common/PageHeader';
import { useAsync } from '../../hooks/useAsync';
import { formatDateTime, formatPercent, formatScore } from '../../utils/format';
import { QUESTION_TYPE_LABEL } from '../../utils/labels';

export default function CandidateResultPage() {
  const { attemptId } = useParams();
  const id = Number(attemptId);
  const theme = useTheme();
  const { data, loading, error, reload } = useAsync(() => attemptApi.results(id), [id]);

  if (loading && !data) return <SectionLoader rows={6} />;
  if (error || !data) return <ErrorState error={error} onRetry={reload} />;

  if (!data.released) {
    return (
      <>
        <PageHeader backTo="/candidate" overline="Result" title={data.assignmentTitle} />
        <GlassCard>
          <Stack alignItems="center" spacing={2} sx={{ py: 5, textAlign: 'center' }}>
            <HourglassTopRounded color="primary" sx={{ fontSize: 48 }} />
            <Typography variant="h6">Your result is not available yet</Typography>
            <Typography color="text.secondary" sx={{ maxWidth: 480 }}>{data.message}</Typography>
            <Typography variant="body2" color="text.secondary">Submitted {formatDateTime(data.submittedAt)}</Typography>
          </Stack>
        </GlassCard>
      </>
    );
  }

  const pct = Number(data.percentage ?? 0);
  const passed = data.passed;
  return (
    <>
      <PageHeader backTo="/candidate" overline="Result" title={data.assignmentTitle} subtitle={`Submitted ${formatDateTime(data.submittedAt)}`} />
      <Box sx={{ display: 'grid', gap: 2.5, gridTemplateColumns: { xs: '1fr', md: '340px 1fr' }, alignItems: 'start' }}>
        <GlassCard glow>
          <Stack alignItems="center" spacing={2} sx={{ py: 2 }}>
            <Box sx={{ position: 'relative', width: 170, height: 170 }}>
              <CircularProgress variant="determinate" value={100} size={170} thickness={4} sx={{ color: 'action.hover', position: 'absolute' }} />
              <CircularProgress variant="determinate" value={pct} size={170} thickness={4}
                sx={{ position: 'absolute', color: passed ? 'success.main' : passed === false ? 'error.main' : 'primary.main', '& circle': { strokeLinecap: 'round' } }} />
              <Stack sx={{ position: 'absolute', inset: 0 }} alignItems="center" justifyContent="center">
                <Typography variant="h3" sx={{ fontWeight: 900, background: theme.brand.gradient, WebkitBackgroundClip: 'text', backgroundClip: 'text', color: 'transparent' }}>
                  {formatPercent(pct)}
                </Typography>
                <Typography variant="body2" color="text.secondary">{formatScore(data.totalScore, data.maxScore)}</Typography>
              </Stack>
            </Box>
            {passed !== null && passed !== undefined && (
              <Chip icon={passed ? <CheckCircleRounded /> : <CancelRounded />} color={passed ? 'success' : 'error'}
                label={passed ? 'Passed' : 'Not passed'} sx={{ fontSize: 15, py: 2, px: 1 }} />
            )}
            <Typography variant="body2" color="text.secondary">Pass mark: {data.passingMarks}</Typography>
          </Stack>
        </GlassCard>
        <Stack spacing={2.5}>
          {data.feedback && (
            <GlassCard title="Feedback from your assessor">
              <Typography sx={{ whiteSpace: 'pre-wrap' }}>{data.feedback}</Typography>
            </GlassCard>
          )}
          {!data.showCorrectAnswers && <Alert severity="info">Correct answers are not shown for this assessment.</Alert>}
          <GlassCard title="Question breakdown">
            <Stack spacing={1.5}>
              {data.questions.map((q) => <QuestionResult key={q.questionId} q={q} />)}
            </Stack>
          </GlassCard>
        </Stack>
      </Box>
    </>
  );
}

function QuestionResult({ q }: { q: CandidateQuestionResult }) {
  return (
    <Box sx={{ p: 2, borderRadius: 3, border: 1, borderColor: 'divider', bgcolor: 'action.hover' }}>
      <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
        <Chip size="small" color="primary" label={`Q${q.index}`} />
        <Typography fontWeight={700} sx={{ flex: 1, minWidth: 150 }}>{q.title}</Typography>
        <Chip size="small" variant="outlined" label={QUESTION_TYPE_LABEL[q.type]} />
        <Chip size="small" color={q.correct ? 'success' : q.correct === false ? 'error' : 'default'} label={formatScore(q.marksAwarded, q.maxMarks)} />
      </Stack>
      {q.options && (
        <Stack spacing={0.5} sx={{ mt: 1.25 }}>
          {q.options.map((o) => (
            <Typography key={o.id} variant="body2" sx={{ color: o.correct ? 'success.main' : o.selected && o.correct === false ? 'error.main' : 'text.secondary', fontWeight: o.selected ? 700 : 400 }}>
              {o.selected ? '◉' : '○'} {o.text}{o.correct ? '  ✓' : ''}
            </Typography>
          ))}
        </Stack>
      )}
      {q.textAnswer && <Typography variant="body2" sx={{ mt: 1, whiteSpace: 'pre-wrap' }}>{q.textAnswer}</Typography>}
      {q.codeAnswer && <Box component="pre" sx={{ mt: 1, p: 1.5, borderRadius: 2, bgcolor: 'background.default', overflowX: 'auto', fontFamily: '"JetBrains Mono", monospace', fontSize: 13 }}>{q.codeAnswer}</Box>}
      {q.fileName && <Typography variant="body2" sx={{ mt: 1 }}>File: {q.fileName}</Typography>}
      {q.evaluatorComment && <Alert severity="info" sx={{ mt: 1.25 }}>{q.evaluatorComment}</Alert>}
    </Box>
  );
}
