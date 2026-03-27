import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { adminGuard } from './core/auth/admin.guard';
import { superadminGuard } from './core/auth/superadmin.guard';
import { portalGuard } from './portal/core/portal-auth.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./features/login/login.component').then(m => m.LoginComponent) },
  { path: 'activation', loadComponent: () => import('./features/activation/activation.component').then(m => m.ActivationComponent) },
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
      { path: 'contracts/:contractId/payments', loadComponent: () => import('./features/contracts/payment-schedule.component').then(m => m.PaymentScheduleComponent) },
      { path: 'reservations', loadComponent: () => import('./features/reservations/reservations.component').then(m => m.ReservationsComponent) },
      { path: 'dashboard/commercial', loadComponent: () => import('./features/dashboard/commercial-dashboard.component').then(m => m.CommercialDashboardComponent) },
      { path: 'dashboard/commercial/sales', loadComponent: () => import('./features/dashboard/commercial-dashboard-sales.component').then(m => m.CommercialDashboardSalesComponent) },
      { path: 'dashboard/commercial/cash', loadComponent: () => import('./features/dashboard/cash-dashboard.component').then(m => m.CashDashboardComponent) },
      { path: 'dashboard/receivables', loadComponent: () => import('./features/dashboard/receivables-dashboard.component').then(m => m.ReceivablesDashboardComponent) },
      { path: 'commissions', loadComponent: () => import('./features/commissions/commissions.component').then(m => m.CommissionsComponent) },
      { path: 'audit', loadComponent: () => import('./features/audit/audit.component').then(m => m.AuditComponent) },
      { path: 'tasks', loadComponent: () => import('./features/tasks/tasks.component').then(m => m.TasksComponent) },
      { path: 'templates', canActivate: [adminGuard], loadComponent: () => import('./features/templates/template-list.component').then(m => m.TemplateListComponent) },
      { path: 'templates/:type/edit', canActivate: [adminGuard], loadComponent: () => import('./features/templates/template-editor.component').then(m => m.TemplateEditorComponent) },
      { path: '', redirectTo: 'properties', pathMatch: 'full' },
    ],
  },
  {
    path: 'superadmin',
    loadComponent: () => import('./features/superadmin/superadmin-shell.component').then(m => m.SuperadminShellComponent),
    canActivate: [superadminGuard],
    children: [
      { path: 'societes', loadComponent: () => import('./features/superadmin/societes/societe-list.component').then(m => m.SocieteListComponent) },
      { path: 'societes/new', loadComponent: () => import('./features/superadmin/societes/societe-form.component').then(m => m.SocieteFormComponent) },
      { path: 'societes/:id', loadComponent: () => import('./features/superadmin/societes/societe-detail.component').then(m => m.SocieteDetailComponent) },
      { path: 'societes/:id/edit', loadComponent: () => import('./features/superadmin/societes/societe-form.component').then(m => m.SocieteFormComponent) },
      { path: '', redirectTo: 'societes', pathMatch: 'full' },
    ],
  },
  {
    path: 'portal',
    children: [
      { path: 'login', loadComponent: () => import('./portal/features/portal-login/portal-login.component').then(m => m.PortalLoginComponent) },
      {
        path: '',
        loadComponent: () => import('./portal/features/portal-shell/portal-shell.component').then(m => m.PortalShellComponent),
        canActivate: [portalGuard],
        children: [
          { path: 'contracts', loadComponent: () => import('./portal/features/portal-contracts/portal-contracts.component').then(m => m.PortalContractsComponent) },
          { path: 'contracts/:contractId/payments', loadComponent: () => import('./portal/features/portal-payments/portal-payments.component').then(m => m.PortalPaymentsComponent) },
          { path: 'properties/:id', loadComponent: () => import('./portal/features/portal-property/portal-property.component').then(m => m.PortalPropertyComponent) },
          { path: '', redirectTo: 'contracts', pathMatch: 'full' },
        ],
      },
    ],
  },
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: '**', redirectTo: 'login' },
];
