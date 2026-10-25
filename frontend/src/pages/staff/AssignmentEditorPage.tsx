import { zodResolver } from '@hookform/resolvers/zod';
import {
  AddRounded, ArrowDownwardRounded, ArrowUpwardRounded, DeleteOutlineRounded, InfoOutlined, SaveRounded,
} from '@mui/icons-material';
import {
  Alert, Box, Button, Chip, Divider, FormControlLabel, IconButton, InputAdornment, MenuItem, Stack, Switch, TextField,
  Tooltip, Typography,
} from '@mui/material';
import { useEffect, useState } from 'react';
import { Controller, useForm, type Control } from 'react-hook-form';
import { useNavigate, useParams } from 'react-router-dom';
import { z } from 'zod';
import { assignmentApi } from '../../api/services';
import type { Assignment, AssignmentPayload, Question } from '../../api/types';
import { ErrorState } from '../../components/common/Feedback';
import { GlassCard } from '../../components/common/GlassCard';
import { SectionLoader } from '../../components/common/Loaders';
import { useNotify } from '../../components/common/NotificationProvider';
import { PageHeader } from '../../components/common/PageHeader';
import { errorMessage } from '../../utils/errors';
import { fromLocalInput, toLocalInput } from '../../utils/format';
import { ASSESSMENT_TYPE_LABEL, DIFFICULTY_LABEL, QUESTION_TYPE_LABEL, VIOLATION_ACTION_LABEL } from '../../utils/labels';
import { QuestionPickerDialog } from './QuestionPickerDialog';

const schema = z
  .object({
    title: z.string().trim().min(3, 'At least 3 characters').max(200),
    description: z.string().max(10000).optional(),
    instructions: z.string().max(20000).optional(),
    evaluationCriteria: z.string().max(10000).optional(),
    skillCategory: z.string().max(100).optional(),
    difficulty: z.enum(['BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT']),
    assessmentType: z.enum(['QUIZ', 'CODING', 'DESCRIPTIVE', 'MIXED', 'TAKE_HOME']),
    passingMarks: z.coerce.number().min(0, 'Cannot be negative'),
    startAt: z.string().optional(),
    endAt: z.string().optional(),
    durationMinutes: z.coerce.number().int().min(1, 'At least 1 minute').max(1440, 'At most 24 hours'),
    maxAttempts: z.coerce.number().int().min(1).max(20),
    gracePeriodSeconds: z.coerce.number().int().min(0).max(600),
    shuffleQuestions: z.boolean(),
    shuffleOptions: z.boolean(),
    showCorrectAnswers: z.boolean(),
    resultsReleasePolicy: z.enum(['AFTER_GRADING', 'MANUAL']),
    monitoringEnabled: z.boolean(),
    warningThreshold: z.coerce.number().int().min(1).max(100),
    maxViolations: z.coerce.number().int().min(1).max(100),
    violationAction: z.enum(['WARN_ONLY', 'FLAG_FOR_REVIEW', 'AUTO_SUBMIT']),
  })
  .refine((v) => !v.startAt || !v.endAt || new Date(v.endAt) > new Date(v.startAt), { path: ['endAt'], message: 'Must be after the start' })
  .refine((v) => v.warningThreshold <= v.maxViolations, { path: ['warningThreshold'], message: 'Must not exceed the violation limit' });

type FormValues = z.infer<typeof schema>;

const DEFAULTS: FormValues = {
  title: '', description: '', instructions: '', evaluationCriteria: '', skillCategory: '',
  difficulty: 'INTERMEDIATE', assessmentType: 'MIXED', passingMarks: 0, startAt: '', endAt: '',
  durationMinutes: 60, maxAttempts: 1, gracePeriodSeconds: 30, shuffleQuestions: false, shuffleOptions: false,
  showCorrectAnswers: false, resultsReleasePolicy: 'AFTER_GRADING', monitoringEnabled: true, warningThreshold: 3,
  maxViolations: 5, violationAction: 'FLAG_FOR_REVIEW',
};

interface SelectedQuestion {
  question: Question;
  marks: number;
}

