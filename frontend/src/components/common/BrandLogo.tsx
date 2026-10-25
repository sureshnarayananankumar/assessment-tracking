import { Box, Stack, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';

interface BrandLogoProps {
  /** Height of the logo in pixels (the wordmark is ~4:1, the mark is square). */
  size?: number;
  /** Full Business Gateways International wordmark (true) or the square mark only (false). */
  withText?: boolean;
  /** Show the "AssessHub" product line under the wordmark. */
  product?: boolean;
  subtitle?: string;
}

/**
 * Business Gateways International brand logo. Dark mode uses a variant with light lettering
 * (the blue wordmark would be unreadable on dark glass); the orange and the blue mark stay on-brand.
 */
export function BrandLogo({ size = 36, withText = true, product = true, subtitle }: BrandLogoProps) {
  const theme = useTheme();
  const dark = theme.palette.mode === 'dark';
  if (!withText) return <BrandMark size={size} />;
  return (
    <Stack spacing={0.75} alignItems="flex-start" sx={{ minWidth: 0 }}>
      <Box
        component="img"
        src={dark ? '/brand/bgi-logo-dark.svg' : '/brand/bgi-logo-light.svg'}
        alt="Business Gateways International"
        sx={{ height: size, width: 'auto', maxWidth: '100%', display: 'block', userSelect: 'none' }}
        draggable={false}
      />
      {product && (
        <Stack direction="row" spacing={0.75} alignItems="center" sx={{ pl: `${size * 0.02}px` }}>
          <Box sx={{ width: 6, height: 6, borderRadius: '50%', bgcolor: 'secondary.main', boxShadow: `0 0 10px ${theme.palette.secondary.main}` }} />
          <Typography variant="caption" sx={{ fontWeight: 800, letterSpacing: '0.16em', textTransform: 'uppercase', lineHeight: 1.2 }}>
            AssessHub
          </Typography>
          {subtitle && (
            <Typography variant="caption" color="text.secondary" noWrap sx={{ lineHeight: 1.2 }}>
              · {subtitle}
            </Typography>
          )}
        </Stack>
      )}
    </Stack>
  );
}

/** Square BGI mark (blue square, white circle) with a soft brand glow. */
export function BrandMark({ size = 36 }: { size?: number }) {
  const theme = useTheme();
  return (
    <Box
      role="img"
      aria-label="Business Gateways International"
      sx={{
        width: size,
        height: size,
        flexShrink: 0,
        boxShadow: theme.brand.glow,
        position: 'relative',
        '& svg': { display: 'block' },
        '&::after': {
          content: '""',
          position: 'absolute',
          inset: 0,
          background: 'linear-gradient(160deg, rgba(255,255,255,0.28) 0%, transparent 42%)',
          pointerEvents: 'none',
        },
      }}
    >
      <svg width={size} height={size} viewBox="0 0 198.308 198.308" aria-hidden>
        <rect width="198.308" height="198.308" fill={theme.brand.blue} />
        <circle cx="99.16" cy="99.15" r="64.01" fill="#FFFFFF" />
      </svg>
    </Box>
  );
}
