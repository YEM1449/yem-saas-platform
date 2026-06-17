import { Component, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { I18nService } from '../i18n/i18n.service';

/**
 * Language switcher (FR / EN / عربي) — now functional: it drives the real i18n layer
 * ({@link I18nService} → ngx-translate), persists the choice, syncs it to the user profile,
 * and flips text direction (RTL for Arabic). Re-introduced as part of EX-014 after EX-007 removed
 * the earlier cosmetic version that changed nothing but `dir`.
 */
@Component({
  selector: 'app-language-switcher',
  standalone: true,
  imports: [TranslatePipe],
  template: `
    <div class="lang-bar" role="group" [attr.aria-label]="'lang.groupAria' | translate">
      <svg class="lang-icon" width="14" height="14" viewBox="0 0 24 24" fill="none"
           stroke="currentColor" stroke-width="2" aria-hidden="true">
        <circle cx="12" cy="12" r="10"/>
        <path d="M2 12h20M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/>
      </svg>
      @for (lang of i18n.languages; track lang.code) {
        <button type="button"
                [class.active]="i18n.activeLang() === lang.code"
                [attr.aria-pressed]="i18n.activeLang() === lang.code"
                [attr.aria-label]="('lang.switchTo' | translate:{ name: lang.name })"
                (click)="i18n.setLanguage(lang.code)"
                [title]="lang.name">
          {{ lang.label }}
        </button>
      }
    </div>
  `,
  styles: [`
    .lang-bar {
      display: inline-flex; align-items: center; gap: 4px; padding: 3px;
      background: rgba(0, 0, 0, 0.03); border: 1px solid var(--line, #e8dec5);
      border-radius: 9999px;
    }
    .lang-icon { flex-shrink: 0; margin: 0 4px 0 6px; color: var(--ink-3, #9a8e77); opacity: 0.7; }
    .lang-bar button {
      background: transparent; border: none; color: var(--ink-2, #5a4e3d);
      padding: 5px 12px; min-width: 38px; border-radius: 9999px; cursor: pointer;
      font-family: inherit; font-size: 12px; font-weight: 600; letter-spacing: 0.03em;
      line-height: 1; transition: background 140ms ease, color 140ms ease;
    }
    .lang-bar button:hover:not(.active) { background: rgba(0, 0, 0, 0.06); color: var(--ink, #2d241a); }
    .lang-bar button:focus-visible { outline: 2px solid var(--c-primary, #16a34a); outline-offset: 2px; }
    .lang-bar button.active {
      background: var(--c-primary, #16a34a); color: #fff; box-shadow: 0 1px 2px rgba(22, 163, 74, 0.25);
    }
    :host-context(.sidebar:not(.rail-expanded)) .lang-bar {
      flex-direction: column; gap: 2px; padding: 4px 2px; border-radius: 12px;
    }
    :host-context(.sidebar:not(.rail-expanded)) .lang-icon { display: none; }
    :host-context(.sidebar:not(.rail-expanded)) .lang-bar button {
      min-width: 32px; padding: 4px 6px; font-size: 11px;
    }
  `],
})
export class LanguageSwitcherComponent {
  readonly i18n = inject(I18nService);
}
