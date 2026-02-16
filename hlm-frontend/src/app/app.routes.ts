import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { adminGuard } from './core/auth/admin.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./features/login/login.component').then(m => m.LoginComponent) },
  {
    path: 'app',
    loadComponent: () => import('./features/shell/shell.component').then(m => m.ShellComponent),
    canActivate: [authGuard],
    children: [
      { path: 'properties', loadComponent: () => import('./features/properties/properties.component').then(m => m.PropertiesComponent) },
      { path: 'contacts', loadComponent: () => import('./features/contacts/contacts.component').then(m => m.ContactsComponent) },
      { path: 'contacts/:id', loadComponent: () => import('./features/contacts/contact-detail.component').then(m => m.ContactDetailComponent) },
      { path: 'prospects', loadComponent: () => import('./features/prospects/prospects.component').then(m => m.ProspectsComponent) },
      { path: 'prospects/:id', loadComponent: () => import('./features/prospects/prospect-detail.component').then(m => m.ProspectDetailComponent) },
      { path: 'notifications', loadComponent: () => import('./features/notifications/notifications.component').then(m => m.NotificationsComponent) },
      { path: 'admin/users', canActivate: [adminGuard], loadComponent: () => import('./features/admin-users/admin-users.component').then(m => m.AdminUsersComponent) },
      { path: '', redirectTo: 'properties', pathMatch: 'full' },
    ],
  },
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: '**', redirectTo: 'login' },
];
