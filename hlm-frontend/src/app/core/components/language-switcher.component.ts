import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-language-switcher',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="lang-bar" role="group" aria-label="Sélection de la langue">
      <svg class="lang-icon" width="14" height="14" viewBox="0 0 24 24" fill="none"
           stroke="currentColor" stroke-width="2" aria-hidden="true">
        <circle cx="12" cy="12" r="10"/>
        <path d="M2 12h20M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/>
      </svg>
      @for (lang of languages; track lang.code) {
        <button type="button"
                [class.active]="activeLang === lang.code"
                [attr.aria-pressed]="activeLang === lang.code"
                [attr.aria-label]="lang.name"
                (click)="switchLanguage(lang.code)"
                [title]="lang.name">
          {{ lang.label }}
        </button>
      }
    </div>
  `,
  styles: [`
    /* Language switcher — works on cream/paper surfaces (CRM shell, login).
       Active language gets a filled brand-green pill; inactive options stay
       clearly visible so the user can switch. */
    .lang-bar {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 3px;
      background: rgba(0, 0, 0, 0.03);
      border: 1px solid var(--line, #e8dec5);
      border-radius: 9999px;
    }
    .lang-icon {
      flex-shrink: 0;
      margin: 0 4px 0 6px;
      color: var(--ink-3, #9a8e77);
      opacity: 0.7;
    }
    .lang-bar button {
      background: transparent;
      border: none;
      color: var(--ink-2, #5a4e3d);
      padding: 5px 12px;
      min-width: 38px;
      border-radius: 9999px;
      cursor: pointer;
      font-family: inherit;
      font-size: 12px;
      font-weight: 600;
      letter-spacing: 0.03em;
      line-height: 1;
      transition: background 140ms ease, color 140ms ease;
    }
    .lang-bar button:hover:not(.active) {
      background: rgba(0, 0, 0, 0.06);
      color: var(--ink, #2d241a);
    }
    .lang-bar button:focus-visible {
      outline: 2px solid var(--c-primary, #16a34a);
      outline-offset: 2px;
    }
    .lang-bar button.active {
      background: var(--c-primary, #16a34a);
      color: #fff;
      box-shadow: 0 1px 2px rgba(22, 163, 74, 0.25);
    }

    /* Compact variant — used inside the collapsed 64px rail */
    :host-context(.sidebar:not(.rail-expanded)) .lang-bar {
      flex-direction: column;
      gap: 2px;
      padding: 4px 2px;
      border-radius: 12px;
    }
    :host-context(.sidebar:not(.rail-expanded)) .lang-icon { display: none; }
    :host-context(.sidebar:not(.rail-expanded)) .lang-bar button {
      min-width: 32px;
      padding: 4px 6px;
      font-size: 11px;
    }
  `],
})
export class LanguageSwitcherComponent {
  private translate = inject(TranslateService);
  private http = inject(HttpClient);

  languages = [
    { code: 'fr', label: 'FR', name: 'Français' },
    { code: 'en', label: 'EN', name: 'English' },
    { code: 'ar', label: 'عربي', name: 'العربية' },
  ];

  activeLang: string = localStorage.getItem('hlm_lang') || 'fr';

  switchLanguage(lang: string): void {
    if (this.activeLang === lang) return;
    this.activeLang = lang;
    document.body.style.transition = 'opacity 0.15s ease';
    document.body.style.opacity = '0.7';

    this.translate.use(lang);
    localStorage.setItem('hlm_lang', lang);
    document.documentElement.dir = lang === 'ar' ? 'rtl' : 'ltr';
    document.documentElement.lang = lang;

    setTimeout(() => {
      document.body.style.opacity = '1';
    }, 200);

    this.http.put('/auth/me/langue', { langue: lang }).subscribe();
  }
}
