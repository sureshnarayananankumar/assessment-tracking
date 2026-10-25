import { Box, Card, Stack, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import type { ReactNode } from 'react';

type Tone = 'primary' | 'secondary' | 'success' | 'warning' | 'error' | 'info';

interface StatCardProps {
  label: string;
  value: ReactNode;
  icon: ReactNode;
  tone?: Tone;
  hint?: ReactNode;
  onClick?: () => void;
}

export function StatCard({ label, value, icon, tone = 'primary', hint, onClick }: StatCardProps) {
  const theme = useTheme();
  const color = theme.palette[tone].main;
  return (
    <Card
      onClick={onClick}
      sx={{
        p: 2.25,
        cursor: onClick ? 'pointer' : 'default',
        transition: 'transform .2s ease, box-shadow .2s ease',
        '&:hover': onClick ? { transform: 'translateY(-3px)', boxShadow: `${theme.glass.shadow}, 0 12px 32px -14px ${alpha(color, 0.7)}` } : undefined,
        '&::after': {
          content: '""',
          position: 'absolute',
          width: 140,
          height: 140,
          right: -50,
          top: -60,
          borderRadius: '50%',
          background: `radial-gradient(circle, ${alpha(color, 0.35)} 0%, transparent 70%)`,
          pointerEvents: 'none',
        },
      }}
    >
      <Stack direction="row" spacing={2} alignItems="center" sx={{ position: 'relative' }}>
        <Box
          sx={{
            width: 46,
            height: 46,
            borderRadius: 3.5,
            display: 'grid',
            placeItems: 'center',
            color,
            background: alpha(color, theme.palette.mode === 'dark' ? 0.16 : 0.12),
            border: `1px solid ${alpha(color, 0.3)}`,
            flexShrink: 0,
          }}
        >
          {icon}
        </Box>
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="body2" color="text.secondary" noWrap>{label}</Typography>
          <Typography variant="h5" sx={{ fontWeight: 800, lineHeight: 1.2 }}>{value}</Typography>
          {hint && <Typography variant="caption" color="text.secondary">{hint}</Typography>}
        </Box>
      </Stack>
    </Card>
  );
}
