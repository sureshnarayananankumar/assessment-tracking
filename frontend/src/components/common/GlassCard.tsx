import { Box, Card, CardContent, Stack, Typography, type CardProps } from '@mui/material';
import type { ReactNode } from 'react';

interface GlassCardProps extends Omit<CardProps, 'title'> {
  title?: ReactNode;
  subtitle?: ReactNode;
  action?: ReactNode;
  /** Adds a soft gradient glow behind the card for emphasis. */
  glow?: boolean;
  noPadding?: boolean;
  children?: ReactNode;
}

/** Frosted-glass card with optional header, used as the main content container everywhere. */
export function GlassCard({ title, subtitle, action, glow, noPadding, children, sx, ...rest }: GlassCardProps) {
  return (
    <Card
      {...rest}
      sx={[
        glow ? (t) => ({ boxShadow: `${t.glass.shadow}, ${t.brand.glow}` }) : {},
        ...(Array.isArray(sx) ? sx : [sx]),
      ]}
    >
      {(title || subtitle || action) && (
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          alignItems={{ xs: 'flex-start', sm: 'center' }}
          justifyContent="space-between"
          spacing={1.5}
          sx={{ px: 2.75, pt: 2.5, pb: noPadding ? 2 : 0, position: 'relative' }}
        >
          <Box sx={{ minWidth: 0 }}>
            {title && <Typography variant="h6" sx={{ fontSize: 17 }}>{title}</Typography>}
            {subtitle && <Typography variant="body2" color="text.secondary">{subtitle}</Typography>}
          </Box>
          {action && <Box sx={{ flexShrink: 0 }}>{action}</Box>}
        </Stack>
      )}
      {noPadding ? <Box sx={{ position: 'relative' }}>{children}</Box> : <CardContent>{children}</CardContent>}
    </Card>
  );
}
