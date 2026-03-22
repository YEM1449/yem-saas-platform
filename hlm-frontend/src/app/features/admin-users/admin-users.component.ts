import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { AdminUserService } from './admin-user.service';
import { AdminUser } from './admin-user.model';
import { ErrorResponse } from '../../core/models/error-response.model';
import { AuthService } from '../../core/auth/auth.service';

const ALL_ROLES = ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_AGENT'] as const;
const NON_ADMIN_ROLES = ['ROLE_MANAGER', 'ROLE_AGENT'] as const;

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-users.component.html',
  styleUrl: './admin-users.component.css',
})
export class AdminUsersComponent implements OnInit {
  private svc = inject(AdminUserService);
  private auth = inject(AuthService);

  users: AdminUser[] = [];
  loading = true;
  error = '';
  success = '';

  // Search
  searchQuery = '';

  // Create form
  showCreate = false;
  createEmail = '';
  createPassword = '';
  createRole = 'ROLE_AGENT';
  creating = false;

  // Temp password display
  tempPassword = '';
  tempPasswordUser = '';

  /**
   * RBAC: only SUPER_ADMIN can assign the ADMIN role.
   * Company-level ADMIN sees MANAGER and AGENT only.
   */
  get roles(): readonly string[] {
    return this.auth.user?.role === 'ROLE_SUPER_ADMIN' ? ALL_ROLES : NON_ADMIN_ROLES;
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    const q = this.searchQuery.trim() || undefined;
    this.svc.list(q).subscribe({
      next: (list) => {
        this.users = list;
        this.loading = false;
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        this.error = this.extractError(err);
      },
    });
  }

  search(): void {
    this.load();
  }

  toggleCreate(): void {
    this.showCreate = !this.showCreate;
    this.clearCreateForm();
  }

  submitCreate(): void {
    this.creating = true;
    this.error = '';
    this.success = '';
    this.svc.create({ email: this.createEmail, password: this.createPassword, role: this.createRole }).subscribe({
      next: (user) => {
        this.creating = false;
        this.showCreate = false;
        this.success = `User ${user.email} created.`;
        this.clearCreateForm();
        this.load();
      },
      error: (err: HttpErrorResponse) => {
        this.creating = false;
        this.error = this.extractError(err);
      },
    });
  }

  changeRole(user: AdminUser, newRole: string): void {
    if (newRole === user.role) return;
    this.error = '';
    this.success = '';
    this.svc.changeRole(user.id, newRole).subscribe({
      next: (updated) => {
        const idx = this.users.findIndex((u) => u.id === updated.id);
        if (idx >= 0) this.users[idx] = updated;
        this.success = `Role updated for ${updated.email}.`;
      },
      error: (err: HttpErrorResponse) => {
        this.error = this.extractError(err);
      },
    });
  }

  toggleEnabled(user: AdminUser): void {
    this.error = '';
    this.success = '';
    this.svc.setEnabled(user.id, !user.enabled).subscribe({
      next: (updated) => {
        const idx = this.users.findIndex((u) => u.id === updated.id);
        if (idx >= 0) this.users[idx] = updated;
        this.success = `${updated.email} ${updated.enabled ? 'enabled' : 'disabled'}.`;
      },
      error: (err: HttpErrorResponse) => {
        this.error = this.extractError(err);
      },
    });
  }

  resetPassword(user: AdminUser): void {
    this.error = '';
    this.success = '';
    this.tempPassword = '';
    this.svc.resetPassword(user.id).subscribe({
      next: (res) => {
        this.tempPassword = res.temporaryPassword;
        this.tempPasswordUser = user.email;
        this.success = `Temporary password generated for ${user.email}.`;
      },
      error: (err: HttpErrorResponse) => {
        this.error = this.extractError(err);
      },
    });
  }

  dismissTempPassword(): void {
    this.tempPassword = '';
    this.tempPasswordUser = '';
  }

  formatRole(role: string): string {
    return role.replace('ROLE_', '');
  }

  private clearCreateForm(): void {
    this.createEmail = '';
    this.createPassword = '';
    this.createRole = 'ROLE_AGENT';
  }

  private extractError(err: HttpErrorResponse): string {
    if (err.status === 401) return 'Session expired. Please log in again.';
    const body = err.error as ErrorResponse | null;
    if (err.status === 403) {
      if (body?.code === 'ROLE_ESCALATION_FORBIDDEN') {
        return 'Action non autorisée : seul un Super Administrateur peut attribuer le rôle Administrateur.';
      }
      return 'Accès refusé. Rôle insuffisant pour cette action.';
    }
    if (body?.message) return body.message;
    return `Request failed (${err.status})`;
  }
}
