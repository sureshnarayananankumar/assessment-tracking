import { Box } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { useRef, type ChangeEvent, type KeyboardEvent, type UIEvent } from 'react';

interface Props {
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  minRows?: number;
  ariaLabel?: string;
}

/**
 * Lightweight code editor (monospace textarea with line numbers and tab indentation).
 * Submitted code is stored for manual review and is never executed by the platform.
 */
export function CodeEditor({ value, onChange, disabled, minRows = 14, ariaLabel = 'Code answer' }: Props) {
  const theme = useTheme();
  const ref = useRef<HTMLTextAreaElement>(null);
  const gutterRef = useRef<HTMLDivElement>(null);
  const lines = Math.max(minRows, value.split('\n').length);
  const dark = theme.palette.mode === 'dark';

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key !== 'Tab' || e.ctrlKey || e.metaKey || e.altKey) return;
    // Tab indents; Shift+Tab or Escape then Tab lets keyboard users leave the editor.
    if (e.shiftKey) return;
    e.preventDefault();
    const el = e.currentTarget;
    const { selectionStart: start, selectionEnd: end } = el;
    const next = `${value.slice(0, start)}  ${value.slice(end)}`;
    onChange(next);
    requestAnimationFrame(() => {
      el.selectionStart = el.selectionEnd = start + 2;
    });
  };

  return (
    <Box
      sx={{
        display: 'flex',
        borderRadius: 3,
        overflow: 'hidden',
        border: `1px solid ${theme.palette.divider}`,
        bgcolor: dark ? 'rgba(2, 6, 23, 0.65)' : 'rgba(248, 250, 252, 0.9)',
        fontFamily: '"JetBrains Mono", ui-monospace, monospace',
        fontSize: 13.5,
        lineHeight: '21px',
        '&:focus-within': { boxShadow: `0 0 0 4px ${alpha(theme.palette.primary.main, 0.2)}`, borderColor: theme.palette.primary.main },
      }}
    >
      <Box
        ref={gutterRef}
        aria-hidden
        sx={{
          py: 1.5,
          px: 1.25,
          textAlign: 'right',
          color: 'text.secondary',
          opacity: 0.6,
          userSelect: 'none',
          borderRight: `1px solid ${theme.palette.divider}`,
          overflow: 'hidden',
          minWidth: 44,
        }}
      >
        {Array.from({ length: lines }, (_, i) => <div key={i}>{i + 1}</div>)}
      </Box>
      <Box
        component="textarea"
        ref={ref}
        value={value}
        disabled={disabled}
        spellCheck={false}
        aria-label={ariaLabel}
        rows={lines}
        onChange={(e: ChangeEvent<HTMLTextAreaElement>) => onChange(e.target.value)}
        onKeyDown={onKeyDown}
        onScroll={(e: UIEvent<HTMLTextAreaElement>) => {
          if (gutterRef.current) gutterRef.current.scrollTop = e.currentTarget.scrollTop;
        }}
        sx={{
          flex: 1,
          py: 1.5,
          px: 1.75,
          border: 0,
          outline: 0,
          resize: 'vertical',
          background: 'transparent',
          color: 'text.primary',
          font: 'inherit',
          whiteSpace: 'pre',
          overflowX: 'auto',
          tabSize: 2,
        }}
      />
    </Box>
  );
}
