import '@mui/material/styles';

declare module '@mui/material/styles' {
  interface GlassTokens {
    background: string;
    backgroundStrong: string;
    border: string;
    highlight: string;
    blur: number;
    shadow: string;
  }

  interface BrandTokens {
    gradient: string;
    blobs: [string, string, string, string];
    glow: string;
    /** Orange brand accent gradient (BGI logo orange). */
    accent: string;
    blue: string;
    orange: string;
  }

  interface Theme {
    glass: GlassTokens;
    brand: BrandTokens;
  }

  interface ThemeOptions {
    glass?: GlassTokens;
    brand?: BrandTokens;
  }
}
