import { zodResolver } from '@hookform/resolvers/zod';
import { MarkEmailReadRounded } from '@mui/icons-material';
import { Alert, Button, Link, Stack, TextField, Typography } from '@mui/material';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link as RouterLink } from 'react-router-dom';
import { z } from 'zod';
import { authApi } from '../../api/services';
import { errorMessage } from '../../utils/errors';
import { AuthLayout } from './AuthLayout';

const schema = z.object({ email: z.string().trim().email('Enter a valid e-mail address') });

export default function ForgotPasswordPage() {
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { register, handleSubmit, formState } = useForm<{ email: string }>({ resolver: zodResolver(schema) });

  const onSubmit = async ({ email }: { email: string }) => {
    setError(null);
    try {
      await authApi.forgotPassword(email);
      setSent(true);
    } catch (e) {
      setError(errorMessage(e));
    }
  };

  return (
    <AuthLayout title="Reset your password" subtitle="We'll e-mail you a secure, single-use link.">
      {sent ? (
        <Stack spacing={2.5} alignItems="flex-start">
          <MarkEmailReadRounded sx={{ fontSize: 44, color: 'primary.light' }} />
          <Typography>
            If an account exists for that address, a reset link is on its way. The link expires shortly, so use it soon.
          </Typography>
          <Button component={RouterLink} to="/login" variant="outlined">Back to sign in</Button>
        </Stack>
      ) : (
        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          <Stack spacing={2.25}>
            {Boolean(error) && <Alert severity="error">{error}</Alert>}
            <TextField label="E-mail" type="email" autoFocus fullWidth {...register('email')}
              error={Boolean(formState.errors.email)} helperText={formState.errors.email?.message} />
            <Button type="submit" variant="contained" size="large" disabled={formState.isSubmitting}>
              {formState.isSubmitting ? 'Sending…' : 'Send reset link'}
            </Button>
            <Link component={RouterLink} to="/login" underline="hover" variant="body2">Back to sign in</Link>
          </Stack>
        </form>
      )}
    </AuthLayout>
  );
}
