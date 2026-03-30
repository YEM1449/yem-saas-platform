import { APP_INITIALIZER, ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { portalInterceptor } from './portal/core/portal.interceptor';

export function initializeApp(translate: TranslateService) {
  return () => {
    translate.setDefaultLang('fr');
    const saved = localStorage.getItem('hlm_lang') || 'fr';
    translate.use(saved);
    document.documentElement.dir = saved === 'ar' ? 'rtl' : 'ltr';
    document.documentElement.lang = saved;
  };
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor, portalInterceptor])),
    provideTranslateService({ fallbackLang: 'fr' }),
    ...provideTranslateHttpLoader({ prefix: '/assets/i18n/', suffix: '.json' }),
    {
      provide: APP_INITIALIZER,
      useFactory: initializeApp,
      deps: [TranslateService],
      multi: true,
    },
  ],
};
