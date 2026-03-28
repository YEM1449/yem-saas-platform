import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { AuditService } from './audit.service';
import { AuthService } from '../../core/auth/auth.service';
import { AuditEventResponse } from '../../core/models/audit.model';

@Component({
  selector: 'app-audit',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './audit.component.html',
})
export class AuditComponent implements OnInit {
  private auditService = inject(AuditService);
  private authService = inject(AuthService);

  events: AuditEventResponse[] = [];
  loading = false;
  error: string | null = null;
  from = '';
  to = '';

  get isAuthorized(): boolean {
    const role = this.authService.user?.role;
    return role === 'ROLE_ADMIN' || role === 'ROLE_MANAGER';
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    if (!this.isAuthorized) {
      this.error = 'Accès refusé';
      return;
    }
    this.loading = true;
    this.error = null;
    const params: { from?: string; to?: string; limit?: number } = { limit: 200 };
    if (this.from) params.from = this.from;
    if (this.to) params.to = this.to;
    this.auditService.getCommercialAudit(params).subscribe({
      next: (data) => {
        this.events = data;
        this.loading = false;
      },
      error: (err) => {
        this.error = err.status === 403 ? 'Accès refusé' : 'Erreur lors du chargement des événements.';
        this.loading = false;
      },
    });
  }

  actorShort(actorUserId: string): string {
    return actorUserId ? actorUserId.substring(0, 8).toUpperCase() : '—';
  }
}
