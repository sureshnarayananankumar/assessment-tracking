import { CheckCircleRounded } from '@mui/icons-material';
import { Box, Card, Stack, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import type { ReactNode } from 'react';
import { BrandLogo } from '../../components/common/BrandLogo';
import { AppearancePicker, ThemeModeToggle } from '../../components/layout/ThemeControls';
import { LiquidBackground } from '../../theme/LiquidBackground';

const FEATURES = [
  'Timed assessments enforced by the server',
  'Autosave with instant recovery after refresh',
  'Objective auto-grading plus expert review',
  'Focus monitoring with human-in-the-loop review',
];

export function AuthLayout({ title, subtitle, children, wide }: { title: string; subtitle?: ReactNode; children: ReactNode; wide?: boolean }) {
  const theme = useTheme();
  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', p: { xs: 2, sm: 4 } }}>
      <LiquidBackground />
      <Stack direction="row" spacing={1} sx={{ position: 'fixed', top: 16, right: 16, zIndex: 2 }}>
        <AppearancePicker />
        <ThemeModeToggle />
      </Stack>
      <Box
        sx={{
          width: '100%',
          maxWidth: wide ? 1040 : 980,
          display: 'grid',
          gridTemplateColumns: { xs: '1fr', md: '1.05fr 1fr' },
          gap: { xs: 3, md: 5 },
          alignItems: 'center',
        }}
      >
        <Box sx={{ display: { xs: 'none', md: 'block' }, pr: 2 }}>
          <BrandLogo size={52} />
          <Typography variant="h2" sx={{ mt: 5, fontSize: 46, lineHeight: 1.08 }}>
            Hire and grow talent with{' '}
            <Box component="span" sx={{ background: theme.brand.accent, WebkitBackgroundClip: 'text', backgroundClip: 'text', color: 'transparent' }}>
              fair, focused
            </Box>{' '}
            assessments.
          </Typography>
          <Typography color="text.secondary" sx={{ mt: 2, fontSize: 17, maxWidth: 460 }}>
            One workspace for frontend, backend, full-stack, QA and every other role — from question bank to final report.
          </Typography>
          <Stack spacing={1.5} sx={{ mt: 4 }}>
            {FEATURES.map((f) => (
              <Stack key={f} direction="row" spacing={1.25} alignItems="center">
                <CheckCircleRounded sx={{ color: 'primary.light', fontSize: 20 }} />
                <Typography>{f}</Typography>
              </Stack>
            ))}
          </Stack>
        </Box>
        <Card sx={{ p: { xs: 3, sm: 4.5 }, boxShadow: (t) => `${t.glass.shadow}, ${t.brand.glow}` }}>
          <Box sx={{ display: { xs: 'block', md: 'none' }, mb: 3 }}>
            <BrandLogo />
          </Box>
          <Typography variant="h4" sx={{ fontSize: 28 }}>{title}</Typography>
          {subtitle && <Typography color="text.secondary" sx={{ mt: 1, mb: 3 }}>{subtitle}</Typography>}
          {!subtitle && <Box sx={{ mb: 3 }} />}
          <Box sx={{ position: 'relative' }}>{children}</Box>
        </Card>
      </Box>
    </Box>
  );
}
