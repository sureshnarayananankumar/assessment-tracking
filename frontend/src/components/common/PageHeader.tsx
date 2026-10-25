import { ArrowBackRounded } from '@mui/icons-material';
import { Box, IconButton, Stack, Tooltip, Typography } from '@mui/material';
import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';

interface PageHeaderProps {
  title: ReactNode;
  subtitle?: ReactNode;
  overline?: string;
  actions?: ReactNode;
  backTo?: string;
}

export function PageHeader({ title, subtitle, overline, actions, backTo }: PageHeaderProps) {
  const navigate = useNavigate();
  return (
    <Stack
      direction={{ xs: 'column', md: 'row' }}
      alignItems={{ xs: 'flex-start', md: 'flex-end' }}
      justifyContent="space-between"
      spacing={2}
      sx={{ mb: 3 }}
    >
      <Stack direction="row" spacing={1.5} alignItems="center" sx={{ minWidth: 0 }}>
        {backTo && (
          <Tooltip title="Back">
            <IconButton onClick={() => navigate(backTo)} aria-label="Back" sx={{ border: 1, borderColor: 'divider' }}>
              <ArrowBackRounded />
            </IconButton>
          </Tooltip>
        )}
        <Box sx={{ minWidth: 0 }}>
          {overline && (
            <Typography variant="overline" color="secondary.main" sx={{ display: 'block', lineHeight: 1.6 }}>
              {overline}
            </Typography>
          )}
          <Typography variant="h4" component="h1" sx={{ fontSize: { xs: 26, md: 32 }, wordBreak: 'break-word' }}>
            {title}
          </Typography>
          {subtitle && (
            <Typography variant="body1" color="text.secondary" sx={{ mt: 0.5 }}>
              {subtitle}
            </Typography>
          )}
        </Box>
      </Stack>
      {actions && (
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ rowGap: 1 }}>
          {actions}
        </Stack>
      )}
    </Stack>
  );
}
