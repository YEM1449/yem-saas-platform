import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import {
  DEFAULT_LANG, LANGUAGES, LANG_STORAGE_KEY, LanguageDef, languageDef, normalizeLang,
} from './i18n.config';

/**
 * Application-facing i18n facade over {@link TranslateService}.
 *
 * Owns everything other code should not re-implement: the supported language list, the active
 * language (as a signal for templates), persistence (localStorage + best-effort backend sync of
 * the user's `langueInterface`), and the document side effects (`<html lang>` / `<html dir>`),
 * including RTL for Arabic.
 *
 * Call {@link bootstrap} once at app start (from an APP_INITIALIZER) so the first paint is already
 * in the right language and direction.
 */
@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly translate = inject(TranslateService);
  private readonly http = inject(HttpClient);

  readonly languages: readonly LanguageDef[] = LANGUAGES;

  /** Reactive active language code; bind in templates via `i18n.activeLang()`. */
  readonly activeLang = signal<string>(DEFAULT_LANG);

  /**
   * Initialise the translation engine and apply the persisted (or default) language.
   * Returns a promise so the APP_INITIALIZER waits for the first catalog to load — this avoids a
   * flash of translation keys on first paint.
   */
  bootstrap(): Promise<unknown> {
    this.translate.addLangs(LANGUAGES.map((l) => l.code));
    this.translate.setFallbackLang(DEFAULT_LANG);
    const initial = normalizeLang(localStorage.getItem(LANG_STORAGE_KEY));
    this.applyDocument(initial);
    this.activeLang.set(initial);
    // `use()` returns an observable that completes once the catalog is loaded.
    return new Promise((resolve) => this.translate.use(initial).subscribe({
      next: () => resolve(true),
      error: () => resolve(false),
    }));
  }

  /** Switch the active language: catalog, persistence, document direction, and backend sync. */
  setLanguage(code: string): void {
    const lang = normalizeLang(code);
    if (lang === this.activeLang()) return;
    this.translate.use(lang);
    this.activeLang.set(lang);
    localStorage.setItem(LANG_STORAGE_KEY, lang);
    this.applyDocument(lang);
    // Best-effort: remember the choice on the user profile so it follows them across devices.
    // Silent on failure (e.g. portal/anonymous contexts) — the local choice still applies.
    this.http.patch('/api/me', { langueInterface: lang }).subscribe({ error: () => {} });
  }

  /**
   * Adopt a server-provided preference (e.g. the user's `langueInterface` returned at login) when
   * the visitor has not made an explicit local choice yet. Keeps device choice authoritative.
   */
  adoptUserPreference(code: string | null | undefined): void {
    if (!code) return;
    if (localStorage.getItem(LANG_STORAGE_KEY)) return; // explicit local choice wins
    this.setLanguage(code);
  }

  /** Synchronous translation for use in TypeScript (toasts, dynamic labels). */
  instant(key: string, params?: Record<string, unknown>): string {
    return this.translate.instant(key, params);
  }

  isRtl(code: string = this.activeLang()): boolean {
    return languageDef(code).dir === 'rtl';
  }

  private applyDocument(code: string): void {
    const def = languageDef(code);
    const html = document.documentElement;
    html.lang = def.code;
    html.dir = def.dir;
  }
}
