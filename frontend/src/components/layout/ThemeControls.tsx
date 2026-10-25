import { DarkModeRounded, LightModeRounded, PaletteRounded, CheckRounded } from '@mui/icons-material';
import { Box, IconButton, Menu, Stack, ToggleButton, ToggleButtonGroup, Tooltip, Typography } from '@mui/material';
import { useState } from 'react';
import { useThemeSettings } from '../../theme/ThemeContext';
import { ACCENTS, type AccentName, type GlassIntensity, type ThemeMode } from '../../theme/tokens';

export function ThemeModeToggle() {
  const { resolvedMode, toggleMode } = useThemeSettings();
  return (
    <Tooltip title={resolvedMode === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}>
      <IconButton onClick={toggleMode} aria-label="Toggle colour mode">
        {resolvedMode === 'dark' ? <LightModeRounded /> : <DarkModeRounded />}
      </IconButton>
    </Tooltip>
  );
}

/** Accent colour, glass intensity and mode picker. */
export function AppearancePicker({ inline = false }: { inline?: boolean }) {
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const content = <AppearanceOptions />;
  if (inline) return content;
  return (
    <>
      <Tooltip title="Appearance">
        <IconButton onClick={(e) => setAnchor(e.currentTarget)} aria-label="Appearance settings">
          <PaletteRounded />
        </IconButton>
      </Tooltip>
      <Menu anchorEl={anchor} open={Boolean(anchor)} onClose={() => setAnchor(null)} slotProps={{ paper: { sx: { p: 2, width: 300 } } }}>
        {content}
      </Menu>
    </>
  );
}

export function AppearanceOptions() {
  const { mode, setMode, accent, setAccent, intensity, setIntensity } = useThemeSettings();
  return (
    <Stack spacing={2.25}>
      <Box>
        <Typography variant="overline" color="text.secondary">Mode</Typography>
        <ToggleButtonGroup exclusive fullWidth size="small" value={mode} onChange={(_, v: ThemeMode | null) => v && setMode(v)}>
          <ToggleButton value="dark">Dark</ToggleButton>
          <ToggleButton value="light">Light</ToggleButton>
          <ToggleButton value="system">System</ToggleButton>
        </ToggleButtonGroup>
      </Box>
      <Box>
        <Typography variant="overline" color="text.secondary">Accent</Typography>
        <Stack direction="row" spacing={1.25}>
          {(Object.keys(ACCENTS) as AccentName[]).map((name) => (
            <Tooltip key={name} title={ACCENTS[name].label}>
              <Box
                role="button"
                tabIndex={0}
                aria-label={`${ACCENTS[name].label} accent`}
                onClick={() => setAccent(name)}
                onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && setAccent(name)}
                sx={{
                  width: 40,
                  height: 40,
                  borderRadius: 3,
                  background: ACCENTS[name].gradient,
                  cursor: 'pointer',
                  display: 'grid',
                  placeItems: 'center',
                  color: '#fff',
                  outline: accent === name ? '2px solid' : 'none',
                  outlineColor: 'text.primary',
                  outlineOffset: 2,
                  transition: 'transform .15s ease',
                  '&:hover': { transform: 'scale(1.08)' },
                }}
              >
                {accent === name && <CheckRounded fontSize="small" />}
              </Box>
            </Tooltip>
          ))}
        </Stack>
      </Box>
      <Box>
        <Typography variant="overline" color="text.secondary">Glass</Typography>
        <ToggleButtonGroup exclusive fullWidth size="small" value={intensity} onChange={(_, v: GlassIntensity | null) => v && setIntensity(v)}>
          <ToggleButton value="subtle">Frosted</ToggleButton>
          <ToggleButton value="balanced">Balanced</ToggleButton>
          <ToggleButton value="vivid">Liquid</ToggleButton>
        </ToggleButtonGroup>
      </Box>
    </Stack>
  );
}
