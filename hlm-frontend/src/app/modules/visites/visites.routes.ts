import { Routes } from '@angular/router';

/**
 * Visites module routes (Wave 16). Lazy-loaded under /app/visites; the shell already guards
 * the area with authGuard. Agenda is the entry point; detail and forms are siblings.
 */
export const VISITES_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./components/agenda/agenda.component').then(m => m.AgendaComponent),
  },
  {
    path: 'nouvelle',
    loadComponent: () =>
      import('./components/visite-form/visite-form.component').then(m => m.VisiteFormComponent),
  },
  {
    path: ':id',
    loadComponent: () =>
      import('./components/visite-detail/visite-detail.component').then(m => m.VisiteDetailComponent),
  },
  {
    path: ':id/modifier',
    loadComponent: () =>
      import('./components/visite-form/visite-form.component').then(m => m.VisiteFormComponent),
  },
  {
    path: ':id/compte-rendu',
    loadComponent: () =>
      import('./components/visite-compte-rendu/visite-compte-rendu.component')
        .then(m => m.VisiteCompteRenduComponent),
  },
];
