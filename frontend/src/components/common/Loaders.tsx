import { Box, CircularProgress, Skeleton, Stack, Typography } from '@mui/material';
import { LiquidBackground } from '../../theme/LiquidBackground';
import { BrandLogo } from './BrandLogo';

export function FullPageLoader({ label = 'Loading…' }: { label?: string }) {
  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center' }}>
      <LiquidBackground calm />
      <Stack alignItems="center" spacing={2.5}>
        <BrandLogo size={52} withText={false} />
        <CircularProgress size={28} thickness={5} />
        <Typography variant="body2" color="text.secondary">{label}</Typography>
      </Stack>
    </Box>
  );
}

export function SectionLoader({ rows = 4 }: { rows?: number }) {
  return (
    <Stack spacing={1.5} sx={{ py: 1 }} aria-busy="true" aria-label="Loading">
      {Array.from({ length: rows }).map((_, i) => (
        <Skeleton key={i} variant="rounded" height={i === 0 ? 34 : 52} sx={{ borderRadius: 3 }} />
      ))}
    </Stack>
  );
}

export function CardsSkeleton({ count = 4, height = 120 }: { count?: number; height?: number }) {
  return (
    <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: `repeat(${count}, 1fr)` } }}>
      {Array.from({ length: count }).map((_, i) => (
        <Skeleton key={i} variant="rounded" height={height} sx={{ borderRadius: 5 }} />
      ))}
    </Box>
  );
}
