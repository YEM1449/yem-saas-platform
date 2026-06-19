#!/usr/bin/env node
/**
 * i18n hardcoded-string gate (EX-014).
 *
 * Fails CI when a user-facing French string is added to an Angular template without
 * going through the translation layer. Heuristic: a French *accented* character
 * (à â ä é è ê ë î ï ô ö û ù ç + uppercase) appearing in a visible text node or a
 * plain (non-bound) text attribute — after stripping interpolations, control-flow,
 * comments and bound attributes. Accents are a high-signal marker of untranslated
 * French; this intentionally does not try to catch unaccented English (too noisy).
 *
 * Scope: every `*.html` template + inline `template: \`...\`` blocks under src/app.
 * Escape hatches:
 *   - add a path fragment to IGNORE for intentionally-FR-only legal content;
 *   - put `<!-- i18n-ignore -->` on the line for a one-off exception.
 *
 * Run: `npm run i18n:scan`
 */
import { readFileSync } from 'node:fs';
import { globSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join, relative } from 'node:path';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const SRC = join(ROOT, 'src', 'app');

// Intentionally FR-only — legal/document content, never machine-translated (see docs/i18n).
const IGNORE = [
  'portal/features/portal-legal',
  'portal/features/portal-privacy',
  // template-editor clause HTML bodies are Moroccan VEFA legal text held in TS data arrays,
  // not in a `template:` block — they are not scanned, but ignore defensively.
  'features/templates/template-editor.component.ts',
  // Orphan: the portal-payments component is a small inline-template stub redirecting to the
  // ventes tab (the real échéancier lives in portal-ventes). This .html is dead — it even
  // calls itemStatusLabel() which the stub doesn't define. Left in place, not scanned.
  'portal/features/portal-payments/portal-payments.component.html',
];

const ACCENT = /[àâäéèêëîïôöûùç]/i;

/** Blank a match but keep its newlines, so line numbers stay accurate. */
const blank = (m) => m.replace(/[^\n]/g, ' ');

/** Remove what is NOT a literal user-facing string from a template. */
function sanitize(tpl) {
  return tpl
    .replace(/<!--[\s\S]*?-->/g, blank)        // html comments
    .replace(/\{\{[\s\S]*?\}\}/g, blank)        // interpolations (keys/expressions)
    .replace(/<(script|style|svg)[\s\S]*?<\/\1>/gi, blank) // non-text elements
    .replace(/\[[^\]]*\]="[^"]*"/g, blank)      // property/attr bindings [x]="..."
    .replace(/\([^)]*\)="[^"]*"/g, blank)       // event bindings (x)="..."
    .replace(/\*ng[a-zA-Z]+="[^"]*"/g, blank)   // structural directives
    .replace(/#\w+/g, ' ');                     // template refs
}

const violations = [];

function scanTemplate(relPath, tpl, lineOffset) {
  const sane = sanitize(tpl);
  const lines = sane.split('\n');
  const rawLines = tpl.split('\n');
  lines.forEach((line, i) => {
    if ((rawLines[i] ?? '').includes('i18n-ignore')) return;
    // text nodes: >…<  and plain text attributes title/placeholder/aria-label/alt="…"
    const candidates = [];
    for (const m of line.matchAll(/>([^<>{}]*?)</g)) candidates.push(m[1]);
    for (const m of line.matchAll(/\b(?:title|placeholder|aria-label|alt)="([^"]*)"/g)) candidates.push(m[1]);
    for (const text of candidates) {
      if (ACCENT.test(text) && /\p{L}{2,}/u.test(text)) {
        violations.push({ file: relPath, line: lineOffset + i + 1, text: text.trim().slice(0, 60) });
      }
    }
  });
}

const htmlFiles = globSync('**/*.html', { cwd: SRC });
for (const f of htmlFiles) {
  if (IGNORE.some((ig) => f.includes(ig))) continue;
  scanTemplate(join('src/app', f), readFileSync(join(SRC, f), 'utf8'), 0);
}

const tsFiles = globSync('**/*.ts', { cwd: SRC });
for (const f of tsFiles) {
  if (IGNORE.some((ig) => f.includes(ig))) continue;
  const src = readFileSync(join(SRC, f), 'utf8');
  // inline template: `...`
  const m = src.match(/template:\s*`([\s\S]*?)`\s*,?\s*\n/);
  if (!m) continue;
  const lineOffset = src.slice(0, m.index).split('\n').length - 1;
  scanTemplate(join('src/app', f), m[1], lineOffset);
}

if (violations.length) {
  console.error(`\n✘ i18n gate: ${violations.length} hardcoded French string(s) in templates:\n`);
  for (const v of violations) {
    console.error(`  ${v.file}:${v.line}  —  "${v.text}"`);
  }
  console.error(
    '\nWrap them with the translate pipe (see docs/i18n/i18n-migration-guide.md §3),' +
    '\nor add `<!-- i18n-ignore -->` on the line / a path to IGNORE in tools/i18n-scan.mjs' +
    '\nfor intentionally non-translatable content.\n'
  );
  process.exit(1);
}
console.log(`✓ i18n gate: ${htmlFiles.length} templates scanned, no hardcoded French found.`);
