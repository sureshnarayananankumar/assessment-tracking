export type ThemeMode = 'dark' | 'light' | 'system';
export type ResolvedMode = 'dark' | 'light';
export type AccentName = 'bgi' | 'sunrise' | 'midnight';
export type GlassIntensity = 'subtle' | 'balanced' | 'vivid';

/** Official Business Gateways International logo colours. */
export const BGI = {
  blue: '#006DB7',
  orange: '#F48120',
  /** Lighter tints used where the brand colour sits on dark glass (WCAG contrast). */
  blueOnDark: '#3EA3EE',
  blueSoft: '#7CC2F5',
  orangeOnDark: '#FF9A45',
  navy: '#03111F',
} as const;

export interface AccentPalette {
  label: string;
  /** Main colour on light surfaces. */
  primary: string;
  /** Main colour on dark surfaces (brighter tint of the same hue). */
  primaryOnDark: string;
  primaryLight: string;
  secondary: string;
  secondaryOnDark: string;
  /** Primary buttons, active indicators, progress bars. */
  gradient: string;
  /** Colours of the animated liquid blobs behind the glass. */
  blobs: [string, string, string, string];
}

/** Every accent is derived from the BGI logo; "BGI Blue" is the default brand theme. */
export const ACCENTS: Record<AccentName, AccentPalette> = {
  bgi: {
    label: 'BGI Blue',
    primary: BGI.blue,
    primaryOnDark: BGI.blueOnDark,
    primaryLight: BGI.blueSoft,
    secondary: BGI.orange,
    secondaryOnDark: BGI.orangeOnDark,
    gradient: 'linear-gradient(135deg, #004B82 0%, #006DB7 45%, #1E95E3 100%)',
    blobs: ['#006DB7', '#0A4F8F', '#F48120', '#1E95E3'],
  },
  sunrise: {
    label: 'BGI Sunrise',
    primary: '#D9660B',
    primaryOnDark: BGI.orangeOnDark,
    primaryLight: '#FFB679',
    secondary: BGI.blue,
    secondaryOnDark: BGI.blueOnDark,
    gradient: 'linear-gradient(135deg, #E06A0E 0%, #F48120 50%, #FFA24D 100%)',
    blobs: ['#F48120', '#006DB7', '#C2560A', '#1E95E3'],
  },
  midnight: {
    label: 'BGI Midnight',
    primary: '#00599A',
    primaryOnDark: '#5AB2F2',
    primaryLight: '#9ED2F8',
    secondary: '#2C8ED6',
    secondaryOnDark: '#7CC2F5',
    gradient: 'linear-gradient(135deg, #003A66 0%, #006DB7 55%, #3EA3EE 100%)',
    blobs: ['#006DB7', '#003A66', '#1E95E3', '#0A4F8F'],
  },
};

export const GLASS_LEVELS: Record<GlassIntensity, { blur: number; dark: number; light: number }> = {
  subtle: { blur: 10, dark: 0.74, light: 0.8 },
  balanced: { blur: 18, dark: 0.56, light: 0.62 },
  vivid: { blur: 28, dark: 0.4, light: 0.44 },
};
