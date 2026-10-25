import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import utc from 'dayjs/plugin/utc';

dayjs.extend(relativeTime);
dayjs.extend(utc);

/** Server values are UTC instants; everything is displayed in the browser's local time zone. */
export function formatDateTime(iso?: string | null): string {
  if (!iso) return '—';
  return dayjs(iso).format('DD MMM YYYY, HH:mm');
}

export function formatDate(iso?: string | null): string {
  if (!iso) return '—';
  return dayjs(iso).format('DD MMM YYYY');
}

export function formatTime(iso?: string | null): string {
  if (!iso) return '—';
  return dayjs(iso).format('HH:mm:ss');
}

export function fromNow(iso?: string | null): string {
  if (!iso) return '';
  return dayjs(iso).fromNow();
}

/** Converts an ISO instant to the value expected by <input type="datetime-local"> (local time). */
export function toLocalInput(iso?: string | null): string {
  if (!iso) return '';
  return dayjs(iso).format('YYYY-MM-DDTHH:mm');
}

/** Converts a datetime-local value (local time) to an ISO UTC instant. */
export function fromLocalInput(value?: string | null): string | null {
  if (!value) return null;
  const d = dayjs(value);
  return d.isValid() ? d.toISOString() : null;
}

export function formatDuration(minutes: number): string {
  if (minutes < 60) return `${minutes} min`;
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return m ? `${h} h ${m} min` : `${h} h`;
}

/** 3725 -> "01:02:05"; under an hour -> "02:05". */
export function formatClock(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  const pad = (n: number) => String(n).padStart(2, '0');
  return h > 0 ? `${pad(h)}:${pad(m)}:${pad(sec)}` : `${pad(m)}:${pad(sec)}`;
}

export function formatMs(ms?: number | null): string {
  if (!ms) return '0s';
  const s = Math.round(ms / 1000);
  if (s < 60) return `${s}s`;
  const m = Math.floor(s / 60);
  return `${m}m ${s % 60}s`;
}

export function formatBytes(bytes?: number | null): string {
  if (!bytes) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  let i = 0;
  let v = bytes;
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024;
    i++;
  }
  return `${v.toFixed(i === 0 ? 0 : 1)} ${units[i]}`;
}

export function formatScore(score?: number | null, max?: number | null): string {
  if (score === undefined || score === null) return '—';
  const s = Number(score);
  return max !== undefined && max !== null ? `${trim(s)} / ${trim(Number(max))}` : trim(s);
}

export function formatPercent(p?: number | null): string {
  if (p === undefined || p === null) return '—';
  return `${trim(Number(p))}%`;
}

function trim(n: number): string {
  return Number.isInteger(n) ? String(n) : n.toFixed(2).replace(/0+$/, '').replace(/\.$/, '');
}

export function initials(name?: string): string {
  if (!name) return '?';
  return name
    .split(/[\s@._-]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]!.toUpperCase())
    .join('');
}
