import { AddRounded, CheckRounded, SearchRounded } from '@mui/icons-material';
import {
  Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, InputAdornment, LinearProgress, List, ListItem,
  ListItemText, MenuItem, Pagination, Stack, TextField, Typography,
} from '@mui/material';
import { useState } from 'react';
import { questionApi } from '../../api/services';
import type { Question, QuestionType } from '../../api/types';
import { useAsync, useDebounced } from '../../hooks/useAsync';
import { DIFFICULTY_LABEL, QUESTION_TYPE_LABEL } from '../../utils/labels';

interface Props {
  open: boolean;
  selectedIds: number[];
  onAdd: (question: Question) => void;
  onClose: () => void;
}

export function QuestionPickerDialog({ open, selectedIds, onAdd, onClose }: Props) {
  const [search, setSearch] = useState('');
  const [type, setType] = useState<QuestionType | ''>('');
  const [page, setPage] = useState(0);
  const q = useDebounced(search);
  const { data, loading } = useAsync(
    () => (open ? questionApi.list({ search: q, type, page, size: 10, sort: 'updatedAt,desc' }) : Promise.resolve(undefined)),
    [open, q, type, page],
  );

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>Add questions from the bank</DialogTitle>
      <DialogContent>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ mb: 2, mt: 0.5 }}>
          <TextField size="small" placeholder="Search title or tags" value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }} sx={{ flex: 1 }}
            InputProps={{ startAdornment: <InputAdornment position="start"><SearchRounded /></InputAdornment> }} />
          <TextField select size="small" label="Type" value={type} onChange={(e) => { setType(e.target.value as QuestionType | ''); setPage(0); }} sx={{ minWidth: 180 }}>
            <MenuItem value="">All types</MenuItem>
            {Object.entries(QUESTION_TYPE_LABEL).map(([v, l]) => <MenuItem key={v} value={v}>{l}</MenuItem>)}
          </TextField>
        </Stack>
        {loading && <LinearProgress sx={{ mb: 1 }} />}
        <List disablePadding>
          {data?.content.map((question) => {
            const added = selectedIds.includes(question.id);
            return (
              <ListItem key={question.id} divider
                secondaryAction={
                  <Button size="small" variant={added ? 'text' : 'outlined'} disabled={added}
                    startIcon={added ? <CheckRounded /> : <AddRounded />} onClick={() => onAdd(question)}>
                    {added ? 'Added' : 'Add'}
                  </Button>
                }>
                <ListItemText
                  primary={<Typography fontWeight={650} sx={{ pr: 10 }}>{question.title}</Typography>}
                  secondary={
                    <Stack direction="row" spacing={0.75} sx={{ mt: 0.5 }} flexWrap="wrap" useFlexGap>
                      <Chip size="small" label={QUESTION_TYPE_LABEL[question.type]} />
                      <Chip size="small" variant="outlined" label={DIFFICULTY_LABEL[question.difficulty]} />
                      {question.skillCategory && <Chip size="small" variant="outlined" label={question.skillCategory} />}
                      <Chip size="small" variant="outlined" label={`${question.defaultMarks} marks`} />
                    </Stack>
                  }
                  secondaryTypographyProps={{ component: 'div' }}
                />
              </ListItem>
            );
          })}
        </List>
        {data && data.content.length === 0 && (
          <Box sx={{ py: 5, textAlign: 'center' }}>
            <Typography color="text.secondary">No questions match. Create questions in the question bank first.</Typography>
          </Box>
        )}
        {data && data.totalPages > 1 && (
          <Stack alignItems="center" sx={{ mt: 2 }}>
            <Pagination count={data.totalPages} page={page + 1} onChange={(_, p) => setPage(p - 1)} />
          </Stack>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} variant="contained">Done</Button>
      </DialogActions>
    </Dialog>
  );
}
