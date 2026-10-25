import {
  Box, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, TableSortLabel, Typography,
  LinearProgress,
} from '@mui/material';
import type { ReactNode } from 'react';

export interface Column<T> {
  key: string;
  label: string;
  render: (row: T) => ReactNode;
  /** Backend property used for server-side sorting. */
  sortField?: string;
  align?: 'left' | 'right' | 'center';
  width?: number | string;
  /** Hide on small screens to keep tables readable on phones. */
  hideBelow?: 'sm' | 'md' | 'lg';
}

export interface SortState {
  field: string;
  direction: 'asc' | 'desc';
}

interface DataTableProps<T> {
  columns: Column<T>[];
  rows: T[];
  getRowId: (row: T) => string | number;
  loading?: boolean;
  total?: number;
  page?: number;
  size?: number;
  onPageChange?: (page: number) => void;
  onSizeChange?: (size: number) => void;
  sort?: SortState;
  onSortChange?: (sort: SortState) => void;
  onRowClick?: (row: T) => void;
  emptyMessage?: ReactNode;
  dense?: boolean;
}

/** Server-side paginated, sortable table that scrolls horizontally on narrow screens. */
export function DataTable<T>({
  columns, rows, getRowId, loading, total, page = 0, size = 20, onPageChange, onSizeChange, sort, onSortChange,
  onRowClick, emptyMessage = 'Nothing to show yet.', dense,
}: DataTableProps<T>) {
  const display = (c: Column<T>) =>
    c.hideBelow ? { display: { xs: 'none', [c.hideBelow]: 'table-cell' } } : undefined;

  return (
    <Box sx={{ position: 'relative' }}>
      {loading && <LinearProgress sx={{ position: 'absolute', top: 0, left: 0, right: 0, height: 3, borderRadius: 0, zIndex: 1 }} />}
      <TableContainer sx={{ overflowX: 'auto' }}>
        <Table size={dense ? 'small' : 'medium'} sx={{ minWidth: 640 }}>
          <TableHead>
            <TableRow>
              {columns.map((c) => (
                <TableCell key={c.key} align={c.align} sx={{ width: c.width, ...display(c) }}>
                  {c.sortField && onSortChange ? (
                    <TableSortLabel
                      active={sort?.field === c.sortField}
                      direction={sort?.field === c.sortField ? sort.direction : 'asc'}
                      onClick={() =>
                        onSortChange({
                          field: c.sortField!,
                          direction: sort?.field === c.sortField && sort?.direction === 'asc' ? 'desc' : 'asc',
                        })
                      }
                    >
                      {c.label}
                    </TableSortLabel>
                  ) : (
                    c.label
                  )}
                </TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row) => (
              <TableRow
                key={getRowId(row)}
                hover
                onClick={onRowClick ? () => onRowClick(row) : undefined}
                sx={{ cursor: onRowClick ? 'pointer' : 'default' }}
              >
                {columns.map((c) => (
                  <TableCell key={c.key} align={c.align} sx={display(c)}>
                    {c.render(row)}
                  </TableCell>
                ))}
              </TableRow>
            ))}
            {!loading && rows.length === 0 && (
              <TableRow>
                <TableCell colSpan={columns.length} sx={{ py: 6, textAlign: 'center', borderBottom: 0 }}>
                  <Typography color="text.secondary">{emptyMessage}</Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>
      {onPageChange && total !== undefined && (
        <TablePagination
          component="div"
          count={total}
          page={page}
          rowsPerPage={size}
          rowsPerPageOptions={[10, 20, 50, 100]}
          onPageChange={(_, p) => onPageChange(p)}
          onRowsPerPageChange={(e) => onSizeChange?.(parseInt(e.target.value, 10))}
        />
      )}
    </Box>
  );
}

export function sortParam(sort?: SortState): string | undefined {
  return sort ? `${sort.field},${sort.direction}` : undefined;
}
