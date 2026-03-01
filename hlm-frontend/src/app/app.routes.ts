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
      { path: 'properties/:id', loadComponent: () => import('./features/properties/property-detail.component').then(m => m.PropertyDetailComponent) },
      { path: 'contacts', loadComponent: () => import('./features/contacts/contacts.component').then(m => m.ContactsComponent) },
      { path: 'contacts/:id', loadComponent: () => import('./features/contacts/contact-detail.component').then(m => m.ContactDetailComponent) },
      { path: 'prospects', loadComponent: () => import('./features/prospects/prospects.component').then(m => m.ProspectsComponent) },
      { path: 'prospects/:id', loadComponent: () => import('./features/prospects/prospect-detail.component').then(m => m.ProspectDetailComponent) },
      { path: 'notifications', loadComponent: () => import('./features/notifications/notifications.component').then(m => m.NotificationsComponent) },
      { path: 'messages', loadComponent: () => import('./features/outbox/outbox.component').then(m => m.OutboxComponent) },
      { path: 'projects', loadComponent: () => import('./features/projects/projects.component').then(m => m.ProjectsComponent) },
      { path: 'projects/:id', loadComponent: () => import('./features/projects/project-detail.component').then(m => m.ProjectDetailComponent) },
      { path: 'admin/users', canActivate: [adminGuard], loadComponent: () => import('./features/admin-users/admin-users.component').then(m => m.AdminUsersComponent) },
      { path: 'contracts', loadComponent: () => import('./features/contracts/contracts.component').then(m => m.ContractsComponent) },
      { path: 'contracts/:id', loadComponent: () => import('./features/contracts/contract-detail.component').then(m => m.ContractDetailComponent) },
      { path: 'contracts/:contractId/payments', loadComponent: () => import('./features/payments/payment-schedule.component').then(m => m.PaymentScheduleComponent) },
      { path: 'dashboard/commercial', loadComponent: () => import('./features/dashboard/commercial-dashboard.component').then(m => m.CommercialDashboardComponent) },
      { path: 'dashboard/commercial/sales', loadComponent: () => import('./features/dashboard/commercial-dashboard-sales.component').then(m => m.CommercialDashboardSalesComponent) },
      { path: 'dashboard/commercial/cash', loadComponent: () => import('./features/dashboard/cash-dashboard.component').then(m => m.CashDashboardComponent) },
      { path: '', redirectTo: 'properties', pathMatch: 'full' },
    ],
  },
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: '**', redirectTo: 'login' },
];
