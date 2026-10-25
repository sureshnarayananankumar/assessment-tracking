import { zodResolver } from '@hookform/resolvers/zod';
import { LockRounded, VisibilityOffRounded, VisibilityRounded } from '@mui/icons-material';
import { Alert, Button, IconButton, InputAdornment, Link, Stack, TextField } from '@mui/material';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Navigate, Link as RouterLink, useNavigate, useSearchParams } from 'react-router-dom';
import { z } from 'zod';
import { useAuth } from '../../auth/AuthContext';
import { errorMessage } from '../../utils/errors';
import { AuthLayout } from './AuthLayout';

const schema = z.object({
  email: z.string().trim().min(1, 'E-mail is required').email('Enter a valid e-mail address'),
  password: z.string().min(1, 'Password is required'),
});
type FormValues = z.infer<typeof schema>;

/** Only allow same-site relative redirects (prevents open redirects). */
function safeRedirect(value: string | null): string | null {
  return value && value.startsWith('/') && !value.startsWith('//') ? value : null;
}

export default function LoginPage() {
  const { login, user, initializing, isStaff } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { register, handleSubmit, formState } = useForm<FormValues>({ resolver: zodResolver(schema), mode: 'onTouched' });
  const redirect = safeRedirect(params.get('redirect'));

  if (!initializing && user) {
    return <Navigate to={redirect ?? (isStaff ? '/dashboard' : '/candidate')} replace />;
  }

  const onSubmit = async (values: FormValues) => {
    setError(null);
    try {
      const u = await login(values.email, values.password);
      const staff = u.roles.some((r) => r !== 'CANDIDATE');
      navigate(redirect ?? (staff ? '/dashboard' : '/candidate'), { replace: true });
    } catch (e) {
      setError(errorMessage(e));
    }
  };

  return (
    <AuthLayout title="Welcome back" subtitle="Sign in to manage or take your assessments.">
      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <Stack spacing={2.25}>
          {Boolean(error) && <Alert severity="error">{error}</Alert>}
          <TextField
            label="E-mail"
            type="email"
            autoComplete="email"
            autoFocus
            fullWidth
            {...register('email')}
            error={Boolean(formState.errors.email)}
            helperText={formState.errors.email?.message}
          />
          <TextField
            label="Password"
            type={showPassword ? 'text' : 'password'}
            autoComplete="current-password"
            fullWidth
            {...register('password')}
            error={Boolean(formState.errors.password)}
            helperText={formState.errors.password?.message}
            InputProps={{
              endAdornment: (
                <InputAdornment position="end">
                  <IconButton onClick={() => setShowPassword((s) => !s)} edge="end" aria-label={showPassword ? 'Hide password' : 'Show password'}>
                    {showPassword ? <VisibilityOffRounded /> : <VisibilityRounded />}
                  </IconButton>
                </InputAdornment>
              ),
            }}
          />
          <Stack direction="row" justifyContent="flex-end">
            <Link component={RouterLink} to="/forgot-password" underline="hover" variant="body2">
              Forgot password?
            </Link>
          </Stack>
          <Button type="submit" variant="contained" size="large" startIcon={<LockRounded />} disabled={formState.isSubmitting}>
            {formState.isSubmitting ? 'Signing in…' : 'Sign in'}
          </Button>
        </Stack>
      </form>
    </AuthLayout>
  );
}
