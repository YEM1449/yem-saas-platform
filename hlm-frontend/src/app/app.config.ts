import {
  APP_INITIALIZER, ApplicationConfig, DEFAULT_CURRENCY_CODE, LOCALE_ID, provideZoneChangeDetection,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideTranslateService } from '@ngx-translate/core';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { portalInterceptor } from './portal/core/portal.interceptor';
import { registerLocaleData } from '@angular/common';
import { I18nService } from './core/i18n/i18n.service';
import { DEFAULT_LANG } from './core/i18n/i18n.config';
// Morocco French locale: drives date/number/percent formatting and makes MAD the default currency
// (EX-008). `fr` is also registered so any explicit `:'fr'` pipe usage keeps working.
import localeFr from '@angular/common/locales/fr';
import localeFrMA from '@angular/common/locales/fr-MA';
registerLocaleData(localeFr);
registerLocaleData(localeFrMA);

/**
 * Loads the active language catalog before the app renders, so the first paint is already
 * translated and in the correct text direction (EX-014). The chosen language comes from
 * localStorage (set by the language switcher) and defaults to French.
 */
export function initializeI18n(i18n: I18nService) {
  return () => i18n.bootstrap();
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    { provide: LOCALE_ID, useValue: 'fr-MA' },
    { provide: DEFAULT_CURRENCY_CODE, useValue: 'MAD' },
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor, portalInterceptor])),
    // Runtime i18n: JSON catalogs served from /i18n/<lang>.json, French as the source/fallback.
    provideTranslateService({
      loader: provideTranslateHttpLoader({ prefix: '/i18n/', suffix: '.json' }),
      fallbackLang: DEFAULT_LANG,
    }),
    {
      provide: APP_INITIALIZER,
      useFactory: initializeI18n,
      deps: [I18nService],
      multi: true,
    },
  ],
};
