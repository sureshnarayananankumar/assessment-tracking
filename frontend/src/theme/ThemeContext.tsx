import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { CssBaseline, ThemeProvider, useMediaQuery } from '@mui/material';
import { createAppTheme } from './createAppTheme';
import { ACCENTS, type AccentName, type GlassIntensity, type ResolvedMode, type ThemeMode } from './tokens';

interface ThemeSettings {
  mode: ThemeMode;
  accent: AccentName;
  intensity: GlassIntensity;
  reduceMotion: boolean;
}

interface ThemeSettingsContextValue extends ThemeSettings {
  resolvedMode: ResolvedMode;
  setMode: (mode: ThemeMode) => void;
  setAccent: (accent: AccentName) => void;
  setIntensity: (intensity: GlassIntensity) => void;
  setReduceMotion: (value: boolean) => void;
  toggleMode: () => void;
}

// v2: BGI brand theme - older saved accents are not carried over.
const STORAGE_KEY = 'assesshub.theme.v2';
const DEFAULTS: ThemeSettings = { mode: 'dark', accent: 'bgi', intensity: 'balanced', reduceMotion: false };

function loadSettings(): ThemeSettings {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const saved = raw ? { ...DEFAULTS, ...(JSON.parse(raw) as Partial<ThemeSettings>) } : DEFAULTS;
    return ACCENTS[saved.accent] ? saved : { ...saved, accent: DEFAULTS.accent };
  } catch {
    return DEFAULTS;
  }
}

const ThemeSettingsContext = createContext<ThemeSettingsContextValue | null>(null);

export function AppThemeProvider({ children }: { children: ReactNode }) {
  const [settings, setSettings] = useState<ThemeSettings>(loadSettings);
  const prefersDark = useMediaQuery('(prefers-color-scheme: dark)', { noSsr: true });
  const resolvedMode: ResolvedMode = settings.mode === 'system' ? (prefersDark ? 'dark' : 'light') : settings.mode;

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
    } catch {
      /* storage unavailable (private mode) - settings stay in memory */
    }
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', resolvedMode === 'dark' ? '#03111F' : '#EEF5FC');
  }, [settings, resolvedMode]);

  const theme = useMemo(
    () => createAppTheme(resolvedMode, settings.accent, settings.intensity),
    [resolvedMode, settings.accent, settings.intensity],
  );

  const value = useMemo<ThemeSettingsContextValue>(
    () => ({
      ...settings,
      resolvedMode,
      setMode: (mode) => setSettings((s) => ({ ...s, mode })),
      setAccent: (accent) => setSettings((s) => ({ ...s, accent })),
      setIntensity: (intensity) => setSettings((s) => ({ ...s, intensity })),
      setReduceMotion: (reduceMotion) => setSettings((s) => ({ ...s, reduceMotion })),
      toggleMode: () => setSettings((s) => ({ ...s, mode: resolvedMode === 'dark' ? 'light' : 'dark' })),
    }),
    [settings, resolvedMode],
  );

  return (
    <ThemeSettingsContext.Provider value={value}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </ThemeSettingsContext.Provider>
  );
}

export function useThemeSettings(): ThemeSettingsContextValue {
  const ctx = useContext(ThemeSettingsContext);
  if (!ctx) throw new Error('useThemeSettings must be used inside AppThemeProvider');
  return ctx;
}
