import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-language-switcher',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  template: `
    <div class="lang-switcher">
      @for (lang of languages; track lang.code) {
        <button
          [class.active]="currentLang === lang.code"
          (click)="switchLanguage(lang.code)"
          [title]="lang.name">
          {{ lang.label }}
        </button>
      }
    </div>
  `,
  styles: [`
    .lang-switcher { display: flex; gap: 4px; }
    .lang-switcher button {
      background: transparent;
      border: 1px solid rgba(255,255,255,0.3);
      color: inherit;
      padding: 2px 8px;
      border-radius: 4px;
      cursor: pointer;
      font-size: 12px;
    }
    .lang-switcher button.active {
      background: rgba(255,255,255,0.2);
      border-color: rgba(255,255,255,0.6);
      font-weight: 600;
    }
  `],
})
export class LanguageSwitcherComponent {
  private translate = inject(TranslateService);
  private http = inject(HttpClient);

  languages = [
    { code: 'fr', label: 'FR', name: 'Français' },
    { code: 'en', label: 'EN', name: 'English' },
    { code: 'ar', label: 'ع', name: 'العربية' },
  ];

  get currentLang(): string {
    return this.translate.currentLang || 'fr';
  }

  switchLanguage(lang: string): void {
    this.translate.use(lang);
    localStorage.setItem('hlm_lang', lang);
    document.documentElement.dir = lang === 'ar' ? 'rtl' : 'ltr';
    document.documentElement.lang = lang;

    // Persist to backend (fire-and-forget)
    this.http.put('/auth/me/langue', { langue: lang }).subscribe();
  }
}
