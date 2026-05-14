/**
 * Mesh-name normalisation + fuzzy pairing utilities for the 3D mapping admin UI.
 *
 * Real-world GLB authoring tools (Blender, 3ds Max, Revit) produce mesh names that
 * follow many conventions:
 *   "Lot_A_101"   "Lot.A-101"   "LOT_A101"   "A_101_lot"   "Property-A.101"
 *   "BAT-1_LOT-101"   "Tranche2.Bat3.Lot-101"
 * Database property references are equally varied: "A-101", "A101", "REF-A-101".
 *
 * `normalize()` collapses all of those into a comparable form so the auto-pair
 * matcher can recognise that "Lot_A_101" and "A-101" describe the same lot.
 */

const STRIP_PREFIXES = [
  'lot_', 'lot-', 'lot.', 'lot ', 'lot',
  'apt_', 'apt-', 'apt.', 'apt ', 'apt',
  'unit_', 'unit-', 'unit.', 'unit',
  'property_', 'property-', 'property',
  'ref_', 'ref-', 'ref.', 'ref ', 'ref',
];

const STRIP_SUFFIXES = [
  '_lot', '-lot', '.lot', ' lot',
  '_apt', '-apt', '.apt',
  '_unit', '-unit', '.unit',
  '_mesh', '-mesh',
  '_001', // some exporters append a sequential disambiguator
];

/**
 * Canonical form for fuzzy comparison: lower-cased, prefix/suffix stripped,
 * separators removed entirely so "Lot_A-101", "lot.a 101" and "A101" all
 * collapse to "a101".
 */
export function normalize(name: string): string {
  let n = name.toLowerCase().trim();

  // Drop the leading "lot_"/"apt_"/"unit_"/... family
  for (const p of STRIP_PREFIXES) {
    if (n.startsWith(p)) { n = n.substring(p.length); break; }
  }
  // Drop the trailing "_lot"/"-mesh"/"_001"/... family
  for (const s of STRIP_SUFFIXES) {
    if (n.endsWith(s)) { n = n.substring(0, n.length - s.length); break; }
  }
  // Remove every separator + non-alphanumeric character
  return n.replace(/[^a-z0-9]/g, '');
}

/**
 * The numeric tail of a name — e.g. "Lot_A_101" → "101", "A-12B" → "12".
 * Used as a last-resort matcher when normalised forms don't agree.
 */
export function trailingNumber(name: string): string | null {
  const m = name.match(/(\d+)\D*$/);
  return m ? m[1] : null;
}

/** Strategy used to find a match — surfaced to the UI so the admin sees how confident a pairing is. */
export type PairStrategy =
  | 'exact'      // normalize(mesh) === normalize(ref)
  | 'contains'   // one normalized form contains the other
  | 'suffix'     // mesh name ends with the property ref (or vice-versa)
  | 'trailing'   // matched only by the trailing number (lowest confidence)
  | 'manual';    // human pairing — recorded for the UI but never used by the auto-pair

export interface PairProposal {
  meshName:        string;
  propertyId:      string;
  propertyRef:     string;
  propertyTitle:   string;
  strategy:        PairStrategy;
  /** 0–1, computed from strategy + length of overlap. Higher = more confident. */
  confidence:      number;
}

/**
 * Run the auto-pair matcher across the unpaired meshes.
 *
 * Strategy ladder (high → low confidence):
 *   1. normalised exact match
 *   2. normalised contains (bidirectional)
 *   3. suffix match (mesh ends with ref or vice-versa)
 *   4. trailing-number match (e.g. mesh "Floor3_101" → property "A-101")
 *
 * Properties that are ALREADY paired to another mesh are excluded from the
 * candidate pool to prevent duplicate assignments. If multiple candidates tie
 * within a strategy, the FIRST in `properties` wins (caller controls ordering).
 */
export function proposeAutoPairings(
  unpairedMeshes: string[],
  candidateProperties: Array<{ id: string; referenceCode: string; title: string }>
): PairProposal[] {
  const proposals: PairProposal[] = [];
  const usedPropertyIds = new Set<string>();

  // Pre-compute normalised forms once
  const propertyNorms = candidateProperties.map(p => ({
    p,
    norm:     normalize(p.referenceCode || ''),
    trailing: trailingNumber(p.referenceCode || ''),
  }));

  for (const meshName of unpairedMeshes) {
    const meshNorm     = normalize(meshName);
    const meshTrailing = trailingNumber(meshName);

    let best: { p: typeof propertyNorms[number]['p']; strategy: PairStrategy; confidence: number } | null = null;

    for (const { p, norm, trailing } of propertyNorms) {
      if (usedPropertyIds.has(p.id) || !norm) continue;

      // 1. exact normalised match
      if (meshNorm === norm) {
        best = { p, strategy: 'exact', confidence: 1.0 };
        break;
      }

      // 2. contains (bidirectional) — score by the shorter side's coverage
      if (meshNorm.length >= 3 && norm.length >= 3) {
        if (meshNorm.includes(norm)) {
          const conf = 0.7 + (norm.length / meshNorm.length) * 0.2;
          if (!best || conf > best.confidence) best = { p, strategy: 'contains', confidence: conf };
          continue;
        }
        if (norm.includes(meshNorm)) {
          const conf = 0.7 + (meshNorm.length / norm.length) * 0.2;
          if (!best || conf > best.confidence) best = { p, strategy: 'contains', confidence: conf };
          continue;
        }
      }

      // 3. suffix match
      if (meshNorm.length >= 3 && norm.length >= 3) {
        if (meshNorm.endsWith(norm) || norm.endsWith(meshNorm)) {
          const conf = 0.6;
          if (!best || conf > best.confidence) best = { p, strategy: 'suffix', confidence: conf };
          continue;
        }
      }

      // 4. trailing-number match
      if (meshTrailing && trailing && meshTrailing === trailing) {
        const conf = 0.4;
        if (!best || conf > best.confidence) best = { p, strategy: 'trailing', confidence: conf };
      }
    }

    if (best) {
      usedPropertyIds.add(best.p.id);
      proposals.push({
        meshName,
        propertyId:    best.p.id,
        propertyRef:   best.p.referenceCode,
        propertyTitle: best.p.title,
        strategy:      best.strategy,
        confidence:    best.confidence,
      });
    }
  }

  return proposals;
}

// ── Hierarchy heuristics (immeuble / tranche detection) ─────────────────────

/** Recognise mesh names that look like a building container: "BAT_A", "IMM-1", "Building_03". */
export function looksLikeBuilding(name: string): boolean {
  const n = name.toLowerCase();
  return /^(bat|bld|building|imm|immeuble|tour|tower)[\s\-_.]/.test(n) ||
         /^(bat|bld|imm)\d/.test(n);
}

/** Recognise mesh names that look like a tranche container: "TR_1", "Tranche-A", "Phase2". */
export function looksLikeTranche(name: string): boolean {
  const n = name.toLowerCase();
  return /^(tr|tranche|phase|stage)[\s\-_.]/.test(n) ||
         /^(tr|ph)\d/.test(n);
}
