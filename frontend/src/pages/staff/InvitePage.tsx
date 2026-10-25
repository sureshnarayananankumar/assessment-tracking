import { SendRounded } from '@mui/icons-material';
import { Alert, Box, Button, Chip, Stack, TextField, Typography } from '@mui/material';
import { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { assignmentApi } from '../../api/services';
import type { InviteResult } from '../../api/types';
import { ErrorState } from '../../components/common/Feedback';
import { GlassCard } from '../../components/common/GlassCard';
import { SectionLoader } from '../../components/common/Loaders';
import { useNotify } from '../../components/common/NotificationProvider';
import { PageHeader } from '../../components/common/PageHeader';
import { useAsync } from '../../hooks/useAsync';
import { errorMessage } from '../../utils/errors';
import { formatDateTime, formatDuration, fromLocalInput } from '../../utils/format';

const EMAIL = /^[^\s@<>]+@[^\s@<>]+\.[^\s@<>]+$/;

/** Accepts "a@b.com" or "Jane Doe <jane@b.com>", separated by new lines, commas or semicolons. */
export function parseInvitees(raw: string): { valid: { email: string; fullName?: string }[]; invalid: string[] } {
  const valid: { email: string; fullName?: string }[] = [];
  const invalid: string[] = [];
  const seen = new Set<string>();
  for (const line of raw.split(/[\n;]+/)) {
    // split on commas that are outside <...> so "Name <a@b.c>, x@y.z" yields two entries
    const parts = line.split(/,(?![^<]*>)/);
    for (const part of parts) {
      const token = part.trim();
      if (!token) continue;
      const m = /^(.*?)<([^>]+)>$/.exec(token);
      const email = (m ? m[2] : token).trim().toLowerCase();
      const fullName = m && m[1].trim() ? m[1].trim().replace(/^"|"$/g, '') : undefined;
      if (!EMAIL.test(email)) {
        invalid.push(token);
      } else if (!seen.has(email)) {
        seen.add(email);
        valid.push(fullName ? { email, fullName } : { email });
      }
    }
  }
  return { valid, invalid };
}

export default function InvitePage() {
  const { id } = useParams();
  const assignmentId = Number(id);
  const navigate = useNavigate();
  const notify = useNotify();
  const { data: a, loading, error } = useAsync(() => assignmentApi.get(assignmentId), [assignmentId]);
  const [raw, setRaw] = useState('');
  const [dueAt, setDueAt] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<InviteResult | null>(null);
  const parsed = useMemo(() => parseInvitees(raw), [raw]);

  if (loading) return <SectionLoader rows={6} />;
  if (error || !a) return <ErrorState error={error} />;
  const open = a.status === 'PUBLISHED' || a.status === 'ACTIVE';

  const send = async () => {
    setBusy(true);
    try {
      const res = await assignmentApi.invite(assignmentId, parsed.valid, fromLocalInput(dueAt), message || undefined);
      setResult(res);
      notify.success(`${res.invited} invited, ${res.reinvited} re-sent`);
      setRaw('');
    } catch (e) {
      notify.error(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader backTo={`/assignments/${assignmentId}`} overline="Assign assessment" title={a.title}
        subtitle={`${formatDuration(a.durationMinutes)} · ${a.questionCount} questions · deadline ${a.endAt ? formatDateTime(a.endAt) : 'none'}`} />
      {!open && <Alert severity="warning" sx={{ mb: 2 }}>Publish this assessment before inviting people.</Alert>}
      <Box sx={{ display: 'grid', gap: 2.5, gridTemplateColumns: { xs: '1fr', lg: '1.3fr 1fr' }, alignItems: 'start' }}>
        <GlassCard title="Who should take it?" subtitle="One per line or comma-separated. Optional name: Jane Doe <jane@company.com>">
          <Stack spacing={2}>
            <TextField multiline minRows={7} fullWidth value={raw} onChange={(e) => setRaw(e.target.value)}
              placeholder={'jane@company.com\nJohn Smith <john@company.com>'} disabled={!open} />
            {(parsed.valid.length > 0 || parsed.invalid.length > 0) && (
              <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
                {parsed.valid.map((v) => <Chip key={v.email} size="small" color="primary" variant="outlined" label={v.fullName ? `${v.fullName} · ${v.email}` : v.email} />)}
                {parsed.invalid.map((v) => <Chip key={v} size="small" color="error" label={`Invalid: ${v}`} />)}
              </Stack>
            )}
            <TextField label="Personal deadline (optional)" type="datetime-local" InputLabelProps={{ shrink: true }} value={dueAt}
              onChange={(e) => setDueAt(e.target.value)} disabled={!open}
              helperText="Overrides the assessment deadline for these people (local time)." />
            <TextField label="Message (optional)" multiline minRows={3} value={message} onChange={(e) => setMessage(e.target.value)} disabled={!open}
              inputProps={{ maxLength: 2000 }} />
            <Stack direction="row" spacing={1.5} justifyContent="flex-end">
              <Button onClick={() => navigate(`/assignments/${assignmentId}`)}>Cancel</Button>
              <Button variant="contained" startIcon={<SendRounded />} disabled={!open || busy || parsed.valid.length === 0 || parsed.invalid.length > 0} onClick={send}>
                {busy ? 'Sending…' : `Send ${parsed.valid.length || ''} invitation${parsed.valid.length === 1 ? '' : 's'}`}
              </Button>
            </Stack>
          </Stack>
        </GlassCard>
        <Stack spacing={2.5}>
          <GlassCard title="What happens next">
            <Stack spacing={1.25}>
              <Typography variant="body2">• New people get a candidate account automatically.</Typography>
              <Typography variant="body2">• Each person receives a personal, expiring link with the title, instructions, duration and deadline.</Typography>
              <Typography variant="body2">• Inviting someone again re-sends the e-mail and revokes their previous link — no duplicate assignments are created.</Typography>
              <Typography variant="body2">• The timer only starts when they press Start.</Typography>
            </Stack>
          </GlassCard>
          {result && (
            <GlassCard title="Result" glow>
              <Stack direction="row" spacing={1} sx={{ mb: 1.5 }}>
                <Chip color="success" label={`${result.invited} invited`} />
                <Chip color="info" label={`${result.reinvited} re-sent`} />
                <Chip color={result.skipped ? 'warning' : 'default'} label={`${result.skipped} skipped`} />
              </Stack>
              {result.messages.map((m) => <Typography key={m} variant="body2" color="text.secondary">• {m}</Typography>)}
              <Button sx={{ mt: 2 }} variant="outlined" onClick={() => navigate(`/assignments/${assignmentId}`)}>View people</Button>
            </GlassCard>
          )}
        </Stack>
      </Box>
    </>
  );
}
