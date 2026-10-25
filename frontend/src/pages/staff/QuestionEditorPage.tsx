import { zodResolver } from '@hookform/resolvers/zod';
import { AddRounded, DeleteOutlineRounded, SaveRounded } from '@mui/icons-material';
import {
  Alert, Box, Button, Checkbox, FormControlLabel, IconButton, MenuItem, Radio, Stack, Switch, TextField, Tooltip, Typography,
} from '@mui/material';
import { useEffect, useMemo, useState } from 'react';
import { Controller, useFieldArray, useForm } from 'react-hook-form';
import { useNavigate, useParams } from 'react-router-dom';
import { z } from 'zod';
import { questionApi } from '../../api/services';
import type { AttemptQuestion, Question, QuestionPayload, QuestionType } from '../../api/types';
import { emptyAnswer, QuestionView, type LocalAnswer } from '../../components/assessment/QuestionView';
import { ErrorState } from '../../components/common/Feedback';
import { GlassCard } from '../../components/common/GlassCard';
import { SectionLoader } from '../../components/common/Loaders';
import { useNotify } from '../../components/common/NotificationProvider';
import { PageHeader } from '../../components/common/PageHeader';
import { errorMessage } from '../../utils/errors';
import { CODING_LANGUAGES, DIFFICULTY_LABEL, QUESTION_TYPE_LABEL } from '../../utils/labels';

const optionalInt = z.preprocess(
  (v) => (v === '' || v === null || v === undefined || Number.isNaN(v) ? undefined : Number(v)),
  z.number().int().min(1).max(20).optional(),
);

const schema = z
  .object({
    type: z.enum(['MCQ_SINGLE', 'MCQ_MULTI', 'TRUE_FALSE', 'SHORT_ANSWER', 'LONG_ANSWER', 'CODING', 'FILE_UPLOAD']),
    title: z.string().trim().min(3, 'At least 3 characters').max(255),
    body: z.string().trim().min(1, 'The question text is required').max(20000),
    skillCategory: z.string().max(100).optional(),
    difficulty: z.enum(['BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT']),
    defaultMarks: z.coerce.number().min(0, 'Cannot be negative').max(1000),
    visibility: z.enum(['PRIVATE', 'SHARED']),
    codingLanguage: z.string().optional(),
    starterCode: z.string().max(20000).optional(),
    referenceAnswer: z.string().max(20000).optional(),
    evaluationGuidelines: z.string().max(5000).optional(),
    acceptedAnswersText: z.string().max(5000).optional(),
    caseSensitive: z.boolean(),
    allowedFileTypes: z.string().max(255).optional(),
    maxFileSizeMb: optionalInt,
    tags: z.string().max(255).optional(),
    options: z.array(z.object({ text: z.string().trim().min(1, 'Option text is required').max(2000), correct: z.boolean() })),
  })
  .superRefine((v, ctx) => {
    const correct = v.options.filter((o) => o.correct).length;
    if (v.type === 'MCQ_SINGLE' || v.type === 'MCQ_MULTI') {
      if (v.options.length < 2) ctx.addIssue({ code: 'custom', path: ['options'], message: 'Add at least two options' });
      if (v.type === 'MCQ_SINGLE' && correct !== 1) ctx.addIssue({ code: 'custom', path: ['options'], message: 'Mark exactly one correct option' });
      if (v.type === 'MCQ_MULTI' && correct < 1) ctx.addIssue({ code: 'custom', path: ['options'], message: 'Mark at least one correct option' });
    }
    if (v.type === 'TRUE_FALSE' && correct !== 1) ctx.addIssue({ code: 'custom', path: ['options'], message: 'Choose the correct answer' });
  });

type FormValues = z.infer<typeof schema>;

const usesOptions = (t: QuestionType) => t === 'MCQ_SINGLE' || t === 'MCQ_MULTI' || t === 'TRUE_FALSE';
const TRUE_FALSE = [{ text: 'True', correct: true }, { text: 'False', correct: false }];
const BLANK_OPTIONS = [{ text: '', correct: true }, { text: '', correct: false }, { text: '', correct: false }, { text: '', correct: false }];

