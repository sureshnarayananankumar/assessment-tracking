import {
  BookmarkAddedRounded, BookmarkBorderRounded, ChevronLeftRounded, ChevronRightRounded, CloudDoneRounded,
  CloudOffRounded, CloudSyncRounded, ErrorOutlineRounded, SendRounded, VisibilityOffRounded,
} from '@mui/icons-material';
import {
  Alert, AppBar, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle,
  LinearProgress, Stack, Toolbar, Tooltip, Typography, useMediaQuery,
} from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { attemptApi } from '../../api/services';
import type { ActivityEventInput, AnswerInput, AttemptView } from '../../api/types';
import { CountdownTimer } from '../../components/assessment/CountdownTimer';
import { emptyAnswer, isAnswered, QuestionView, type LocalAnswer } from '../../components/assessment/QuestionView';
import { BrandLogo } from '../../components/common/BrandLogo';
import { ErrorState } from '../../components/common/Feedback';
import { FullPageLoader } from '../../components/common/Loaders';
import { useNotify } from '../../components/common/NotificationProvider';
import { ThemeModeToggle } from '../../components/layout/ThemeControls';
import { useActivityMonitor } from '../../hooks/useActivityMonitor';
import { useCountdown } from '../../hooks/useCountdown';
import { LiquidBackground } from '../../theme/LiquidBackground';
import { errorCode, errorMessage, errorStatus } from '../../utils/errors';
import { formatTime } from '../../utils/format';
import { VIOLATION_ACTION_LABEL } from '../../utils/labels';

type SaveState = 'idle' | 'pending' | 'saving' | 'saved' | 'offline' | 'error';
interface BackupEntry { answer: LocalAnswer; updatedAt: number }
const CLOSED_CODES = ['ATTEMPT_EXPIRED', 'ATTEMPT_CLOSED', 'SUBMISSION_LATE'];

function readBackup(key: string): Record<string, BackupEntry> {
  try {
    return JSON.parse(localStorage.getItem(key) ?? '{}') as Record<string, BackupEntry>;
  } catch {
    return {};
  }
}

function writeBackup(key: string, data: Record<string, BackupEntry>) {
  try {
    localStorage.setItem(key, JSON.stringify(data));
  } catch {
    /* storage full or disabled: the server copy is still authoritative */
  }
}

function toInput(questionId: number, a: LocalAnswer): AnswerInput {
  return {
    questionId,
    selectedOptionIds: a.selectedOptionIds,
    textAnswer: a.textAnswer ?? null,
    codeAnswer: a.codeAnswer ?? null,
    codeLanguage: a.codeLanguage ?? null,
  };
}

/**
 * Distraction-free timed assessment. The server owns the clock: this page only mirrors the
 * server deadline, autosaves continuously, and submits when the server-synchronised timer ends.
 */
