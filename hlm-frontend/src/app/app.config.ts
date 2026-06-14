import {
  APP_INITIALIZER, ApplicationConfig, DEFAULT_CURRENCY_CODE, LOCALE_ID, provideZoneChangeDetection,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { portalInterceptor } from './portal/core/portal.interceptor';
import { registerLocaleData } from '@angular/common';
// Morocco French locale: drives date/number/percent formatting and makes MAD the default currency
// (EX-008). `fr` is also registered so any explicit `:'fr'` pipe usage keeps working.
import localeFr from '@angular/common/locales/fr';
import localeFrMA from '@angular/common/locales/fr-MA';
registerLocaleData(localeFr);
registerLocaleData(localeFrMA);

export function initializeApp() {
  return () => {
    // FR-only product: force LTR/French and clear any stale language preference left by the
    // (removed) multi-language switcher, which could otherwise pin the layout to RTL (EX-007).
    localStorage.removeItem('hlm_lang');
    document.documentElement.dir = 'ltr';
    document.documentElement.lang = 'fr';
  };
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    { provide: LOCALE_ID, useValue: 'fr-MA' },
    { provide: DEFAULT_CURRENCY_CODE, useValue: 'MAD' },
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor, portalInterceptor])),
    {
      provide: APP_INITIALIZER,
      useFactory: initializeApp,
      deps: [],
      multi: true,
    },
  ],
};
