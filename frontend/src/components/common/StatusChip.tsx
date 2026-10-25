import { Chip, type ChipProps } from '@mui/material';
import { alpha } from '@mui/material/styles';
import { STATUS_COLOR, humanize } from '../../utils/labels';

interface StatusChipProps extends Omit<ChipProps, 'color' | 'label'> {
  status?: string | null;
  label?: string;
}

/** Soft, tinted status pill whose colour is derived from the status value. */
export function StatusChip({ status, label, sx, ...rest }: StatusChipProps) {
  const color = (status && STATUS_COLOR[status]) || 'default';
  return (
    <Chip
      size="small"
      label={label ?? humanize(status)}
      {...rest}
      sx={[
        (t) => {
          const c = color === 'default' ? t.palette.text.secondary : t.palette[color].main;
          return {
            color: c,
            backgroundColor: alpha(c, t.palette.mode === 'dark' ? 0.14 : 0.1),
            border: `1px solid ${alpha(c, 0.32)}`,
            fontWeight: 650,
            '& .MuiChip-icon': { color: c },
          };
        },
        ...(Array.isArray(sx) ? sx : [sx]),
      ]}
    />
  );
}
