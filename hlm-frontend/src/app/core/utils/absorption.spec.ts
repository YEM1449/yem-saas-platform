import { absorptionRate, absorptionTone } from './absorption';

/**
 * Unit tests for the canonical absorption rate — the single source of truth shared
 * by project cards, the project Aperçu, the building view and the dashboard.
 * absorption = SOLD / (ACTIVE + RESERVED + SOLD) × 100.
 */
describe('absorptionRate', () => {
  it('returns null when there is no commercialised stock', () => {
    expect(absorptionRate(0, 0, 0)).toBeNull();
  });

  it('computes SOLD over (ACTIVE + RESERVED + SOLD)', () => {
    // 5 sold out of 10 commercialised → 50%
    expect(absorptionRate(3, 2, 5)).toBe(50);
  });

  it('returns 100 when every commercialised unit is sold', () => {
    expect(absorptionRate(0, 0, 8)).toBe(100);
  });

  it('returns 0 when nothing is sold yet', () => {
    expect(absorptionRate(4, 6, 0)).toBe(0);
  });

  it('excludes DRAFT/WITHDRAWN from the base (only the three args count)', () => {
    // caller already filters; with 1 active + 0 reserved + 1 sold → 50%
    expect(absorptionRate(1, 0, 1)).toBe(50);
  });
});

describe('absorptionTone', () => {
  it('returns empty string for null', () => {
    expect(absorptionTone(null)).toBe('');
  });

  it('returns good at or above 70', () => {
    expect(absorptionTone(70)).toBe('good');
    expect(absorptionTone(95)).toBe('good');
  });

  it('returns mid between 40 and 69', () => {
    expect(absorptionTone(40)).toBe('mid');
    expect(absorptionTone(69.9)).toBe('mid');
  });

  it('returns low below 40', () => {
    expect(absorptionTone(0)).toBe('low');
    expect(absorptionTone(39.9)).toBe('low');
  });
});
