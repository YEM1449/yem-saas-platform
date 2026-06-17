/**
 * Africa/Casablanca timezone helpers for the Visites module (RG-V10).
 *
 * Visits are stored as instants (TIMESTAMPTZ) on the backend. Agents always think in
 * Casablanca wall-clock time, so the UI must:
 *   - convert a `datetime-local` wall-clock string the agent types → a UTC instant (ISO), and
 *   - render an instant back into Casablanca wall-clock for display.
 *
 * Morocco runs at UTC+1 year-round (DST effectively suspended), but we never hardcode the
 * offset: it is derived from `Intl` per date so the code stays correct if the rules change.
 */

const TZ = 'Africa/Casablanca';

/** Minutes that Casablanca is ahead of UTC at the given instant (e.g. +60). */
function offsetMinutes(at: Date): number {
  // Format the instant as Casablanca wall-clock, re-read it as if it were UTC, and diff.
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: TZ,
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
    hour12: false,
  }).formatToParts(at);
  const get = (t: string) => Number(parts.find(p => p.type === t)?.value);
  const asUtc = Date.UTC(
    get('year'), get('month') - 1, get('day'),
    get('hour') % 24, get('minute'), get('second'),
  );
  return Math.round((asUtc - at.getTime()) / 60000);
}

/**
 * Interpret a `datetime-local` value (`"2026-06-20T14:30"`, no zone) as Casablanca wall-clock
 * and return the matching UTC instant as an ISO string for the API.
 */
export function casablancaWallToInstant(wall: string): string {
  if (!wall) return '';
  const naiveUtc = new Date(wall.length === 16 ? `${wall}:00Z` : `${wall}Z`);
  const off = offsetMinutes(naiveUtc);
  return new Date(naiveUtc.getTime() - off * 60000).toISOString();
}

/**
 * Render an instant as a `datetime-local`-compatible Casablanca wall-clock string,
 * e.g. for pre-filling the edit form.
 */
export function instantToCasablancaWall(iso: string): string {
  if (!iso) return '';
  const d = new Date(iso);
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: TZ,
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hour12: false,
  }).formatToParts(d);
  const get = (t: string) => parts.find(p => p.type === t)?.value ?? '';
  return `${get('year')}-${get('month')}-${get('day')}T${get('hour')}:${get('minute')}`;
}

/** Start of the Casablanca calendar day containing `ref`, as a UTC instant ISO string. */
export function casablancaDayStart(ref: Date): string {
  const day = casablancaDateParts(ref);
  return casablancaWallToInstant(`${day}T00:00`);
}

/** End of the Casablanca calendar day containing `ref` (exclusive next-midnight). */
export function casablancaDayEnd(ref: Date): string {
  const start = new Date(casablancaDayStart(ref));
  return new Date(start.getTime() + 24 * 3600 * 1000).toISOString();
}

/** `YYYY-MM-DD` of the Casablanca calendar day containing `ref`. */
export function casablancaDateParts(ref: Date): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: TZ, year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(ref);
}

/** Human time `HH:mm` (Casablanca) for an instant. */
export function formatHeure(iso: string): string {
  return new Intl.DateTimeFormat('fr-FR', {
    timeZone: TZ, hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(new Date(iso));
}

/** Human date `lun. 20 juin 2026` (Casablanca) for an instant. */
export function formatDateLong(iso: string): string {
  return new Intl.DateTimeFormat('fr-FR', {
    timeZone: TZ, weekday: 'short', day: 'numeric', month: 'long', year: 'numeric',
  }).format(new Date(iso));
}
