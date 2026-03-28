import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-language-switcher',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="lang-bar">
      <svg class="lang-icon" width="14" height="14" viewBox="0 0 24 24" fill="none"
           stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="10"/>
        <path d="M2 12h20M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/>
      </svg>
      @for (lang of languages; track lang.code) {
        <button [class.active]="activeLang === lang.code"
                (click)="switchLanguage(lang.code)"
                [title]="lang.name">
          {{ lang.label }}
        </button>
      }
    </div>
  `,
  styles: [`
    .lang-bar {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 4px 0;
    }
    .lang-icon {
      opacity: 0.5;
      flex-shrink: 0;
      color: rgba(255,255,255,0.75);
    }
    .lang-bar button {
      background: transparent;
      border: 1.5px solid rgba(255,255,255,0.35);
      color: rgba(255,255,255,0.75);
      padding: 5px 14px;
      border-radius: 9999px;
      cursor: pointer;
      font-size: 13px;
      font-weight: 500;
      letter-spacing: 0.02em;
      transition: all 0.2s ease;
    }
    .lang-bar button:hover {
      background: rgba(255,255,255,0.1);
      border-color: rgba(255,255,255,0.5);
      color: #fff;
    }
    .lang-bar button.active {
      background: #fff;
      color: #0f172a;
      border-color: #fff;
      font-weight: 600;
      box-shadow: 0 0 0 3px rgba(255,255,255,0.2);
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
    this.activeLang = lang;
    // Brief fade-out/in for visual feedback
    document.body.style.transition = 'opacity 0.15s ease';
    document.body.style.opacity = '0.7';

    this.translate.use(lang);
    localStorage.setItem('hlm_lang', lang);
    document.documentElement.dir = lang === 'ar' ? 'rtl' : 'ltr';
    document.documentElement.lang = lang;

    setTimeout(() => {
      document.body.style.opacity = '1';
    }, 200);

    // Persist to backend (fire-and-forget)
    this.http.put('/auth/me/langue', { langue: lang }).subscribe();
  }
}
