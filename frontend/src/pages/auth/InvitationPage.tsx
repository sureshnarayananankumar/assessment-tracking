import { AccessTimeRounded, EventRounded, QuizRounded } from '@mui/icons-material';
import { Alert, Box, Button, Divider, LinearProgress, Stack, TextField, Typography } from '@mui/material';
import { useState, type ReactNode } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';
import { invitationApi } from '../../api/services';
import { useAuth } from '../../auth/AuthContext';
import { useAsync } from '../../hooks/useAsync';
import { errorCode, errorMessage } from '../../utils/errors';
import { formatDateTime, formatDuration, fromNow } from '../../utils/format';
import { AuthLayout } from './AuthLayout';
import { passwordSchema } from './ResetPasswordPage';

/** Landing page for the secure link e-mailed to a candidate. */
export default function InvitationPage() {
  const { token = '' } = useParams();
  const navigate = useNavigate();
  const { setSession } = useAuth();
  const info = useAsync(() => invitationApi.info(token), [token]);
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const passwordError = password ? passwordSchema.safeParse(password).error?.issues[0]?.message : undefined;

  const accept = async () => {
    setBusy(true);
    setError(null);
    try {
      const res = await invitationApi.accept(token, password || undefined);
      if (res.requiresLogin || !res.auth) {
        navigate(`/login?redirect=${encodeURIComponent(`/candidate/assessments/${res.assignmentId}`)}`);
        return;
      }
      setSession(res.auth);
      navigate(`/candidate/assessments/${res.assignmentId}`, { replace: true });
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  if (info.loading) {
    return (
      <AuthLayout title="Opening your invitation…">
        <LinearProgress />
      </AuthLayout>
    );
  }

  if (info.error || !info.data) {
    const code = errorCode(info.error);
    return (
      <AuthLayout title="Invitation unavailable">
        <Stack spacing={2}>
          <Alert severity="warning">
            {code === 'INVITATION_EXPIRED'
              ? 'This invitation link has expired. Please contact the person who invited you for a new link.'
              : code === 'INVITATION_REVOKED'
                ? errorMessage(info.error)
                : 'This invitation link is not valid. Check that you copied the full link from your e-mail.'}
          </Alert>
          <Button component={RouterLink} to="/login" variant="outlined">Go to sign in</Button>
        </Stack>
      </AuthLayout>
    );
  }

  const inv = info.data;
  return (
    <AuthLayout title={inv.assignmentTitle} subtitle={`${inv.orgName} invited ${inv.candidateName} (${inv.candidateEmail}).`}>
      <Stack spacing={2.5}>
        <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 1.5 }}>
          <Fact icon={<AccessTimeRounded />} label="Duration" value={formatDuration(inv.durationMinutes)} />
          <Fact icon={<QuizRounded />} label="Questions" value={String(inv.questionCount)} />
          <Fact icon={<EventRounded />} label="Deadline" value={inv.deadline ? fromNow(inv.deadline) : 'None'} />
        </Box>
        {inv.startAt && <Typography variant="body2" color="text.secondary">Opens {formatDateTime(inv.startAt)} (your local time)</Typography>}
        {inv.deadline && <Typography variant="body2" color="text.secondary">Due {formatDateTime(inv.deadline)} (your local time)</Typography>}
        {inv.description && <Typography>{inv.description}</Typography>}
        <Divider />
        {inv.requiresLogin ? (
          <Alert severity="info">This invitation belongs to a staff account. Please sign in with your password to continue.</Alert>
        ) : !inv.hasPassword ? (
          <TextField
            label="Create a password (optional)"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={Boolean(passwordError)}
            helperText={passwordError ?? 'Lets you sign in later without this link. You can skip this.'}
            fullWidth
          />
        ) : null}
        {Boolean(error) && <Alert severity="error">{error}</Alert>}
        <Button variant="contained" size="large" onClick={accept} disabled={busy || Boolean(passwordError)}>
          {busy ? 'Opening…' : inv.requiresLogin ? 'Sign in to continue' : 'Continue to assessment'}
        </Button>
        <Typography variant="caption" color="text.secondary">
          The timer starts only when you press <b>Start</b> on the next screen.
        </Typography>
      </Stack>
    </AuthLayout>
  );
}

function Fact({ icon, label, value }: { icon: ReactNode; label: string; value: string }) {
  return (
    <Box sx={{ p: 1.5, borderRadius: 3, border: 1, borderColor: 'divider', bgcolor: 'action.hover', textAlign: 'center' }}>
      <Box sx={{ color: 'primary.light' }}>{icon}</Box>
      <Typography variant="caption" color="text.secondary" display="block">{label}</Typography>
      <Typography fontWeight={700} noWrap>{value}</Typography>
    </Box>
  );
}