const DEFAULTS: FormValues = {
  type: 'MCQ_SINGLE', title: '', body: '', skillCategory: '', difficulty: 'INTERMEDIATE', defaultMarks: 1,
  visibility: 'PRIVATE', codingLanguage: 'javascript', starterCode: '', referenceAnswer: '', evaluationGuidelines: '',
  acceptedAnswersText: '', caseSensitive: false, allowedFileTypes: 'pdf,docx,zip', maxFileSizeMb: 10, tags: '', options: BLANK_OPTIONS,
};

function toForm(q: Question): FormValues {
  return {
    type: q.type, title: q.title, body: q.body, skillCategory: q.skillCategory ?? '', difficulty: q.difficulty,
    defaultMarks: Number(q.defaultMarks), visibility: q.visibility, codingLanguage: q.codingLanguage ?? 'javascript',
    starterCode: q.starterCode ?? '', referenceAnswer: q.referenceAnswer ?? '', evaluationGuidelines: q.evaluationGuidelines ?? '',
    acceptedAnswersText: q.acceptedAnswers.join('\n'), caseSensitive: q.caseSensitive, allowedFileTypes: q.allowedFileTypes ?? '',
    maxFileSizeMb: q.maxFileSizeMb ?? 10, tags: q.tags ?? '',
    options: q.options.length ? q.options.map((o) => ({ text: o.text, correct: Boolean(o.correct) })) : BLANK_OPTIONS,
  };
}

