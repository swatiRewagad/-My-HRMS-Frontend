/**
 * The complaints endpoint sends dd-MM-yyyy while drafts send an ISO timestamp. new Date() returns
 * Invalid Date for the former, so every date consumer must go through here.
 */
export function parseComplaintDate(value: string | null | undefined): Date | null {
  if (!value || value === '—') return null;
  const dmy = /^(\d{2})[-/](\d{2})[-/](\d{4})/.exec(value);
  if (dmy) return new Date(+dmy[3], +dmy[2] - 1, +dmy[1]);
  const parsed = new Date(value);
  return isNaN(parsed.getTime()) ? null : parsed;
}

/** Calendar-day key in the local timezone, so filtering never round-trips through UTC. */
export function toDayKey(date: Date | null): string {
  if (!date) return '';
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0')
  ].join('-');
}

export function formatComplaintDate(value: string | null | undefined): string {
  const date = parseComplaintDate(value);
  if (!date) return '—';
  return [
    String(date.getDate()).padStart(2, '0'),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getFullYear())
  ].join('/');
}
