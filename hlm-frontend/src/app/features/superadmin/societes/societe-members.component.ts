import { Component, inject, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { SocieteService } from './societe.service';
import { InviteUserRequest, MembreSocieteDto } from './societe.model';

@Component({
  selector: 'app-societe-members',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './societe-members.component.html',
  styleUrl: './societe-members.component.css',
})
export class SocieteMembersComponent implements OnInit {
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
      this.error = 'Email, prénom et nom de famille sont requis.';
      return;
    }
    this.inviting = true;
    this.error = '';
    this.success = '';
    this.svc.inviteUser(this.societeId, this.inviteForm).subscribe({
      next: () => {
        this.inviting = false;
        this.success = `Invitation envoyée à ${this.inviteForm.email}.`;
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
        this.success = `Rôle de ${updated.email ?? updated.userId} mis à jour.`;
      },
      error: (err: HttpErrorResponse) => {
        // Reset to current role
        this.roleEditing[m.userId] = m.role;
        this.error = this.extractError(err);
      },
    });
  }

  removeMembre(m: MembreSocieteDto): void {
    if (!confirm(`Retirer ${m.email ?? m.userId} de la société ?`)) return;
    this.error = '';
    this.success = '';
    this.svc.removeMembre(this.societeId, m.userId).subscribe({
      next: () => {
        this.success = `Membre ${m.email ?? m.userId} retiré.`;
        this.load();
      },
      error: (err: HttpErrorResponse) => {
        this.error = this.extractError(err);
      },
    });
  }

  impersonate(m: MembreSocieteDto): void {
    if (!confirm(`Usurper l'identité de ${m.email ?? m.userId} ? Vous pourrez revenir via le bandeau.`)) return;
    this.impersonating[m.userId] = true;
    this.error = '';
    this.svc.impersonate(this.societeId, m.userId).subscribe({
      next: (res) => {
        this.impersonating[m.userId] = false;
        const currentToken = localStorage.getItem('hlm_access_token');
        if (currentToken) {
          localStorage.setItem('hlm_superadmin_original_token', currentToken);
        }
        localStorage.setItem('hlm_access_token', res.token);
        localStorage.setItem('hlm_impersonation_active', 'true');
        localStorage.setItem('hlm_impersonation_target', res.targetUserEmail);
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
    if (err.status === 401) return 'Session expirée. Veuillez vous reconnecter.';
    if (err.status === 403) return 'Accès refusé.';
    if (err.status === 404) return 'Ressource introuvable.';
    const body = err.error as { message?: string } | null;
    if (body?.message) return body.message;
    return `Erreur (${err.status})`;
  }
}
