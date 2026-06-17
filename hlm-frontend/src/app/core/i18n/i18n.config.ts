/**
 * i18n configuration — single source of truth for the supported languages, the default,
 * the fallback, and which languages render right-to-left.
 *
 * Re-introduces a real internationalization layer (EX-014). Phase D had removed ngx-translate
 * and the product went FR-only; this restores a runtime catalog so France (Loi SRU) and Arabic
 * markets become a configuration/translation effort, not a rewrite.
 *
 * To add a language: add an entry here, drop a `public/i18n/<code>.json` catalog, done.
 */
export interface LanguageDef {
  /** BCP-47-ish code used as the catalog filename and `<html lang>` value. */
  readonly code: string;
  /** Short label shown in the switcher (e.g. "FR"). */
  readonly label: string;
  /** Endonym shown as the accessible name (e.g. "Français"). */
  readonly name: string;
  /** Text direction; `rtl` flips `<html dir>` and activates the RTL stylesheet. */
  readonly dir: 'ltr' | 'rtl';
}

export const LANGUAGES: readonly LanguageDef[] = [
  { code: 'fr', label: 'FR',   name: 'Français', dir: 'ltr' },
  { code: 'en', label: 'EN',   name: 'English',  dir: 'ltr' },
  { code: 'ar', label: 'عربي', name: 'العربية',  dir: 'rtl' },
] as const;

/** The product's source language and the fallback for any missing catalog key. */
export const DEFAULT_LANG = 'fr';

/** localStorage key holding the user's chosen language (kept across sessions). */
export const LANG_STORAGE_KEY = 'hlm_lang';

export const SUPPORTED_LANG_CODES: readonly string[] = LANGUAGES.map((l) => l.code);

/** Resolve a possibly-unknown code to a supported one, falling back to the default. */
export function normalizeLang(code: string | null | undefined): string {
  if (!code) return DEFAULT_LANG;
  const base = code.toLowerCase().split('-')[0];
  return SUPPORTED_LANG_CODES.includes(base) ? base : DEFAULT_LANG;
}

export function languageDef(code: string): LanguageDef {
  return LANGUAGES.find((l) => l.code === normalizeLang(code)) ?? LANGUAGES[0];
}