function toForm(a: Assignment): FormValues {
  return {
    title: a.title, description: a.description ?? '', instructions: a.instructions ?? '',
    evaluationCriteria: a.evaluationCriteria ?? '', skillCategory: a.skillCategory ?? '', difficulty: a.difficulty,
    assessmentType: a.assessmentType, passingMarks: Number(a.passingMarks), startAt: toLocalInput(a.startAt),
    endAt: toLocalInput(a.endAt), durationMinutes: a.durationMinutes, maxAttempts: a.maxAttempts,
    gracePeriodSeconds: a.gracePeriodSeconds, shuffleQuestions: a.shuffleQuestions, shuffleOptions: a.shuffleOptions,
    showCorrectAnswers: a.showCorrectAnswers, resultsReleasePolicy: a.resultsReleasePolicy,
    monitoringEnabled: a.monitoringEnabled, warningThreshold: a.warningThreshold, maxViolations: a.maxViolations,
    violationAction: a.violationAction,
  };
}

export default function AssignmentEditorPage() {
  const { id } = useParams();
  const editing = Boolean(id);
  const navigate = useNavigate();
  const notify = useNotify();
  const [loading, setLoading] = useState(editing);
  const [loadError, setLoadError] = useState<unknown>(null);
  const [existing, setExisting] = useState<Assignment | null>(null);
  const [selected, setSelected] = useState<SelectedQuestion[]>([]);
  const [pickerOpen, setPickerOpen] = useState(false);
  const { register, control, handleSubmit, reset, watch, formState } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: DEFAULTS,
    mode: 'onTouched',
  });
  const locked = Boolean(existing?.hasAttempts);
  const totalMarks = selected.reduce((sum, s) => sum + (Number(s.marks) || 0), 0);
  const passingMarks = Number(watch('passingMarks')) || 0;
  const monitoringEnabled = watch('monitoringEnabled');

  useEffect(() => {
    if (!id) return;
    assignmentApi.get(Number(id))
      .then((a) => {
        setExisting(a);
        reset(toForm(a));
        setSelected((a.questions ?? []).map((aq) => ({ question: aq.question, marks: Number(aq.marks) })));
      })
      .catch(setLoadError)
      .finally(() => setLoading(false));
  }, [id, reset]);

  const move = (index: number, delta: number) =>
    setSelected((list) => {
      const next = [...list];
      const [item] = next.splice(index, 1);
      next.splice(index + delta, 0, item);
      return next;
    });

  const onSubmit = async (v: FormValues) => {
    const payload: AssignmentPayload = {
      ...v,
      description: v.description || undefined,
      instructions: v.instructions || undefined,
      evaluationCriteria: v.evaluationCriteria || undefined,
      skillCategory: v.skillCategory || undefined,
      startAt: fromLocalInput(v.startAt),
      endAt: fromLocalInput(v.endAt),
      questions: locked ? undefined : selected.map((s) => ({ questionId: s.question.id, marks: Number(s.marks) })),
    };
    try {
      const saved = editing ? await assignmentApi.update(Number(id), payload) : await assignmentApi.create(payload);
      notify.success(editing ? 'Assessment saved' : 'Assessment created as draft');
      navigate(`/assignments/${saved.id}`);
    } catch (e) {
      notify.error(errorMessage(e));
    }
  };

  if (loading) return <SectionLoader rows={8} />;
  if (loadError) return <ErrorState error={loadError} />;

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate>
      <PageHeader
        backTo={editing ? `/assignments/${id}` : '/assignments'}
        overline={editing ? `Editing · ${existing?.status ?? ''}` : 'New assessment'}
        title={editing ? existing?.title ?? 'Edit assessment' : 'Create assessment'}
        actions={
          <Button type="submit" variant="contained" startIcon={<SaveRounded />} disabled={formState.isSubmitting}>
            {formState.isSubmitting ? 'Saving…' : 'Save'}
          </Button>
        }
      />
      {locked && (
        <Alert severity="info" sx={{ mb: 2 }}>
          Candidates have already started this assessment, so questions and marks are locked. Schedule, instructions and policies can still be changed.
        </Alert>
      )}
      <Box sx={{ display: 'grid', gap: 2.5, gridTemplateColumns: { xs: '1fr', lg: '1.35fr 1fr' }, alignItems: 'start' }}>
        <Stack spacing={2.5}>
          <GlassCard title="Basics" subtitle="Works for any department, role or tech stack">
            <Stack spacing={2}>
              <TextField label="Title" fullWidth required {...register('title')} error={Boolean(formState.errors.title)} helperText={formState.errors.title?.message} />
              <TextField label="Description" fullWidth multiline minRows={2} {...register('description')} />
              <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, 1fr)' } }}>
                <TextField label="Skill category" placeholder="e.g. Frontend, QA, Data" {...register('skillCategory')} />
                <Controller name="difficulty" control={control} render={({ field }) => (
                  <TextField select label="Difficulty" {...field}>
                    {Object.entries(DIFFICULTY_LABEL).map(([v, l]) => <MenuItem key={v} value={v}>{l}</MenuItem>)}
                  </TextField>
                )} />
                <Controller name="assessmentType" control={control} render={({ field }) => (
                  <TextField select label="Assessment type" {...field}>
                    {Object.entries(ASSESSMENT_TYPE_LABEL).map(([v, l]) => <MenuItem key={v} value={v}>{l}</MenuItem>)}
                  </TextField>
                )} />
              </Box>
              <TextField label="Instructions for candidates" fullWidth multiline minRows={4} {...register('instructions')}
                helperText="Shown before the candidate starts and included in the invitation e-mail." />
              <TextField label="Evaluation criteria" fullWidth multiline minRows={2} {...register('evaluationCriteria')} />
            </Stack>
          </GlassCard>

          <GlassCard
            title="Questions"
            subtitle={`${selected.length} question(s) · ${totalMarks} total marks`}
            action={<Button startIcon={<AddRounded />} variant="outlined" onClick={() => setPickerOpen(true)} disabled={locked}>Add from bank</Button>}
          >
            {selected.length === 0 ? (
              <Typography color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
                No questions yet. Add questions from the question bank.
              </Typography>
            ) : (
              <Stack spacing={1.25}>
                {selected.map((s, i) => (
                  <Stack key={s.question.id} direction="row" spacing={1.5} alignItems="center"
                    sx={{ p: 1.5, borderRadius: 3, border: 1, borderColor: 'divider', bgcolor: 'action.hover' }}>
                    <Chip label={i + 1} size="small" color="primary" />
                    <Box sx={{ flex: 1, minWidth: 0 }}>
                      <Typography fontWeight={650} noWrap>{s.question.title}</Typography>
                      <Typography variant="caption" color="text.secondary">{QUESTION_TYPE_LABEL[s.question.type]}</Typography>
                    </Box>
                    <TextField size="small" type="number" label="Marks" value={s.marks} disabled={locked}
                      onChange={(e) => setSelected((list) => list.map((x, j) => (j === i ? { ...x, marks: Number(e.target.value) } : x)))}
                      inputProps={{ min: 0, step: 0.5 }} sx={{ width: 96 }} />
                    <Stack direction="row">
                      <IconButton size="small" disabled={locked || i === 0} onClick={() => move(i, -1)} aria-label="Move up"><ArrowUpwardRounded fontSize="small" /></IconButton>
                      <IconButton size="small" disabled={locked || i === selected.length - 1} onClick={() => move(i, 1)} aria-label="Move down"><ArrowDownwardRounded fontSize="small" /></IconButton>
                      <IconButton size="small" color="error" disabled={locked} onClick={() => setSelected((l) => l.filter((_, j) => j !== i))} aria-label="Remove"><DeleteOutlineRounded fontSize="small" /></IconButton>
                    </Stack>
                  </Stack>
                ))}
              </Stack>
            )}
          </GlassCard>
        </Stack>

        <Stack spacing={2.5}>
          <GlassCard title="Schedule & timing" subtitle="Times use your local time zone and are stored in UTC">
            <Stack spacing={2}>
              <TextField label="Opens at" type="datetime-local" InputLabelProps={{ shrink: true }} {...register('startAt')} helperText="Leave empty to open as soon as it is published" />
              <TextField label="Deadline" type="datetime-local" InputLabelProps={{ shrink: true }} {...register('endAt')}
                error={Boolean(formState.errors.endAt)} helperText={formState.errors.endAt?.message ?? 'Attempts cannot start or continue after this time'} />
              <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: 'repeat(3, 1fr)' }}>
                <TextField label="Duration" type="number" {...register('durationMinutes')} error={Boolean(formState.errors.durationMinutes)}
                  helperText={formState.errors.durationMinutes?.message} InputProps={{ endAdornment: <InputAdornment position="end">min</InputAdornment> }} />
                <TextField label="Attempts" type="number" {...register('maxAttempts')} error={Boolean(formState.errors.maxAttempts)} />
                <TextField label="Grace" type="number" {...register('gracePeriodSeconds')}
                  InputProps={{ endAdornment: <InputAdornment position="end">s</InputAdornment> }} />
              </Box>
              <Typography variant="caption" color="text.secondary">
                The server records the start time and enforces the deadline. The grace period absorbs network latency for the final save.
              </Typography>
            </Stack>
          </GlassCard>

          <GlassCard title="Scoring & results">
            <Stack spacing={1.5}>
              <TextField label="Passing marks" type="number" {...register('passingMarks')}
                error={Boolean(formState.errors.passingMarks) || passingMarks > totalMarks}
                helperText={passingMarks > totalMarks ? `Exceeds total marks (${totalMarks}) — publishing will be blocked` : `Out of ${totalMarks}`} />
              <Controller name="resultsReleasePolicy" control={control} render={({ field }) => (
                <TextField select label="Release results" {...field}>
                  <MenuItem value="AFTER_GRADING">Automatically once graded</MenuItem>
                  <MenuItem value="MANUAL">Manually by an assessor</MenuItem>
                </TextField>
              )} />
              <Toggle control={control} name="showCorrectAnswers" label="Show correct answers with released results" />
              <Toggle control={control} name="shuffleQuestions" label="Randomise question order" />
              <Toggle control={control} name="shuffleOptions" label="Randomise answer options" />
            </Stack>
          </GlassCard>

          <GlassCard title="Focus monitoring" subtitle="Tab-visibility and window-focus events">
            <Stack spacing={2}>
              <Toggle control={control} name="monitoringEnabled" label="Monitor tab switches and focus loss" />
              <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: 'repeat(2, 1fr)', opacity: monitoringEnabled ? 1 : 0.5 }}>
                <TextField label="Strong warning after" type="number" disabled={!monitoringEnabled} {...register('warningThreshold')}
                  error={Boolean(formState.errors.warningThreshold)} helperText={formState.errors.warningThreshold?.message ?? 'events'} />
                <TextField label="Apply action after" type="number" disabled={!monitoringEnabled} {...register('maxViolations')} helperText="events" />
              </Box>
              <Controller name="violationAction" control={control} render={({ field }) => (
                <TextField select label="When the limit is reached" disabled={!monitoringEnabled} {...field}>
                  {Object.entries(VIOLATION_ACTION_LABEL).map(([v, l]) => <MenuItem key={v} value={v}>{l}</MenuItem>)}
                </TextField>
              )} />
              <Divider />
              <Stack direction="row" spacing={1} alignItems="flex-start">
                <Tooltip title="Browser limitation"><InfoOutlined fontSize="small" color="info" sx={{ mt: 0.25 }} /></Tooltip>
                <Typography variant="caption" color="text.secondary">
                  A web page can only see when its own tab is hidden or its window loses focus. It cannot detect other devices, other
                  browsers, incognito windows or a second screen, and OS pop-ups can cause false positives. Events are flagged for human
                  review — they are never treated as proof of cheating.
                </Typography>
              </Stack>
            </Stack>
          </GlassCard>
        </Stack>
      </Box>
      <QuestionPickerDialog
        open={pickerOpen}
        selectedIds={selected.map((s) => s.question.id)}
        onClose={() => setPickerOpen(false)}
        onAdd={(question) => setSelected((l) => [...l, { question, marks: Number(question.defaultMarks) }])}
      />
    </form>
  );
}

function Toggle({ control, name, label }: { control: Control<FormValues>; name: keyof FormValues; label: string }) {
  return (
    <Controller
      name={name}
      control={control}
      render={({ field }) => (
        <FormControlLabel control={<Switch checked={Boolean(field.value)} onChange={(e) => field.onChange(e.target.checked)} />} label={label} />
      )}
    />
  );
}
