import {
  AccessTimeRounded, AttachFileRounded, EventRounded, InfoOutlined, PlayArrowRounded, QuizRounded, ReplayRounded,
  SaveRounded, VisibilityRounded, WarningAmberRounded,
} from '@mui/icons-material';
import { Alert, Box, Button, Checkbox, FormControlLabel, Stack, Typography } from '@mui/material';
import { useState, type ReactNode } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { candidateApi } from '../../api/services';
import { ErrorState } from '../../components/common/Feedback';
import { GlassCard } from '../../components/common/GlassCard';
import { SectionLoader } from '../../components/common/Loaders';
import { useNotify } from '../../components/common/NotificationProvider';
import { PageHeader } from '../../components/common/PageHeader';
import { StatusChip } from '../../components/common/StatusChip';
import { useAsync } from '../../hooks/useAsync';
import { errorMessage } from '../../utils/errors';
import { formatBytes, formatDateTime, formatDuration, fromNow } from '../../utils/format';
import { STATE_LABEL, VIOLATION_ACTION_LABEL } from '../../utils/labels';

export default function InstructionsPage() {
  const { assignmentId } = useParams();
  const id = Number(assignmentId);
  const navigate = useNavigate();
  const notify = useNotify();
  const { data, loading, error, reload } = useAsync(() => candidateApi.instructions(id), [id]);
  const [agreed, setAgreed] = useState(false);
  const [starting, setStarting] = useState(false);

  if (loading && !data) return <SectionLoader rows={6} />;
  if (error || !data) return <ErrorState error={error} onRetry={reload} />;
  const s = data.summary;
  const resume = s.state === 'IN_PROGRESS';

  const start = async () => {
    setStarting(true);
    try {
      const attempt = await candidateApi.start(id);
      navigate(`/candidate/attempts/${attempt.attemptId}/take`, { replace: true });
    } catch (e) {
      notify.error(errorMessage(e));
      reload();
    } finally {
      setStarting(false);
    }
  };

  return (
    <>
      <PageHeader backTo="/candidate" overline="Before you begin" title={s.title}
        subtitle={s.description} actions={<StatusChip status={s.state} label={STATE_LABEL[s.state]} />} />
      <Box sx={{ display: 'grid', gap: 2.5, gridTemplateColumns: { xs: '1fr', lg: '1.5fr 1fr' }, alignItems: 'start' }}>
        <Stack spacing={2.5}>
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: 'repeat(2, 1fr)', xl: 'repeat(4, 1fr)' } }}>
            <Fact icon={<AccessTimeRounded />} label="Time limit" value={formatDuration(s.durationMinutes)} />
            <Fact icon={<QuizRounded />} label="Questions" value={`${s.questionCount} · ${s.totalMarks} marks`} />
            <Fact icon={<ReplayRounded />} label="Attempts" value={`${s.attemptsUsed} of ${s.maxAttempts} used`} />
            <Fact icon={<EventRounded />} label="Deadline" value={s.deadline ? fromNow(s.deadline) : 'None'} />
          </Box>
          <GlassCard title="Instructions">
            <Typography sx={{ whiteSpace: 'pre-wrap', lineHeight: 1.75 }} color={data.instructions ? 'text.primary' : 'text.secondary'}>
              {data.instructions || 'No special instructions. Read each question carefully.'}
            </Typography>
            {data.evaluationCriteria && (
              <>
                <Typography variant="subtitle2" sx={{ mt: 2.5 }}>How you'll be evaluated</Typography>
                <Typography sx={{ whiteSpace: 'pre-wrap' }} color="text.secondary">{data.evaluationCriteria}</Typography>
              </>
            )}
          </GlassCard>
          {data.attachments.length > 0 && (
            <GlassCard title="Reference materials">
              <Stack spacing={1}>
                {data.attachments.map((att) => (
                  <Button key={att.id} startIcon={<AttachFileRounded />} sx={{ justifyContent: 'flex-start' }}
                    onClick={() => candidateApi.downloadAttachment(id, att).catch((e) => notify.error(errorMessage(e)))}>
                    {att.originalName} ({formatBytes(att.sizeBytes)})
                  </Button>
                ))}
              </Stack>
            </GlassCard>
          )}
        </Stack>
        <Stack spacing={2.5}>
          <GlassCard title="How it works" glow>
            <Stack spacing={1.5}>
              <Rule icon={<AccessTimeRounded />} text={`The ${formatDuration(s.durationMinutes)} timer starts when you press Start and keeps running on the server even if you close the page.`} />
              <Rule icon={<SaveRounded />} text="Answers save automatically. If you refresh or lose connection, your progress is restored." />
              <Rule icon={<WarningAmberRounded />} text="When time runs out, your saved answers are submitted automatically. Late submissions are not accepted." />
              {data.monitoringEnabled && (
                <Rule icon={<VisibilityRounded />} text={`Switching tabs or leaving the window is recorded. After ${data.maxViolations} times: ${VIOLATION_ACTION_LABEL[data.violationAction].toLowerCase()}.`} />
              )}
              {s.deadline && <Rule icon={<EventRounded />} text={`Must be finished by ${formatDateTime(s.deadline)} (your local time).`} />}
            </Stack>
          </GlassCard>
          {data.monitoringEnabled && (
            <Alert severity="info" icon={<InfoOutlined />}>
              Only this tab's visibility and focus are recorded — nothing about your other apps or files. Occasional events
              (e.g. a system pop-up) are reviewed by a person and are not treated as cheating on their own.
            </Alert>
          )}
          <GlassCard>
            {data.canStart ? (
              <Stack spacing={2}>
                {!resume && (
                  <FormControlLabel control={<Checkbox checked={agreed} onChange={(e) => setAgreed(e.target.checked)} />}
                    label="I have read the instructions and I'm ready to start." />
                )}
                <Button variant="contained" size="large" startIcon={<PlayArrowRounded />} disabled={(!agreed && !resume) || starting} onClick={start}>
                  {starting ? 'Starting…' : resume ? 'Resume assessment' : 'Start assessment'}
                </Button>
              </Stack>
            ) : (
              <Stack spacing={1.5}>
                <Alert severity={s.state === 'UPCOMING' ? 'info' : 'warning'}>{data.unavailableReason ?? 'This assessment is not available.'}</Alert>
                {s.state === 'UPCOMING' && s.startAt && <Typography>Opens {formatDateTime(s.startAt)} ({fromNow(s.startAt)}).</Typography>}
                {s.latestAttemptId && (
                  <Button variant="outlined" onClick={() => navigate(`/candidate/attempts/${s.latestAttemptId}/result`)}>View my submission</Button>
                )}
              </Stack>
            )}
          </GlassCard>
        </Stack>
      </Box>
    </>
  );
}

function Fact({ icon, label, value }: { icon: ReactNode; label: string; value: string }) {
  return (
    <GlassCard>
      <Stack direction="row" spacing={1.5} alignItems="center">
        <Box sx={{ color: 'primary.light', display: 'flex' }}>{icon}</Box>
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="caption" color="text.secondary">{label}</Typography>
          <Typography fontWeight={700} sx={{ lineHeight: 1.3 }}>{value}</Typography>
        </Box>
      </Stack>
    </GlassCard>
  );
}

function Rule({ icon, text }: { icon: ReactNode; text: string }) {
  return (
    <Stack direction="row" spacing={1.5} alignItems="flex-start">
      <Box sx={{ color: 'primary.light', mt: 0.25, display: 'flex' }}>{icon}</Box>
      <Typography variant="body2">{text}</Typography>
    </Stack>
  );
}
