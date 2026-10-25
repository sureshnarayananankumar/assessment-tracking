import { AttachFileRounded, CloudUploadRounded } from '@mui/icons-material';
import {
  Box, Button, Checkbox, Chip, FormControlLabel, MenuItem, Radio, RadioGroup, Stack, TextField, Typography,
} from '@mui/material';
import { alpha, type Theme } from '@mui/material/styles';
import { useRef, useState } from 'react';
import type { AttemptQuestion } from '../../api/types';
import { formatBytes } from '../../utils/format';
import { CODING_LANGUAGES, QUESTION_TYPE_LABEL } from '../../utils/labels';
import { CodeEditor } from './CodeEditor';

export interface LocalAnswer {
  selectedOptionIds: number[];
  textAnswer?: string | null;
  codeAnswer?: string | null;
  codeLanguage?: string | null;
  fileName?: string | null;
  fileSize?: number | null;
}

export const emptyAnswer = (): LocalAnswer => ({ selectedOptionIds: [] });

export function isAnswered(a?: LocalAnswer): boolean {
  if (!a) return false;
  return a.selectedOptionIds.length > 0 || Boolean(a.textAnswer?.trim()) || Boolean(a.codeAnswer?.trim()) || Boolean(a.fileName);
}

interface Props {
  question: AttemptQuestion;
  answer: LocalAnswer;
  onChange: (answer: LocalAnswer) => void;
  onUpload?: (file: File) => Promise<void>;
  disabled?: boolean;
}

/** Renders one question for a candidate (also used as the live preview in the question editor). */
export function QuestionView({ question, answer, onChange, onUpload, disabled }: Props) {
  const fileRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const options = question.options ?? [];

  const optionSx = (selected: boolean) => (t: Theme) => ({
    m: 0,
    px: 1.5,
    py: 0.75,
    borderRadius: 3,
    border: `1px solid ${selected ? t.palette.primary.main : t.palette.divider}`,
    bgcolor: selected ? alpha(t.palette.primary.main, 0.12) : 'transparent',
    transition: 'all .15s ease',
    '&:hover': { bgcolor: alpha(t.palette.primary.main, 0.07) },
    width: '100%',
  });

  return (
    <Stack spacing={2.25}>
      <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
        <Chip size="small" color="primary" label={`Question ${question.index}`} />
        <Chip size="small" variant="outlined" label={QUESTION_TYPE_LABEL[question.type]} />
        <Chip size="small" variant="outlined" label={`${question.marks} mark${Number(question.marks) === 1 ? '' : 's'}`} />
        {question.type === 'MCQ_MULTI' && <Typography variant="caption" color="text.secondary">Select all that apply</Typography>}
      </Stack>
      <Box>
        <Typography variant="h6" sx={{ mb: 1 }}>{question.title}</Typography>
        <Typography sx={{ whiteSpace: 'pre-wrap', lineHeight: 1.7 }}>{question.body}</Typography>
      </Box>

      {(question.type === 'MCQ_SINGLE' || question.type === 'TRUE_FALSE') && (
        <RadioGroup
          value={answer.selectedOptionIds[0] !== undefined ? String(answer.selectedOptionIds[0]) : ''}
          onChange={(e) => onChange({ ...answer, selectedOptionIds: [Number(e.target.value)] })}
        >
          <Stack spacing={1.25}>
            {options.map((o) => (
              <FormControlLabel key={o.id} value={String(o.id)} disabled={disabled} control={<Radio />} label={o.text}
                sx={optionSx(answer.selectedOptionIds.includes(o.id))} />
            ))}
          </Stack>
        </RadioGroup>
      )}

      {question.type === 'MCQ_MULTI' && (
        <Stack spacing={1.25}>
          {options.map((o) => {
            const checked = answer.selectedOptionIds.includes(o.id);
            return (
              <FormControlLabel key={o.id} disabled={disabled} label={o.text} sx={optionSx(checked)}
                control={
                  <Checkbox checked={checked} onChange={(e) => onChange({
                    ...answer,
                    selectedOptionIds: e.target.checked
                      ? [...answer.selectedOptionIds, o.id]
                      : answer.selectedOptionIds.filter((id) => id !== o.id),
                  })} />
                } />
            );
          })}
        </Stack>
      )}

      {question.type === 'SHORT_ANSWER' && (
        <TextField fullWidth label="Your answer" value={answer.textAnswer ?? ''} disabled={disabled}
          onChange={(e) => onChange({ ...answer, textAnswer: e.target.value })} inputProps={{ maxLength: 2000 }} />
      )}

      {question.type === 'LONG_ANSWER' && (
        <TextField fullWidth multiline minRows={8} label="Your answer" value={answer.textAnswer ?? ''} disabled={disabled}
          onChange={(e) => onChange({ ...answer, textAnswer: e.target.value })}
          helperText={`${(answer.textAnswer ?? '').trim().split(/\s+/).filter(Boolean).length} words`} inputProps={{ maxLength: 100000 }} />
      )}

      {question.type === 'CODING' && (
        <Stack spacing={1.25}>
          <Stack direction="row" spacing={1.5} alignItems="center">
            <TextField select size="small" label="Language" sx={{ minWidth: 180 }} disabled={disabled}
              value={answer.codeLanguage ?? question.codingLanguage ?? 'javascript'}
              onChange={(e) => onChange({ ...answer, codeLanguage: e.target.value })}>
              {CODING_LANGUAGES.map((l) => <MenuItem key={l} value={l}>{l}</MenuItem>)}
            </TextField>
            <Typography variant="caption" color="text.secondary">Code is reviewed by an assessor; it is not executed.</Typography>
          </Stack>
          <CodeEditor value={answer.codeAnswer ?? question.starterCode ?? ''} disabled={disabled}
            onChange={(code) => onChange({ ...answer, codeAnswer: code, codeLanguage: answer.codeLanguage ?? question.codingLanguage ?? 'javascript' })} />
        </Stack>
      )}

      {question.type === 'FILE_UPLOAD' && (
        <Stack spacing={1.25}>
          <input ref={fileRef} type="file" hidden
            accept={question.allowedFileTypes ? question.allowedFileTypes.split(',').map((e) => `.${e}`).join(',') : undefined}
            onChange={async (e) => {
              const file = e.target.files?.[0];
              if (!file || !onUpload) return;
              setUploading(true);
              try {
                await onUpload(file);
              } finally {
                setUploading(false);
                if (fileRef.current) fileRef.current.value = '';
              }
            }} />
          <Box sx={(t) => ({ p: 3, borderRadius: 4, border: `2px dashed ${t.palette.divider}`, textAlign: 'center' })}>
            <CloudUploadRounded color="primary" sx={{ fontSize: 40 }} />
            <Typography sx={{ mt: 1 }}>
              Allowed: {question.allowedFileTypes ?? 'common document types'} · max {question.maxFileSizeMb ?? 10} MB
            </Typography>
            <Button sx={{ mt: 1.5 }} variant="outlined" disabled={disabled || uploading || !onUpload} onClick={() => fileRef.current?.click()}>
              {uploading ? 'Uploading…' : answer.fileName ? 'Replace file' : 'Choose file'}
            </Button>
          </Box>
          {answer.fileName && (
            <Stack direction="row" spacing={1} alignItems="center">
              <AttachFileRounded fontSize="small" color="success" />
              <Typography>{answer.fileName}</Typography>
              <Typography variant="caption" color="text.secondary">{formatBytes(answer.fileSize)}</Typography>
            </Stack>
          )}
        </Stack>
      )}
    </Stack>
  );
}
