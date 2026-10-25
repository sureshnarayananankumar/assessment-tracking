import { keyframes } from '@emotion/react';
import { Box, useMediaQuery } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useThemeSettings } from './ThemeContext';

const morph = keyframes`
  0%   { border-radius: 42% 58% 70% 30% / 45% 45% 55% 55%; transform: translate3d(0, 0, 0) rotate(0deg) scale(1); }
  33%  { border-radius: 70% 30% 46% 54% / 30% 39% 61% 70%; transform: translate3d(6vw, -4vh, 0) rotate(120deg) scale(1.08); }
  66%  { border-radius: 28% 72% 35% 65% / 59% 30% 70% 41%; transform: translate3d(-5vw, 5vh, 0) rotate(240deg) scale(0.94); }
  100% { border-radius: 42% 58% 70% 30% / 45% 45% 55% 55%; transform: translate3d(0, 0, 0) rotate(360deg) scale(1); }
`;

const drift = keyframes`
  0%, 100% { transform: translate3d(0, 0, 0); }
  50%      { transform: translate3d(-4vw, 3vh, 0); }
`;

const BLOBS = [
  { top: '-12%', left: '-8%', size: '46vmax', duration: 34 },
  { top: '35%', left: '62%', size: '40vmax', duration: 42 },
  { top: '62%', left: '-12%', size: '36vmax', duration: 38 },
  { top: '-18%', left: '55%', size: '30vmax', duration: 46 },
];

/**
 * Animated "liquid" colour field rendered behind every glass surface.
 * `calm` lowers saturation/motion for the distraction-free assessment screen.
 */
export function LiquidBackground({ calm = false }: { calm?: boolean }) {
  const theme = useTheme();
  const { reduceMotion } = useThemeSettings();
  const systemReduced = useMediaQuery('(prefers-reduced-motion: reduce)');
  const still = reduceMotion || systemReduced;
  const dark = theme.palette.mode === 'dark';
  const opacity = calm ? (dark ? 0.28 : 0.22) : dark ? 0.55 : 0.4;

  return (
    <Box
      aria-hidden
      sx={{
        position: 'fixed',
        inset: 0,
        zIndex: -1,
        overflow: 'hidden',
        pointerEvents: 'none',
        background: dark
          ? 'radial-gradient(1200px 800px at 10% -10%, #0B2B4D 0%, #03111F 55%), #03111F'
          : 'radial-gradient(1200px 800px at 10% -10%, #ffffff 0%, #EEF5FC 60%), #EEF5FC',
      }}
    >
      {BLOBS.map((blob, i) => (
        <Box
          key={i}
          sx={{
            position: 'absolute',
            top: blob.top,
            left: blob.left,
            width: blob.size,
            height: blob.size,
            opacity,
            filter: `blur(${calm ? 90 : 70}px)`,
            background: `radial-gradient(circle at 30% 30%, ${theme.brand.blobs[i]} 0%, transparent 70%)`,
            mixBlendMode: dark ? 'screen' : 'multiply',
            animation: still ? 'none' : `${morph} ${blob.duration}s ease-in-out infinite, ${drift} ${blob.duration * 0.7}s ease-in-out infinite`,
            willChange: still ? 'auto' : 'transform, border-radius',
          }}
        />
      ))}
      {/* fine grid + grain give the glass something to refract */}
      <Box
        sx={{
          position: 'absolute',
          inset: 0,
          opacity: dark ? 0.35 : 0.5,
          backgroundImage: dark
            ? 'linear-gradient(rgba(124,194,245,0.05) 1px, transparent 1px), linear-gradient(90deg, rgba(124,194,245,0.05) 1px, transparent 1px)'
            : 'linear-gradient(rgba(0,109,183,0.05) 1px, transparent 1px), linear-gradient(90deg, rgba(0,109,183,0.05) 1px, transparent 1px)',
          backgroundSize: '56px 56px',
          maskImage: 'radial-gradient(ellipse at 50% 30%, black 20%, transparent 75%)',
          WebkitMaskImage: 'radial-gradient(ellipse at 50% 30%, black 20%, transparent 75%)',
        }}
      />
      <Box
        sx={{
          position: 'absolute',
          inset: 0,
          opacity: dark ? 0.06 : 0.04,
          backgroundImage:
            "url(\"data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='160' height='160'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='.85' numOctaves='3' stitchTiles='stitch'/></filter><rect width='100%' height='100%' filter='url(%23n)'/></svg>\")",
        }}
      />
    </Box>
  );
}
