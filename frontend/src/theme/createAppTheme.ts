import { alpha, createTheme, type Theme } from '@mui/material/styles';
import { ACCENTS, GLASS_LEVELS, type AccentName, type GlassIntensity, type ResolvedMode } from './tokens';

/**
 * Builds the "liquid glass" design system: translucent frosted surfaces over an animated
 * gradient backdrop, hairline light borders, specular highlights and soft depth.
 */
export function createAppTheme(mode: ResolvedMode, accentName: AccentName, intensity: GlassIntensity): Theme {
  const accent = ACCENTS[accentName];
  const level = GLASS_LEVELS[intensity];
  const dark = mode === 'dark';
  // Brand blue on light surfaces; a brighter tint of the same hue on dark glass for contrast.
  const primaryMain = dark ? accent.primaryOnDark : accent.primary;
  const secondaryMain = dark ? accent.secondaryOnDark : accent.secondary;

  // Glass tinted towards BGI navy in dark mode, crisp white in light mode.
  const surfaceRgb = dark ? '6, 22, 40' : '255, 255, 255';
  const glassAlpha = dark ? level.dark : level.light;
  const glass = {
    background: `rgba(${surfaceRgb}, ${glassAlpha})`,
    backgroundStrong: `rgba(${surfaceRgb}, ${Math.min(0.92, glassAlpha + 0.3)})`,
    border: dark ? 'rgba(160, 205, 245, 0.12)' : 'rgba(255, 255, 255, 0.75)',
    highlight: dark ? 'rgba(255, 255, 255, 0.07)' : 'rgba(255, 255, 255, 0.9)',
    blur: level.blur,
    shadow: dark
      ? '0 10px 40px -12px rgba(0, 8, 20, 0.7), inset 0 1px 0 rgba(180, 220, 255, 0.08)'
      : '0 10px 40px -16px rgba(0, 60, 110, 0.22), inset 0 1px 0 rgba(255, 255, 255, 0.9)',
  };
  const backdrop = `blur(${glass.blur}px) saturate(165%)`;
  const textPrimary = dark ? '#E9F2FB' : '#0B1F33';
  const textSecondary = dark ? '#93A9C2' : '#4A5D73';
  const divider = dark ? 'rgba(255, 255, 255, 0.08)' : 'rgba(15, 23, 42, 0.08)';

  const glassSurface = {
    backgroundColor: glass.background,
    backgroundImage: 'none',
    backdropFilter: backdrop,
    WebkitBackdropFilter: backdrop,
    border: `1px solid ${glass.border}`,
    boxShadow: glass.shadow,
  };

  return createTheme({
    glass,
    brand: {
      gradient: accent.gradient,
      blobs: accent.blobs,
      glow: `0 8px 30px -6px ${alpha(primaryMain, 0.55)}`,
      accent: `linear-gradient(135deg, ${secondaryMain} 0%, #FFB061 100%)`,
      blue: accent.primary,
      orange: accent.secondary,
    },
    palette: {
      mode,
      primary: { main: primaryMain, light: dark ? accent.primaryLight : accent.primary, contrastText: '#ffffff' },
      secondary: { main: secondaryMain, contrastText: '#ffffff' },
      success: { main: dark ? '#34D399' : '#059669' },
      warning: { main: dark ? '#FF9A45' : '#D9660B' },
      error: { main: dark ? '#F87171' : '#DC2626' },
      info: { main: dark ? '#7CC2F5' : '#006DB7' },
      background: { default: dark ? '#03111F' : '#EEF5FC', paper: glass.background },
      text: { primary: textPrimary, secondary: textSecondary },
      divider,
    },
    shape: { borderRadius: 14 },
    typography: {
      fontFamily: '"Inter", "Segoe UI", system-ui, -apple-system, sans-serif',
      h1: { fontWeight: 800, letterSpacing: '-0.03em' },
      h2: { fontWeight: 800, letterSpacing: '-0.025em' },
      h3: { fontWeight: 750, letterSpacing: '-0.02em' },
      h4: { fontWeight: 750, letterSpacing: '-0.02em' },
      h5: { fontWeight: 700, letterSpacing: '-0.01em' },
      h6: { fontWeight: 650 },
      subtitle1: { fontWeight: 600 },
      button: { fontWeight: 650, textTransform: 'none', letterSpacing: 0 },
      overline: { fontWeight: 700, letterSpacing: '0.12em' },
    },
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          ':root': { colorScheme: mode },
          body: {
            backgroundColor: dark ? '#03111F' : '#EEF5FC',
            minHeight: '100vh',
            WebkitFontSmoothing: 'antialiased',
          },
          '::selection': { background: alpha(primaryMain, 0.35) },
          '*::-webkit-scrollbar': { width: 10, height: 10 },
          '*::-webkit-scrollbar-thumb': {
            background: dark ? 'rgba(255,255,255,0.12)' : 'rgba(15,23,42,0.18)',
            borderRadius: 999,
            border: '2px solid transparent',
            backgroundClip: 'content-box',
          },
          '*::-webkit-scrollbar-track': { background: 'transparent' },
          code: { fontFamily: '"JetBrains Mono", ui-monospace, monospace' },
        },
      },
      MuiPaper: {
        defaultProps: { elevation: 0 },
        styleOverrides: { root: glassSurface },
      },
      MuiCard: {
        styleOverrides: {
          root: {
            ...glassSurface,
            borderRadius: 20,
            position: 'relative',
            overflow: 'hidden',
            // specular "liquid" highlight along the top edge
            '&::before': {
              content: '""',
              position: 'absolute',
              inset: 0,
              borderRadius: 'inherit',
              pointerEvents: 'none',
              background: `linear-gradient(180deg, ${glass.highlight} 0%, transparent 38%)`,
            },
          },
        },
      },
      MuiCardContent: { styleOverrides: { root: { position: 'relative', padding: 22, '&:last-child': { paddingBottom: 22 } } } },
      MuiAppBar: {
        defaultProps: { elevation: 0, color: 'transparent' },
        styleOverrides: { root: { ...glassSurface, borderTop: 0, borderLeft: 0, borderRight: 0, boxShadow: 'none' } },
      },
      MuiDrawer: {
        styleOverrides: { paper: { ...glassSurface, borderTop: 0, borderBottom: 0, borderLeft: 0, borderRadius: 0 } },
      },
      MuiDialog: {
        styleOverrides: {
          paper: { ...glassSurface, backgroundColor: glass.backgroundStrong, borderRadius: 22 },
        },
      },
      MuiBackdrop: {
        styleOverrides: {
          root: { backgroundColor: dark ? 'rgba(2, 6, 23, 0.55)' : 'rgba(15, 23, 42, 0.25)', backdropFilter: 'blur(4px)' },
          invisible: { backgroundColor: 'transparent', backdropFilter: 'none' },
        },
      },
      MuiPopover: { styleOverrides: { paper: { ...glassSurface, backgroundColor: glass.backgroundStrong } } },
      MuiMenu: { styleOverrides: { paper: { ...glassSurface, backgroundColor: glass.backgroundStrong } } },
      MuiTooltip: {
        styleOverrides: {
          tooltip: {
            backgroundColor: dark ? 'rgba(15, 20, 35, 0.92)' : 'rgba(15, 23, 42, 0.88)',
            backdropFilter: 'blur(8px)',
            border: `1px solid ${dark ? 'rgba(255,255,255,0.1)' : 'transparent'}`,
            fontSize: 12,
            borderRadius: 8,
          },
        },
      },
      MuiButton: {
        defaultProps: { disableElevation: true },
        styleOverrides: {
          root: { borderRadius: 12, paddingInline: 18, transition: 'transform .15s ease, box-shadow .2s ease, background .2s ease' },
          containedPrimary: {
            background: accent.gradient,
            boxShadow: `0 8px 24px -8px ${alpha(primaryMain, 0.7)}`,
            '&:hover': { background: accent.gradient, filter: 'brightness(1.08)', transform: 'translateY(-1px)', boxShadow: `0 12px 30px -8px ${alpha(primaryMain, 0.8)}` },
            '&.Mui-disabled': { background: dark ? 'rgba(255,255,255,0.08)' : 'rgba(15,23,42,0.08)' },
          },
          outlined: {
            borderColor: dark ? 'rgba(255,255,255,0.16)' : 'rgba(15,23,42,0.16)',
            backgroundColor: dark ? 'rgba(255,255,255,0.03)' : 'rgba(255,255,255,0.5)',
            backdropFilter: 'blur(8px)',
          },
          sizeLarge: { paddingBlock: 12, fontSize: 16 },
        },
      },
      MuiIconButton: {
        styleOverrides: {
          root: {
            borderRadius: 12,
            transition: 'background .2s ease, transform .15s ease',
            '&:hover': { backgroundColor: dark ? 'rgba(255,255,255,0.08)' : 'rgba(15,23,42,0.06)' },
          },
        },
      },
      MuiOutlinedInput: {
        styleOverrides: {
          root: {
            borderRadius: 12,
            backgroundColor: dark ? 'rgba(255,255,255,0.035)' : 'rgba(255,255,255,0.65)',
            transition: 'box-shadow .2s ease, background .2s ease',
            '& .MuiOutlinedInput-notchedOutline': { borderColor: dark ? 'rgba(255,255,255,0.12)' : 'rgba(15,23,42,0.14)' },
            '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: dark ? 'rgba(255,255,255,0.24)' : 'rgba(15,23,42,0.28)' },
            '&.Mui-focused': { boxShadow: `0 0 0 4px ${alpha(primaryMain, 0.18)}` },
          },
        },
      },
      MuiChip: {
        styleOverrides: {
          root: { borderRadius: 9, fontWeight: 600 },
          outlined: { borderColor: dark ? 'rgba(255,255,255,0.16)' : 'rgba(15,23,42,0.16)' },
        },
      },
      MuiTableCell: {
        styleOverrides: {
          root: { borderBottom: `1px solid ${divider}` },
          head: {
            fontSize: 12,
            fontWeight: 700,
            textTransform: 'uppercase',
            letterSpacing: '0.06em',
            color: textSecondary,
            backgroundColor: 'transparent',
          },
        },
      },
      MuiTableRow: {
        styleOverrides: {
          root: { transition: 'background .15s ease', '&.MuiTableRow-hover:hover': { backgroundColor: alpha(primaryMain, dark ? 0.08 : 0.05) } },
        },
      },
      MuiTabs: {
        styleOverrides: {
          indicator: { height: 3, borderRadius: 3, background: accent.gradient },
        },
      },
      MuiTab: { styleOverrides: { root: { textTransform: 'none', fontWeight: 650, minHeight: 48 } } },
      MuiLinearProgress: {
        styleOverrides: {
          root: { borderRadius: 999, height: 8, backgroundColor: dark ? 'rgba(255,255,255,0.08)' : 'rgba(15,23,42,0.08)' },
          bar: { borderRadius: 999, background: accent.gradient },
        },
      },
      MuiAlert: {
        styleOverrides: {
          root: { borderRadius: 14, backdropFilter: 'blur(12px)', border: `1px solid ${divider}` },
        },
      },
      MuiListItemButton: {
        styleOverrides: {
          root: {
            borderRadius: 12,
            position: 'relative',
            '&.Mui-selected': {
              backgroundColor: alpha(primaryMain, dark ? 0.18 : 0.12),
              // BGI orange accent bar on the active navigation item
              '&::before': { content: '""', position: 'absolute', left: 0, top: 8, bottom: 8, width: 3, borderRadius: 3, background: secondaryMain },
              '&:hover': { backgroundColor: alpha(primaryMain, dark ? 0.24 : 0.16) },
            },
          },
        },
      },
      MuiSwitch: {
        styleOverrides: {
          switchBase: { '&.Mui-checked + .MuiSwitch-track': { opacity: 1, background: accent.gradient } },
          track: { borderRadius: 999 },
        },
      },
      MuiSkeleton: { styleOverrides: { root: { backgroundColor: dark ? 'rgba(255,255,255,0.06)' : 'rgba(15,23,42,0.06)' } } },
    },
  });
}
