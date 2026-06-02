/**
 * KPI target registry — the single place every dashboard reads its thresholds.
 *
 * Per the Developer-OS framework (§7 reporting strategy): one canonical
 * definition/target per metric, so a role dashboard never invents its own bar.
 * These are sensible Moroccan-developer defaults; the intent is to source them
 * from société configuration later (a `GET /api/societes/{id}/kpi-targets`
 * endpoint) without touching the components that consume them.
 *
 * Each target carries its direction so tone helpers can be generic:
 *   direction 'higher' → at/above target is good
 *   direction 'lower'  → at/below target is good
 */
export interface KpiTarget {
  /** The pass threshold (in the metric's native unit). */
  target: number;
  /** The "warning" band edge between good and bad. */
  warn: number;
  direction: 'higher' | 'lower';
}

export const KPI_TARGETS = {
  /** Collection rate — % of billed collected (Finance). */
  collectionRate:   { target: 95, warn: 85, direction: 'higher' } as KpiTarget,
  /** DSO — days sales outstanding (Finance). */
  dsoDays:          { target: 60, warn: 90, direction: 'lower' } as KpiTarget,
  /** Commercial absorption — SOLD/(ACTIVE+RESERVED+SOLD) ×100. */
  absorptionPct:    { target: 70, warn: 40, direction: 'higher' } as KpiTarget,
  /** Average sale discount — % off list (Commercial, leakage). */
  discountPct:      { target: 5,  warn: 10, direction: 'lower' } as KpiTarget,
  /** Deposit → sale conversion — % (Commercial/Sales). */
  conversionPct:    { target: 60, warn: 40, direction: 'higher' } as KpiTarget,
  /** Monthly sales-target attainment — % of quota (CEO/Direction). */
  quotaAttainmentPct: { target: 100, warn: 80, direction: 'higher' } as KpiTarget,
  /** Cancellation rate over 90 days — % (CEO, lower is better). */
  cancellationPct:  { target: 10, warn: 20, direction: 'lower' } as KpiTarget,
} as const;

/**
 * Generic tone bucket for a value against a {@link KpiTarget}.
 * Returns 'good' | 'mid' | 'bad', or 'neutral' when the value is unknown.
 * `value` must already be in the target's unit (e.g. a 0–100 percentage).
 */
export function toneFor(value: number | null | undefined, t: KpiTarget): 'good' | 'mid' | 'bad' | 'neutral' {
  if (value == null) return 'neutral';
  if (t.direction === 'higher') {
    if (value >= t.target) return 'good';
    if (value >= t.warn) return 'mid';
    return 'bad';
  }
  // lower is better
  if (value <= t.target) return 'good';
  if (value <= t.warn) return 'mid';
  return 'bad';
}