export default function QuestionEditorPage() {
  const { id } = useParams();
  const editing = Boolean(id);
  const navigate = useNavigate();
  const notify = useNotify();
  const [loading, setLoading] = useState(editing);
  const [loadError, setLoadError] = useState<unknown>(null);
  const [previewAnswer, setPreviewAnswer] = useState<LocalAnswer>(emptyAnswer());
  const { register, control, handleSubmit, reset, watch, setValue, formState } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: DEFAULTS,
    mode: 'onTouched',
  });
  const { fields, append, remove, replace } = useFieldArray({ control, name: 'options' });
  const values = watch();
  const type = values.type;

  useEffect(() => {
    if (!id) return;
    questionApi.get(Number(id)).then((q) => reset(toForm(q))).catch(setLoadError).finally(() => setLoading(false));
  }, [id, reset]);

  const changeType = (next: QuestionType) => {
    setValue('type', next);
    if (next === 'TRUE_FALSE') replace(TRUE_FALSE);
    else if (usesOptions(next) && (type === 'TRUE_FALSE' || fields.length < 2)) replace(BLANK_OPTIONS);
    setPreviewAnswer(emptyAnswer());
  };

  const setCorrect = (index: number, checked: boolean) => {
    const opts = values.options.map((o, i) => ({
      ...o,
      correct: type === 'MCQ_MULTI' ? (i === index ? checked : o.correct) : i === index,
    }));
    replace(opts);
  };

  const preview: AttemptQuestion = useMemo(() => ({
    questionId: 0,
    index: 1,
    type: values.type,
    title: values.title || 'Question title',
    body: values.body || 'Question text appears here.',
    marks: Number(values.defaultMarks) || 0,
    codingLanguage: values.codingLanguage,
    starterCode: values.starterCode,
    allowedFileTypes: values.allowedFileTypes,
    maxFileSizeMb: Number(values.maxFileSizeMb) || 10,
    options: usesOptions(values.type) ? values.options.map((o, i) => ({ id: i + 1, text: o.text || `Option ${i + 1}` })) : null,
  }), [values]);

  const onSubmit = async (v: FormValues) => {
    const payload: QuestionPayload = {
      type: v.type, title: v.title, body: v.body, skillCategory: v.skillCategory || undefined, difficulty: v.difficulty,
      defaultMarks: v.defaultMarks, visibility: v.visibility, tags: v.tags || undefined,
      referenceAnswer: v.referenceAnswer || undefined, evaluationGuidelines: v.evaluationGuidelines || undefined,
      caseSensitive: v.caseSensitive,
      codingLanguage: v.type === 'CODING' ? v.codingLanguage : undefined,
      starterCode: v.type === 'CODING' ? v.starterCode : undefined,
      acceptedAnswers: v.type === 'SHORT_ANSWER'
        ? (v.acceptedAnswersText ?? '').split('\n').map((s) => s.trim()).filter(Boolean)
        : undefined,
      allowedFileTypes: v.type === 'FILE_UPLOAD' ? v.allowedFileTypes : undefined,
      maxFileSizeMb: v.type === 'FILE_UPLOAD' ? v.maxFileSizeMb : undefined,
      options: usesOptions(v.type) ? v.options : undefined,
    };
    try {
      if (editing) await questionApi.update(Number(id), payload);
      else await questionApi.create(payload);
      notify.success(editing ? 'Question saved' : 'Question created');
      navigate('/questions');
    } catch (e) {
      notify.error(errorMessage(e));
    }
  };

  if (loading) return <SectionLoader rows={8} />;
  if (loadError) return <ErrorState error={loadError} />;
  const optionsError = (formState.errors.options as { message?: string; root?: { message?: string } } | undefined);

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate>
      <PageHeader backTo="/questions" overline="Question bank" title={editing ? 'Edit question' : 'New question'}
        actions={<Button type="submit" variant="contained" startIcon={<SaveRounded />} disabled={formState.isSubmitting}>{formState.isSubmitting ? 'Saving…' : 'Save question'}</Button>} />
      <Box sx={{ display: 'grid', gap: 2.5, gridTemplateColumns: { xs: '1fr', lg: '1.2fr 1fr' }, alignItems: 'start' }}>
        <Stack spacing={2.5}>
          <GlassCard title="Question">
            <Stack spacing={2}>
              <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1.3fr 1fr 0.7fr' } }}>
                <TextField select label="Type" value={type} onChange={(e) => changeType(e.target.value as QuestionType)}>
                  {Object.entries(QUESTION_TYPE_LABEL).map(([v, l]) => <MenuItem key={v} value={v}>{l}</MenuItem>)}
                </TextField>
                <Controller name="difficulty" control={control} render={({ field }) => (
                  <TextField select label="Difficulty" {...field}>
                    {Object.entries(DIFFICULTY_LABEL).map(([v, l]) => <MenuItem key={v} value={v}>{l}</MenuItem>)}
                  </TextField>
                )} />
                <TextField label="Marks" type="number" inputProps={{ step: 0.5, min: 0 }} {...register('defaultMarks')}
                  error={Boolean(formState.errors.defaultMarks)} helperText={formState.errors.defaultMarks?.message} />
              </Box>
              <TextField label="Title" {...register('title')} error={Boolean(formState.errors.title)} helperText={formState.errors.title?.message} />
              <TextField label="Question text" multiline minRows={4} {...register('body')} error={Boolean(formState.errors.body)} helperText={formState.errors.body?.message} />
              <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)' } }}>
                <TextField label="Skill category" placeholder="Frontend, Backend, QA…" {...register('skillCategory')} />
                <TextField label="Tags" placeholder="react, hooks" {...register('tags')} />
              </Box>
              <Controller name="visibility" control={control} render={({ field }) => (
                <FormControlLabel control={<Switch checked={field.value === 'SHARED'} onChange={(e) => field.onChange(e.target.checked ? 'SHARED' : 'PRIVATE')} />}
                  label="Share with all assessment creators (read-only for others)" />
              )} />
            </Stack>
          </GlassCard>

          {usesOptions(type) && (
            <GlassCard title={type === 'TRUE_FALSE' ? 'Correct answer' : 'Answer options'}
              subtitle={type === 'MCQ_MULTI' ? 'Tick every correct option (exact match required for marks)' : 'Select the single correct option'}
              action={type !== 'TRUE_FALSE' && fields.length < 12 && <Button size="small" startIcon={<AddRounded />} onClick={() => append({ text: '', correct: false })}>Add option</Button>}>
              <Stack spacing={1.25}>
                {fields.map((f, i) => (
                  <Stack key={f.id} direction="row" spacing={1} alignItems="center">
                    {type === 'MCQ_MULTI'
                      ? <Checkbox checked={values.options[i]?.correct ?? false} onChange={(e) => setCorrect(i, e.target.checked)} inputProps={{ 'aria-label': `Option ${i + 1} correct` }} />
                      : <Radio checked={values.options[i]?.correct ?? false} onChange={() => setCorrect(i, true)} inputProps={{ 'aria-label': `Option ${i + 1} correct` }} />}
                    <TextField fullWidth size="small" placeholder={`Option ${i + 1}`} disabled={type === 'TRUE_FALSE'}
                      {...register(`options.${i}.text` as const)} error={Boolean(formState.errors.options?.[i]?.text)}
                      helperText={formState.errors.options?.[i]?.text?.message} />
                    {type !== 'TRUE_FALSE' && (
                      <Tooltip title="Remove option">
                        <span><IconButton size="small" disabled={fields.length <= 2} onClick={() => remove(i)}><DeleteOutlineRounded fontSize="small" /></IconButton></span>
                      </Tooltip>
                    )}
                  </Stack>
                ))}
                {(optionsError?.message || optionsError?.root?.message) && (
                  <Alert severity="error">{optionsError?.message ?? optionsError?.root?.message}</Alert>
                )}
              </Stack>
            </GlassCard>
          )}

          {type === 'SHORT_ANSWER' && (
            <GlassCard title="Auto-grading" subtitle="Leave empty to grade manually">
              <Stack spacing={1.5}>
                <TextField label="Accepted answers (one per line)" multiline minRows={3} {...register('acceptedAnswersText')}
                  helperText="Whitespace is normalised. Exact match against any line earns full marks." />
                <Controller name="caseSensitive" control={control} render={({ field }) => (
                  <FormControlLabel control={<Switch checked={field.value} onChange={(e) => field.onChange(e.target.checked)} />} label="Case sensitive" />
                )} />
              </Stack>
            </GlassCard>
          )}

          {type === 'CODING' && (
            <GlassCard title="Coding setup" subtitle="Submissions are stored for manual review — code is never executed by the platform">
              <Stack spacing={2}>
                <Controller name="codingLanguage" control={control} render={({ field }) => (
                  <TextField select label="Default language" {...field} value={field.value ?? 'javascript'}>
                    {CODING_LANGUAGES.map((l) => <MenuItem key={l} value={l}>{l}</MenuItem>)}
                  </TextField>
                )} />
                <TextField label="Starter code" multiline minRows={5} {...register('starterCode')} InputProps={{ sx: { fontFamily: '"JetBrains Mono", monospace', fontSize: 13 } }} />
              </Stack>
            </GlassCard>
          )}

          {type === 'FILE_UPLOAD' && (
            <GlassCard title="Upload rules">
              <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '2fr 1fr' } }}>
                <TextField label="Allowed extensions" placeholder="pdf,docx,zip" {...register('allowedFileTypes')} />
                <TextField label="Max size (MB)" type="number" {...register('maxFileSizeMb')} error={Boolean(formState.errors.maxFileSizeMb)} helperText="1-20" />
              </Box>
            </GlassCard>
          )}

          {!['MCQ_SINGLE', 'MCQ_MULTI', 'TRUE_FALSE'].includes(type) && (
            <GlassCard title="For evaluators" subtitle="Never shown to candidates">
              <Stack spacing={2}>
                <TextField label="Reference answer" multiline minRows={3} {...register('referenceAnswer')} />
                <TextField label="Evaluation guidelines / rubric" multiline minRows={3} {...register('evaluationGuidelines')} />
              </Stack>
            </GlassCard>
          )}
        </Stack>

        <Box sx={{ position: { lg: 'sticky' }, top: { lg: 90 } }}>
          <GlassCard title="Candidate preview" subtitle="Exactly what candidates will see (without the answer key)" glow>
            <QuestionView question={preview} answer={previewAnswer} onChange={setPreviewAnswer} />
          </GlassCard>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1.5, px: 1 }}>
            Questions that candidates have already answered are locked to keep results consistent — duplicate them to make changes.
          </Typography>
        </Box>
      </Box>
    </form>
  );
}
