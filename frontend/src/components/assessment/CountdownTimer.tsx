import { keyframes } from '@emotion/react';
import { TimerRounded } from '@mui/icons-material';
import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { formatClock } from '../../utils/format';

const pulse = keyframes`
  0%, 100% { box-shadow: 0 0 0 0 rgba(248, 113, 113, 0.55); }
  50% { box-shadow: 0 0 0 10px rgba(248, 113, 113, 0); }
`;

export type TimerLevel = 'normal' | 'warning' | 'critical';

export function timerLevel(secondsLeft: number): TimerLevel {
  if (secondsLeft <= 60) return 'critical';
  if (secondsLeft <= 300) return 'warning';
  return 'normal';
}

/** Pill-shaped countdown that changes colour at 5 minutes and pulses during the final minute. */
export function CountdownTimer({ secondsLeft }: { secondsLeft: number }) {
  const theme = useTheme();
  const level = timerLevel(secondsLeft);
  const color = level === 'critical' ? theme.palette.error.main : level === 'warning' ? theme.palette.warning.main : theme.palette.primary.light;
  return (
    <Box
      role="timer"
      aria-live={level === 'normal' ? 'off' : 'polite'}
      aria-label={`Time remaining ${formatClock(secondsLeft)}`}
      data-level={level}
      sx={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 1,
        px: 2,
        py: 0.75,
        borderRadius: 999,
        color,
        border: `1px solid ${alpha(color, 0.45)}`,
        background: alpha(color, theme.palette.mode === 'dark' ? 0.14 : 0.1),
        backdropFilter: 'blur(10px)',
        animation: level === 'critical' ? `${pulse} 1.4s ease-in-out infinite` : 'none',
      }}
    >
      <TimerRounded fontSize="small" />
      <Typography component="span" sx={{ fontWeight: 800, fontSize: 18, fontVariantNumeric: 'tabular-nums', letterSpacing: '0.02em' }}>
        {formatClock(secondsLeft)}
      </Typography>
    </Box>
  );
}
