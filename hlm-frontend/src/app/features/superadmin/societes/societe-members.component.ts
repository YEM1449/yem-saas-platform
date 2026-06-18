import { Component, inject, Input, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { I18nService } from '../../../core/i18n/i18n.service';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { SocieteService } from './societe.service';
import { InviteUserRequest, MembreSocieteDto } from './societe.model';

@Component({
  selector: 'app-societe-members',
  standalone: true,
  imports: [FormsModule, DatePipe, TranslatePipe],
  templateUrl: './societe-members.component.html',
  styleUrl: './societe-members.component.css',
})
export class SocieteMembersComponent implements OnInit {
  private i18n = inject(I18nService);
  @Input() societeId = '';

  private svc = inject(SocieteService);
  private router = inject(Router);

  membres: MembreSocieteDto[] = [];
  loading = false;
  error = '';
  success = '';

  // Invite user form
  inviteForm: InviteUserRequest = { email: '', prenom: '', nomFamille: '', role: 'ADMIN' };
  inviting = false;
  showInviteForm = false;

  // Change role inline state
  roleEditing: Record<string, string> = {};

  // Impersonation
  impersonating: Record<string, boolean> = {};

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.svc.listMembres(this.societeId).subscribe({
      next: (list) => {
        this.membres = list;
        this.loading = false;
        // Initialize role editing state
        this.roleEditing = {};
        for (const m of list) {
          this.roleEditing[m.userId] = m.role;
        }
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        this.error = this.extractError(err);
      },
    });
  }

  submitInvite(): void {
    if (!this.inviteForm.email.trim() || !this.inviteForm.prenom.trim() || !this.inviteForm.nomFamille.trim()) {
      this.error = this.i18n.instant('superadmin.members.requiredFields');
      return;
    }
    this.inviting = true;
    this.error = '';
    this.success = '';
    this.svc.inviteUser(this.societeId, this.inviteForm).subscribe({
      next: () => {
        this.inviting = false;
        this.success = this.i18n.instant('superadmin.members.invitationSent', { email: this.inviteForm.email });
        this.inviteForm = { email: '', prenom: '', nomFamille: '', role: 'ADMIN' };
        this.showInviteForm = false;
        this.load();
      },
      error: (err: HttpErrorResponse) => {
        this.inviting = false;
        this.error = this.extractError(err);
      },
    });
  }

  changeRole(m: MembreSocieteDto): void {
    const newRole = this.roleEditing[m.userId];
    if (!newRole || newRole === m.role) return;
    this.error = '';
    this.success = '';
    this.svc.updateMembreRole(this.societeId, m.userId, { nouveauRole: newRole }).subscribe({
      next: (updated) => {
        const idx = this.membres.findIndex((x) => x.userId === updated.userId);
        if (idx >= 0) {
          this.membres[idx] = updated;
          this.roleEditing[updated.userId] = updated.role;
        }
        this.success = this.i18n.instant('superadmin.members.roleUpdated', { user: updated.email ?? updated.userId });
      },
      error: (err: HttpErrorResponse) => {
        // Reset to current role
        this.roleEditing[m.userId] = m.role;
        this.error = this.extractError(err);
      },
    });
  }

  removeMembre(m: MembreSocieteDto): void {
    if (!confirm(this.i18n.instant('superadmin.members.removeConfirm', { user: m.email ?? m.userId }))) return;
    this.error = '';
    this.success = '';
    this.svc.removeMembre(this.societeId, m.userId).subscribe({
      next: () => {
        this.success = this.i18n.instant('superadmin.members.removed', { user: m.email ?? m.userId });
        this.load();
      },
      error: (err: HttpErrorResponse) => {
        this.error = this.extractError(err);
      },
    });
  }

  impersonate(m: MembreSocieteDto): void {
    if (!confirm(this.i18n.instant('superadmin.members.impersonateConfirm', { user: m.email ?? m.userId }))) return;
    this.impersonating[m.userId] = true;
    this.error = '';
    this.svc.impersonate(this.societeId, m.userId).subscribe({
      next: () => {
        this.impersonating[m.userId] = false;
        // Backend sets the impersonation JWT as an httpOnly cookie.
        // Navigate to CRM — /auth/me will return isImpersonating=true.
        this.router.navigateByUrl('/app/properties');
      },
      error: (err: HttpErrorResponse) => {
        this.impersonating[m.userId] = false;
        this.error = this.extractError(err);
      },
    });
  }

  roleClass(role: string): string {
    switch (role) {
      case 'ADMIN':
      case 'ROLE_ADMIN': return 'badge-role-admin';
      case 'MANAGER':
      case 'ROLE_MANAGER': return 'badge-role-manager';
      default: return 'badge-role-agent';
    }
  }

  private extractError(err: HttpErrorResponse): string {
    if (err.status === 401) return this.i18n.instant('superadmin.errors.session');
    if (err.status === 403) return this.i18n.instant('superadmin.errors.accessDenied');
    if (err.status === 404) return this.i18n.instant('superadmin.errors.notFoundResource');
    const body = err.error as { message?: string } | null;
    if (body?.message) return body.message;
    return this.i18n.instant('superadmin.errors.generic', { status: err.status });
  }
}