export default function TakeAssessmentPage() {
  const { attemptId } = useParams();
  const id = Number(attemptId);
  const navigate = useNavigate();
  const notify = useNotify();
  const theme = useTheme();
  const desktop = useMediaQuery(theme.breakpoints.up('md'));
  const backupKey = `assesshub.attempt.${id}`;

  const [view, setView] = useState<AttemptView | null>(null);
  const [loadError, setLoadError] = useState<unknown>(null);
  const [answers, setAnswers] = useState<Record<number, LocalAnswer>>({});
  const [current, setCurrent] = useState(0);
  const [marked, setMarked] = useState<Set<number>>(new Set());
  const [saveState, setSaveState] = useState<SaveState>('idle');
  const [lastSavedAt, setLastSavedAt] = useState<string | undefined>();
  const [counts, setCounts] = useState({ tab: 0, focus: 0, violations: 0, flagged: false });
  const [warning, setWarning] = useState<string | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const answersRef = useRef(answers);
  const dirty = useRef(new Set<number>());
  const saving = useRef(false);
  const closed = useRef(false);
  const saveTimer = useRef<number>();
  const events = useRef<ActivityEventInput[]>([]);
  const eventTimer = useRef<number>();

  // ------------------------------------------------------------ closing / navigation
  const finish = useCallback((message?: string) => {
    if (closed.current) return;
    closed.current = true;
    window.clearTimeout(saveTimer.current);
    window.clearTimeout(eventTimer.current);
    try { localStorage.removeItem(backupKey); } catch { /* ignore */ }
    navigate(`/candidate/attempts/${id}/submitted`, { replace: true, state: { message } });
  }, [backupKey, id, navigate]);

  // ------------------------------------------------------------ autosave
  const flush = useCallback(async (): Promise<boolean> => {
    if (closed.current) return true;
    if (dirty.current.size === 0) return true;
    if (saving.current) {
      window.clearTimeout(saveTimer.current);
      saveTimer.current = window.setTimeout(() => void flush(), 700);
      return false;
    }
    const ids = [...dirty.current];
    dirty.current.clear();
    saving.current = true;
    setSaveState('saving');
    try {
      const res = await attemptApi.saveAnswers(id, ids.map((q) => toInput(q, answersRef.current[q] ?? emptyAnswer())));
      setLastSavedAt(res.savedAt);
      const backup = readBackup(backupKey);
      ids.filter((q) => !dirty.current.has(q)).forEach((q) => delete backup[q]);
      writeBackup(backupKey, backup);
      setSaveState(dirty.current.size ? 'pending' : 'saved');
      return true;
    } catch (e) {
      ids.forEach((q) => dirty.current.add(q));
      if (CLOSED_CODES.includes(errorCode(e) ?? '')) {
        finish(errorMessage(e));
        return false;
      }
      setSaveState(errorStatus(e) === undefined ? 'offline' : 'error');
      window.clearTimeout(saveTimer.current);
      saveTimer.current = window.setTimeout(() => void flush(), 5000);
      return false;
    } finally {
      saving.current = false;
    }
  }, [backupKey, finish, id]);

  const scheduleSave = useCallback((delay = 1200) => {
    window.clearTimeout(saveTimer.current);
    saveTimer.current = window.setTimeout(() => void flush(), delay);
  }, [flush]);

  const onAnswer = (questionId: number, answer: LocalAnswer) => {
    if (closed.current) return;
    answersRef.current = { ...answersRef.current, [questionId]: answer };
    setAnswers(answersRef.current);
    dirty.current.add(questionId);
    const backup = readBackup(backupKey);
    backup[questionId] = { answer: { ...answer, fileName: undefined, fileSize: undefined }, updatedAt: Date.now() };
    writeBackup(backupKey, backup);
    setSaveState('pending');
    scheduleSave();
  };

  // ------------------------------------------------------------ load + restore
  useEffect(() => {
    let active = true;
    attemptApi.get(id)
      .then((v) => {
        if (!active) return;
        if (v.status !== 'IN_PROGRESS') {
          finish(v.submissionReason);
          return;
        }
        const restored: Record<number, LocalAnswer> = {};
        v.answers.forEach((a) => {
          restored[a.questionId] = {
            selectedOptionIds: a.selectedOptionIds ?? [], textAnswer: a.textAnswer, codeAnswer: a.codeAnswer,
            codeLanguage: a.codeLanguage, fileName: a.fileName, fileSize: a.fileSize,
          };
        });
        // Merge answers typed while offline / before a crash that never reached the server.
        const backup = readBackup(backupKey);
        Object.entries(backup).forEach(([qid, entry]) => {
          const q = Number(qid);
          const serverSaved = v.answers.find((a) => a.questionId === q)?.savedAt;
          if (!v.questions.some((x) => x.questionId === q)) return;
          if (!serverSaved || entry.updatedAt > new Date(serverSaved).getTime()) {
            restored[q] = { ...restored[q], ...entry.answer, fileName: restored[q]?.fileName, fileSize: restored[q]?.fileSize };
            dirty.current.add(q);
          }
        });
        answersRef.current = restored;
        setAnswers(restored);
        setView(v);
        setLastSavedAt(v.lastSavedAt);
        setCounts({ tab: v.tabSwitchCount, focus: v.focusLossCount, violations: v.violationCount, flagged: false });
        if (dirty.current.size) {
          setSaveState('pending');
          scheduleSave(300);
          notify.info('Restored answers that had not been saved yet.');
        }
      })
      .catch((e) => active && setLoadError(e));
    return () => { active = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  // periodic safety save, online recovery, unload protection
  useEffect(() => {
    const interval = window.setInterval(() => void flush(), 15000);
    const online = () => void flush();
    const beforeUnload = (e: BeforeUnloadEvent) => {
      if (!closed.current && dirty.current.size > 0) {
        e.preventDefault();
        e.returnValue = '';
      }
    };
    window.addEventListener('online', online);
    window.addEventListener('beforeunload', beforeUnload);
    return () => {
      window.clearInterval(interval);
      window.removeEventListener('online', online);
      window.removeEventListener('beforeunload', beforeUnload);
    };
  }, [flush]);

  useEffect(() => () => {
    window.clearTimeout(saveTimer.current);
    window.clearTimeout(eventTimer.current);
  }, []);

  // ------------------------------------------------------------ focus monitoring
  const sendEvents = useCallback(async () => {
    if (closed.current || events.current.length === 0) return;
    const batch = events.current.splice(0, 100);
    try {
      const res = await attemptApi.activity(id, batch);
      setCounts({ tab: res.tabSwitchCount, focus: res.focusLossCount, violations: res.violationCount, flagged: res.flagged });
      if (res.status !== 'IN_PROGRESS') {
        finish(res.message ?? 'Your assessment was submitted automatically.');
        return;
      }
      if (res.newViolation && res.message) {
        setWarning(res.message);
        events.current.push({ type: 'WARNING_SHOWN', occurredAt: new Date().toISOString(), details: `violations=${res.violationCount}` });
      }
    } catch (e) {
      if (CLOSED_CODES.includes(errorCode(e) ?? '')) {
        finish(errorMessage(e));
        return;
      }
      events.current.unshift(...batch); // retry later
      window.clearTimeout(eventTimer.current);
      eventTimer.current = window.setTimeout(() => void sendEvents(), 5000);
    }
  }, [finish, id]);

  useActivityMonitor({
    enabled: Boolean(view?.monitoringEnabled),
    onEvent: (event) => {
      if (closed.current) return;
      events.current.push(event);
      if (event.type === 'TAB_HIDDEN' || event.type === 'WINDOW_BLUR') void flush();
      window.clearTimeout(eventTimer.current);
      eventTimer.current = window.setTimeout(() => void sendEvents(), 400);
    },
  });

  // ------------------------------------------------------------ timer & submission
  const submit = useCallback(async (auto: boolean) => {
    if (closed.current) return;
    setSubmitting(true);
    // Drain pending autosaves (including one already in flight) before the final submit.
    for (let i = 0; i < 20 && !closed.current && (saving.current || dirty.current.size > 0); i++) {
      if (saving.current) await new Promise((r) => setTimeout(r, 250));
      else if (!(await flush())) break;
    }
    if (closed.current) return;
    try {
      const res = await attemptApi.submit(id);
      finish(res.message);
    } catch (e) {
      if (CLOSED_CODES.includes(errorCode(e) ?? '')) finish(errorMessage(e));
      else {
        notify.error(errorMessage(e));
        setSubmitting(false);
        if (auto) window.setTimeout(() => void submit(true), 3000);
      }
    }
  }, [finish, flush, id, notify]);

  const { secondsLeft } = useCountdown({
    deadlineAt: view?.deadlineAt ?? new Date().toISOString(),
    serverTime: view?.serverTime ?? new Date().toISOString(),
    enabled: Boolean(view),
    warnings: [300, 60],
    onWarning: (s) => notify.warning(s > 60 ? '5 minutes remaining — your answers are being saved.' : 'Less than 1 minute left! The assessment will submit automatically.'),
    onExpire: () => void submit(true),
  });

  // ------------------------------------------------------------ render
  if (loadError) {
    return (
      <Box sx={{ p: 3, maxWidth: 720, mx: 'auto' }}>
        <LiquidBackground calm />
        <ErrorState error={loadError} />
        <Button variant="outlined" onClick={() => navigate('/candidate')}>Back to my assessments</Button>
      </Box>
    );
  }
  if (!view) return <FullPageLoader label="Loading your assessment…" />;

  const questions = view.questions;
  const q = questions[Math.min(current, questions.length - 1)];
  const answeredCount = questions.filter((x) => isAnswered(answers[x.questionId])).length;
  const unanswered = questions.filter((x) => !isAnswered(answers[x.questionId]));

  const palette = (
    <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(42px, 1fr))', gap: 1 }}>
      {questions.map((x, i) => {
        const done = isAnswered(answers[x.questionId]);
        const isCurrent = i === current;
        const flaggedQ = marked.has(x.questionId);
        return (
          <Tooltip key={x.questionId} title={`${done ? 'Answered' : 'Not answered'}${flaggedQ ? ' · marked for review' : ''}`}>
            <Box
              component="button"
              type="button"
              aria-label={`Question ${i + 1}${done ? ', answered' : ''}${flaggedQ ? ', marked for review' : ''}`}
              aria-current={isCurrent ? 'step' : undefined}
              onClick={() => setCurrent(i)}
              sx={{
                position: 'relative',
                height: 42,
                borderRadius: 2.5,
                cursor: 'pointer',
                font: 'inherit',
                fontWeight: 700,
                color: done ? '#fff' : 'text.primary',
                border: `1.5px solid ${isCurrent ? theme.palette.primary.light : theme.palette.divider}`,
                background: done ? theme.brand.gradient : alpha(theme.palette.text.primary, 0.04),
                boxShadow: isCurrent ? `0 0 0 3px ${alpha(theme.palette.primary.main, 0.3)}` : 'none',
                transition: 'transform .12s ease',
                '&:hover': { transform: 'translateY(-1px)' },
              }}
            >
              {i + 1}
              {flaggedQ && <Box sx={{ position: 'absolute', top: 3, right: 4, width: 7, height: 7, borderRadius: '50%', bgcolor: 'warning.main' }} />}
            </Box>
          </Tooltip>
        );
      })}
    </Box>
  );

  const saveIndicator = {
    idle: { icon: <CloudDoneRounded fontSize="small" />, label: 'All changes saved', color: 'text.secondary' },
    saved: { icon: <CloudDoneRounded fontSize="small" />, label: lastSavedAt ? `Saved ${formatTime(lastSavedAt)}` : 'Saved', color: 'success.main' },
    pending: { icon: <CloudSyncRounded fontSize="small" />, label: 'Unsaved changes…', color: 'text.secondary' },
    saving: { icon: <CloudSyncRounded fontSize="small" />, label: 'Saving…', color: 'info.main' },
    offline: { icon: <CloudOffRounded fontSize="small" />, label: 'Offline — kept on this device, retrying', color: 'warning.main' },
    error: { icon: <ErrorOutlineRounded fontSize="small" />, label: 'Save failed — retrying', color: 'error.main' },
  }[saveState];

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <LiquidBackground calm />
      <AppBar position="sticky">
        <Toolbar sx={{ gap: 1.5, flexWrap: 'wrap', py: 1 }}>
          {desktop && <BrandLogo size={30} withText={false} />}
          <Box sx={{ flex: 1, minWidth: 160 }}>
            <Typography fontWeight={750} noWrap>{view.title}</Typography>
            <Stack direction="row" spacing={0.75} alignItems="center" sx={{ color: saveIndicator.color }}>
              {saveIndicator.icon}
              <Typography variant="caption">{saveIndicator.label}</Typography>
            </Stack>
          </Box>
          {view.monitoringEnabled && (
            <Tooltip title="Times you left this tab or window (recorded for review)">
              <Chip icon={<VisibilityOffRounded />} size="small" label={`${counts.violations}/${view.maxViolations}`}
                color={counts.violations >= view.warningThreshold ? 'warning' : 'default'} variant="outlined" />
            </Tooltip>
          )}
          <CountdownTimer secondsLeft={secondsLeft} />
          <ThemeModeToggle />
          <Button variant="contained" startIcon={<SendRounded />} onClick={() => setConfirmOpen(true)} disabled={submitting}>
            {submitting ? 'Submitting…' : 'Submit'}
          </Button>
        </Toolbar>
        <LinearProgress variant="determinate" value={(answeredCount / Math.max(1, questions.length)) * 100} sx={{ borderRadius: 0, height: 3 }} />
      </AppBar>

      <Box component="main" sx={{ flex: 1, width: '100%', maxWidth: 1280, mx: 'auto', p: { xs: 2, md: 3 }, display: 'grid', gap: 2.5, gridTemplateColumns: { xs: '1fr', md: '260px 1fr' }, alignItems: 'start' }}>
        <Card sx={{ position: { md: 'sticky' }, top: { md: 96 } }}>
          <CardContent>
            <Typography variant="subtitle2" sx={{ mb: 0.5 }}>Questions</Typography>
            <Typography variant="caption" color="text.secondary" component="div" sx={{ mb: 1.5 }}>
              {answeredCount} of {questions.length} answered{marked.size ? ` · ${marked.size} marked` : ''}
            </Typography>
            {palette}
          </CardContent>
        </Card>

        <Stack spacing={2}>
          {saveState === 'offline' && (
            <Alert severity="warning">You appear to be offline. Keep working — answers are stored on this device and will sync when the connection returns. The timer keeps running on the server.</Alert>
          )}
          <Card>
            <CardContent sx={{ p: { xs: 2.5, md: 4 } }}>
              <QuestionView
                key={q.questionId}
                question={q}
                answer={answers[q.questionId] ?? emptyAnswer()}
                onChange={(a) => onAnswer(q.questionId, a)}
                disabled={submitting}
                onUpload={async (file) => {
                  try {
                    const saved = await attemptApi.uploadFile(id, q.questionId, file);
                    const next = { ...(answersRef.current[q.questionId] ?? emptyAnswer()), fileName: saved.fileName, fileSize: saved.fileSize };
                    answersRef.current = { ...answersRef.current, [q.questionId]: next };
                    setAnswers(answersRef.current);
                    setLastSavedAt(saved.savedAt ?? undefined);
                    setSaveState('saved');
                    notify.success('File uploaded');
                  } catch (e) {
                    if (CLOSED_CODES.includes(errorCode(e) ?? '')) finish(errorMessage(e));
                    else notify.error(errorMessage(e));
                  }
                }}
              />
            </CardContent>
          </Card>
          <Stack direction="row" spacing={1.5} justifyContent="space-between" alignItems="center">
            <Button variant="outlined" startIcon={<ChevronLeftRounded />} disabled={current === 0} onClick={() => setCurrent((c) => c - 1)}>Previous</Button>
            <Button
              startIcon={marked.has(q.questionId) ? <BookmarkAddedRounded /> : <BookmarkBorderRounded />}
              color={marked.has(q.questionId) ? 'warning' : 'inherit'}
              onClick={() => setMarked((m) => {
                const next = new Set(m);
                if (next.has(q.questionId)) next.delete(q.questionId); else next.add(q.questionId);
                return next;
              })}
            >
              {desktop ? (marked.has(q.questionId) ? 'Marked for review' : 'Mark for review') : 'Mark'}
            </Button>
            {current < questions.length - 1
              ? <Button variant="contained" endIcon={<ChevronRightRounded />} onClick={() => setCurrent((c) => c + 1)}>Next</Button>
              : <Button variant="contained" endIcon={<SendRounded />} onClick={() => setConfirmOpen(true)}>Finish</Button>}
          </Stack>
        </Stack>
      </Box>

      {/* Focus-loss warning */}
      <Dialog open={Boolean(warning)} onClose={() => setWarning(null)} maxWidth="xs" fullWidth>
        <DialogTitle>Please stay on the assessment</DialogTitle>
        <DialogContent>
          <Stack spacing={1.5}>
            <Typography>{warning}</Typography>
            <Typography variant="body2" color="text.secondary">
              Recorded {counts.violations} of {view.maxViolations}. At the limit: {VIOLATION_ACTION_LABEL[view.violationAction].toLowerCase()}.
              Events are reviewed by a person — an occasional system pop-up won't count against you on its own.
            </Typography>
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button variant="contained" onClick={() => setWarning(null)} autoFocus>Return to assessment</Button>
        </DialogActions>
      </Dialog>

      {/* Submit confirmation */}
      <Dialog open={confirmOpen} onClose={() => !submitting && setConfirmOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Submit your assessment?</DialogTitle>
        <DialogContent>
          <Stack spacing={1.5}>
            <Typography>You answered <b>{answeredCount}</b> of <b>{questions.length}</b> questions. You cannot change answers after submitting.</Typography>
            {unanswered.length > 0 && (
              <Alert severity="warning">
                Unanswered: {unanswered.map((x) => x.index).join(', ')}
              </Alert>
            )}
            {marked.size > 0 && (
              <Alert severity="info">Marked for review: {questions.filter((x) => marked.has(x.questionId)).map((x) => x.index).join(', ')}</Alert>
            )}
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button onClick={() => setConfirmOpen(false)} disabled={submitting}>Keep working</Button>
          <Button variant="contained" startIcon={<SendRounded />} disabled={submitting} onClick={() => void submit(false)}>
            {submitting ? 'Submitting…' : 'Submit now'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
