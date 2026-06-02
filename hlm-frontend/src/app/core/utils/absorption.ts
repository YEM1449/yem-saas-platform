/**
 * Canonical commercial absorption rate — the single source of truth for the
 * frontend, matching the backend definition documented in `HomeDashboardDTO`:
 *
 *   absorption = SOLD / (ACTIVE + RESERVED + SOLD) × 100
 *
 * "Absorbed" means actually sold, expressed as a share of commercialised stock
 * (DRAFT/WITHDRAWN/ARCHIVED are excluded from the base). Returns `null` when
 * there is no commercialised stock, so callers can render an em dash.
 *
 * Use this everywhere absorption is shown so a project never reads one number
 * on its card and a different one on the dashboard.
 */
export function absorptionRate(active: number, reserved: number, sold: number): number | null {
  const base = active + reserved + sold;
  if (base <= 0) return null;
  return (sold / base) * 100;
}

/** Tone bucket for a 0–100 absorption value (green ≥70, amber ≥40, else red). */
export function absorptionTone(pct: number | null): 'good' | 'mid' | 'low' | '' {
  if (pct == null) return '';
  if (pct >= 70) return 'good';
  if (pct >= 40) return 'mid';
  return 'low';
}
