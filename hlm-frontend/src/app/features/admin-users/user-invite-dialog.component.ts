import { Component, EventEmitter, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import { AdminUserService } from './admin-user.service';
import { MembreDto, InviterUtilisateurRequest } from './admin-user.model';
import { ErrorResponse } from '../../core/models/error-response.model';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-user-invite-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './user-invite-dialog.component.html',
  styles: [`
    .overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.45); z-index: 100; }
    .modal { position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%); z-index: 101;
             background: #fff; border-radius: 8px; padding: 1.5rem; width: min(480px, 95vw);
             box-shadow: 0 8px 32px rgba(0,0,0,0.18); }
    h3 { margin: 0 0 1rem; font-size: 1.1rem; font-weight: 600; }
    label { display: flex; flex-direction: column; gap: 4px; font-size: 0.875rem; font-weight: 500;
            color: #374151; margin-bottom: 0.75rem; }
    input, select { padding: 0.4rem 0.6rem; border: 1px solid #d1d5db; border-radius: 4px; font-size: 0.875rem; }
    input:focus, select:focus { outline: none; border-color: #6366f1; }
    .required { color: #ef4444; }
    .actions { display: flex; justify-content: flex-end; gap: 0.5rem; margin-top: 1.25rem; }
    .btn-primary { background: #6366f1; color: #fff; border: none; padding: 0.4rem 1rem; border-radius: 4px; cursor: pointer; font-size: 0.875rem; }
    .btn-primary:disabled { opacity: 0.6; cursor: default; }
    .btn-secondary { background: #f3f4f6; color: #374151; border: 1px solid #d1d5db; padding: 0.4rem 1rem; border-radius: 4px; cursor: pointer; font-size: 0.875rem; }
    .error { color: #ef4444; font-size: 0.875rem; margin-bottom: 0.5rem; }
  `],
})
export class UserInviteDialogComponent {
  @Output() invited = new EventEmitter<MembreDto>();
  @Output() cancelled = new EventEmitter<void>();

  private svc  = inject(AdminUserService);
  private auth = inject(AuthService);

  email = '';
  prenom = '';
  nomFamille = '';
  telephone = '';
  poste = '';
  role = 'AGENT';
  submitting = false;
  error = '';

  get availableRoles(): string[] {
    return this.auth.user?.role === 'ROLE_ADMIN' ? ['MANAGER', 'AGENT'] : ['ADMIN', 'MANAGER', 'AGENT'];
  }

  submit(): void {
    if (!this.email.trim() || !this.prenom.trim() || !this.nomFamille.trim()) {
      this.error = 'Email, prénom et nom sont obligatoires.';
      return;
    }
    this.submitting = true;
    this.error = '';
    const req: InviterUtilisateurRequest = {
      email:      this.email.trim(),
      prenom:     this.prenom.trim(),
      nomFamille: this.nomFamille.trim(),
      telephone:  this.telephone.trim() || undefined,
      poste:      this.poste.trim() || undefined,
      role:       this.role,
    };
    this.svc.inviter(req).subscribe({
      next: (m) => { this.submitting = false; this.invited.emit(m); },
      error: (err: HttpErrorResponse) => {
        this.submitting = false;
        const body = err.error as ErrorResponse | null;
        if (err.status === 403 && body?.code === 'ROLE_ESCALATION_FORBIDDEN') {
          this.error = 'Action non autorisée : seul un Super Administrateur peut attribuer le rôle Administrateur.';
        } else {
          this.error = body?.message ?? `Erreur (${err.status})`;
        }
      },
    });
  }

  cancel(): void {
    this.cancelled.emit();
  }
}
