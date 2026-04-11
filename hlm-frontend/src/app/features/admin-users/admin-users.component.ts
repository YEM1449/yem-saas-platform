import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { AdminUserService } from './admin-user.service';
import { MembreDto, MembreStatut } from './admin-user.model';
import { UserInviteDialogComponent } from './user-invite-dialog.component';
import { ErrorResponse } from '../../core/models/error-response.model';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [CommonModule, FormsModule, UserInviteDialogComponent, TranslateModule],
  templateUrl: './admin-users.component.html',
  styleUrl: './admin-users.component.css',
})
export class AdminUsersComponent implements OnInit {
  private svc  = inject(AdminUserService);
  private auth = inject(AuthService);

  membres: MembreDto[] = [];
  loading = true;
  error = '';
  success = '';

  // Filters
  search = '';
  filterRole = '';
  filterActif: '' | 'true' | 'false' = '';

  // Pagination
  page = 0;
  size = 20;
  totalPages = 0;
  totalElements = 0;

  // Dialog
  showInviteDialog = false;

  // GDPR export
  exportData: string | null = null;

  readonly roles = ['', 'ADMIN', 'MANAGER', 'AGENT'];

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.svc.list({
      search: this.search.trim() || undefined,
      role:   this.filterRole  || undefined,
      actif:  this.filterActif === '' ? undefined : this.filterActif === 'true',
      page:   this.page,
      size:   this.size,
    }).subscribe({
      next: (page) => {
        this.membres = page.content;
        this.totalPages = page.totalPages;
        this.totalElements = page.totalElements;
        this.loading = false;
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        const body = err.error as ErrorResponse | null;
        this.error = body?.message ?? `Erreur (${err.status})`;
      },
    });
  }

  applyFilter(): void { this.page = 0; this.load(); }
  prevPage(): void { if (this.page > 0) { this.page--; this.load(); } }
  nextPage(): void { if (this.page + 1 < this.totalPages) { this.page++; this.load(); } }

  onInvited(m: MembreDto): void {
    this.showInviteDialog = false;
    this.success = `Invitation envoyée à ${m.email}.`;
    this.load();
  }

  reinviter(m: MembreDto): void {
    this.error = '';
    this.svc.reinviter(m.id).subscribe({
      next: () => { this.success = `Invitation renvoyée à ${m.email}.`; },
      error: (err: HttpErrorResponse) => { this.error = this.extractError(err); },
    });
  }

  changerRole(m: MembreDto, role: string): void {
    if (role === m.role) return;
    this.error = '';
    this.svc.changerRole(m.id, { nouveauRole: role, version: m.version }).subscribe({
      next: (updated) => {
        this.membres = this.membres.map(u => u.id === updated.id ? updated : u);
        this.success = `Rôle mis à jour pour ${updated.email}.`;
      },
      error: (err: HttpErrorResponse) => { this.error = this.extractError(err); },
    });
  }

  debloquer(m: MembreDto): void {
    this.error = '';
    this.svc.debloquer(m.id).subscribe({
      next: (updated) => {
        this.membres = this.membres.map(u => u.id === updated.id ? updated : u);
        this.success = `Compte de ${updated.email} débloqué.`;
      },
      error: (err: HttpErrorResponse) => { this.error = this.extractError(err); },
    });
  }

  retirer(m: MembreDto): void {
    if (!confirm(`Retirer ${m.nomComplet} de la société ?`)) return;
    this.error = '';
    this.svc.retirer(m.id, { version: m.version }).subscribe({
      next: () => { this.success = `${m.nomComplet} retiré.`; this.load(); },
      error: (err: HttpErrorResponse) => { this.error = this.extractError(err); },
    });
  }

  exportDonnees(m: MembreDto): void {
    this.svc.exportDonnees(m.id).subscribe({
      next: (data) => { this.exportData = JSON.stringify(data, null, 2); },
      error: (err: HttpErrorResponse) => { this.error = this.extractError(err); },
    });
  }

  anonymiser(m: MembreDto): void {
    if (!confirm(`ATTENTION : Anonymiser définitivement les données de ${m.nomComplet} ? Cette action est irréversible.`)) return;
    this.svc.anonymiser(m.id).subscribe({
      next: () => { this.success = `Données de ${m.nomComplet} anonymisées.`; this.load(); },
      error: (err: HttpErrorResponse) => { this.error = this.extractError(err); },
    });
  }

  statutBadgeClass(statut: MembreStatut): string {
    const map: Record<MembreStatut, string> = {
      ACTIF: 'badge-green', INVITE: 'badge-blue',
      INVITATION_EXPIREE: 'badge-amber', BLOQUE: 'badge-red', RETIRE: 'badge-gray',
    };
    return 'badge ' + (map[statut] ?? '');
  }

  get assignableRoles(): string[] {
    return this.auth.user?.role === 'ROLE_ADMIN' ? ['MANAGER', 'AGENT'] : ['ADMIN', 'MANAGER', 'AGENT'];
  }

  private extractError(err: HttpErrorResponse): string {
    if (err.status === 401) return 'Session expirée.';
    const body = err.error as ErrorResponse | null;
    if (err.status === 403 && body?.code === 'ROLE_ESCALATION_FORBIDDEN') {
      return 'Action non autorisée : seul un Super Administrateur peut attribuer le rôle Administrateur.';
    }
    return body?.message ?? `Erreur (${err.status})`;
  }
}
