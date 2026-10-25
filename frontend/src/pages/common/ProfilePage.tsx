import { zodResolver } from '@hookform/resolvers/zod';
import { KeyRounded } from '@mui/icons-material';
import { Avatar, Box, Button, Chip, Stack, TextField, Typography } from '@mui/material';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { authApi } from '../../api/services';
import { useAuth } from '../../auth/AuthContext';
import { GlassCard } from '../../components/common/GlassCard';
import { useNotify } from '../../components/common/NotificationProvider';
import { PageHeader } from '../../components/common/PageHeader';
import { errorMessage } from '../../utils/errors';
import { formatDateTime, initials } from '../../utils/format';
import { ROLE_LABEL } from '../../utils/labels';
import { passwordSchema } from '../auth/ResetPasswordPage';

const schema = z
  .object({ currentPassword: z.string().min(1, 'Required'), newPassword: passwordSchema, confirm: z.string() })
  .refine((v) => v.newPassword === v.confirm, { path: ['confirm'], message: 'Passwords do not match' });
type FormValues = z.infer<typeof schema>;

export default function ProfilePage() {
  const { user, setSession } = useAuth();
  const notify = useNotify();
  const { register, handleSubmit, reset, formState } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = async (v: FormValues) => {
    try {
      setSession(await authApi.changePassword(v.currentPassword, v.newPassword));
      reset();
      notify.success('Password changed. Other sessions were signed out.');
    } catch (e) {
      notify.error(errorMessage(e));
    }
  };

  if (!user) return null;
  return (
    <>
      <PageHeader title="Profile" subtitle="Your account details and security." />
      <Box sx={{ display: 'grid', gap: 2.5, gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, alignItems: 'start' }}>
        <GlassCard glow>
          <Stack direction="row" spacing={2.5} alignItems="center">
            <Avatar sx={{ width: 72, height: 72, fontSize: 26, fontWeight: 800, background: (t) => t.brand.gradient }}>{initials(user.fullName)}</Avatar>
            <Box>
              <Typography variant="h5">{user.fullName}</Typography>
              <Typography color="text.secondary">{user.email}</Typography>
              <Stack direction="row" spacing={0.75} sx={{ mt: 1 }}>{user.roles.map((r) => <Chip key={r} size="small" color="primary" variant="outlined" label={ROLE_LABEL[r]} />)}</Stack>
            </Box>
          </Stack>
          <Stack spacing={1} sx={{ mt: 3 }}>
            <Row label="Department" value={user.department ?? '—'} />
            <Row label="Job title" value={user.jobTitle ?? '—'} />
            <Row label="E-mail verified" value={user.emailVerified ? 'Yes' : 'No'} />
            <Row label="Last sign-in" value={formatDateTime(user.lastLoginAt)} />
            <Row label="Member since" value={formatDateTime(user.createdAt)} />
          </Stack>
        </GlassCard>
        <GlassCard title="Change password" subtitle="At least 8 characters with letters and numbers">
          <form onSubmit={handleSubmit(onSubmit)} noValidate>
            <Stack spacing={2}>
              <TextField type="password" label="Current password" autoComplete="current-password" {...register('currentPassword')}
                error={Boolean(formState.errors.currentPassword)} helperText={formState.errors.currentPassword?.message} />
              <TextField type="password" label="New password" autoComplete="new-password" {...register('newPassword')}
                error={Boolean(formState.errors.newPassword)} helperText={formState.errors.newPassword?.message} />
              <TextField type="password" label="Confirm new password" autoComplete="new-password" {...register('confirm')}
                error={Boolean(formState.errors.confirm)} helperText={formState.errors.confirm?.message} />
              <Button type="submit" variant="contained" startIcon={<KeyRounded />} disabled={formState.isSubmitting}>Update password</Button>
            </Stack>
          </form>
        </GlassCard>
      </Box>
    </>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <Stack direction="row" justifyContent="space-between">
      <Typography color="text.secondary">{label}</Typography>
      <Typography fontWeight={600}>{value}</Typography>
    </Stack>
  );
}
