import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Button, LinearProgress, Stack, TextField, Typography } from '@mui/material';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link as RouterLink, useNavigate, useSearchParams } from 'react-router-dom';
import { z } from 'zod';
import { authApi } from '../../api/services';
import type { TokenCheck } from '../../api/types';
import { useNotify } from '../../components/common/NotificationProvider';
import { useAsync } from '../../hooks/useAsync';
import { errorMessage } from '../../utils/errors';
import { AuthLayout } from './AuthLayout';

export const passwordSchema = z
  .string()
  .min(8, 'At least 8 characters')
  .max(128, 'At most 128 characters')
  .regex(/[A-Za-z]/, 'Include at least one letter')
  .regex(/\d/, 'Include at least one number');

const schema = z
  .object({ password: passwordSchema, confirm: z.string() })
  .refine((v) => v.password === v.confirm, { path: ['confirm'], message: 'Passwords do not match' });
type FormValues = z.infer<typeof schema>;

export function passwordStrength(pw: string): number {
  let score = 0;
  if (pw.length >= 8) score += 25;
  if (pw.length >= 12) score += 20;
  if (/[a-z]/.test(pw) && /[A-Z]/.test(pw)) score += 20;
  if (/\d/.test(pw)) score += 15;
  if (/[^A-Za-z0-9]/.test(pw)) score += 20;
  return Math.min(100, score);
}

export default function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const navigate = useNavigate();
  const notify = useNotify();
  const [error, setError] = useState<string | null>(null);
  const check = useAsync(() => (token ? authApi.validateResetToken(token) : Promise.resolve<TokenCheck>({ valid: false })), [token]);
  const { register, handleSubmit, formState, watch } = useForm<FormValues>({ resolver: zodResolver(schema), mode: 'onTouched' });
  const setup = check.data?.purpose === 'ACCOUNT_SETUP' || params.get('setup') === '1';
  const strength = passwordStrength(watch('password') ?? '');

  const onSubmit = async (values: FormValues) => {
    setError(null);
    try {
      await authApi.resetPassword(token, values.password);
      notify.success(setup ? 'Account activated - you can sign in now.' : 'Password updated - please sign in.');
      navigate('/login', { replace: true });
    } catch (e) {
      setError(errorMessage(e));
    }
  };

  return (
    <AuthLayout
      title={setup ? 'Activate your account' : 'Choose a new password'}
      subtitle={check.data?.email ? `For ${check.data.email}` : undefined}
    >
      {check.loading ? (
        <LinearProgress />
      ) : !check.data?.valid ? (
        <Stack spacing={2}>
          <Alert severity="warning">This link is invalid or has expired. Request a new one to continue.</Alert>
          <Button component={RouterLink} to="/forgot-password" variant="contained">Request a new link</Button>
        </Stack>
      ) : (
        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          <Stack spacing={2.25}>
            {Boolean(error) && <Alert severity="error">{error}</Alert>}
            <TextField label="New password" type="password" autoComplete="new-password" autoFocus fullWidth
              {...register('password')} error={Boolean(formState.errors.password)}
              helperText={formState.errors.password?.message ?? 'At least 8 characters with letters and numbers'} />
            <Stack spacing={0.5}>
              <LinearProgress variant="determinate" value={strength} aria-label="Password strength" />
              <Typography variant="caption" color="text.secondary">
                Strength: {strength < 45 ? 'weak' : strength < 75 ? 'good' : 'strong'}
              </Typography>
            </Stack>
            <TextField label="Confirm password" type="password" autoComplete="new-password" fullWidth
              {...register('confirm')} error={Boolean(formState.errors.confirm)} helperText={formState.errors.confirm?.message} />
            <Button type="submit" variant="contained" size="large" disabled={formState.isSubmitting}>
              {formState.isSubmitting ? 'Saving…' : setup ? 'Activate account' : 'Update password'}
            </Button>
          </Stack>
        </form>
      )}
    </AuthLayout>
  );
}
