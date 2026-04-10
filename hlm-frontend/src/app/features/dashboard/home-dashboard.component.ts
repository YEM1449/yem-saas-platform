import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HomeDashboardService, HomeDashboard } from './home-dashboard.service';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-home-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, DatePipe, DecimalPipe],
  templateUrl: './home-dashboard.component.html',
  styleUrl: './home-dashboard.component.css',
})
export class HomeDashboardComponent implements OnInit {
  private svc   = inject(HomeDashboardService);
  readonly auth = inject(AuthService);

  snap    = signal<HomeDashboard | null>(null);
  loading = signal(true);
  error   = signal('');

  get role(): string { return this.auth.user?.role ?? ''; }
  get isAgent(): boolean { return this.role === 'ROLE_AGENT'; }
  get isAdminOrManager(): boolean {
    return this.role === 'ROLE_ADMIN' || this.role === 'ROLE_MANAGER';
  }

  ngOnInit(): void {
    this.svc.getSnapshot().subscribe({
      next:  s  => { this.snap.set(s); this.loading.set(false); },
      error: () => { this.error.set('Impossible de charger le tableau de bord.'); this.loading.set(false); },
    });
  }

  readonly STATUT_LABELS: Record<string, string> = {
    COMPROMIS: 'Compromis', FINANCEMENT: 'Financement',
    ACTE_NOTARIE: 'Acte notarié', LIVRE: 'Livré', ANNULE: 'Annulé',
  };

  readonly STATUT_COLORS: Record<string, string> = {
    COMPROMIS: '#6366f1', FINANCEMENT: '#f59e0b',
    ACTE_NOTARIE: '#3b82f6', LIVRE: '#10b981', ANNULE: '#ef4444',
  };

  statutLabel(s: string): string { return this.STATUT_LABELS[s] ?? s; }
  statutColor(s: string): string { return this.STATUT_COLORS[s] ?? '#9ca3af'; }

  statutClass(s: string): string {
    const m: Record<string, string> = {
      COMPROMIS: 'badge-info', FINANCEMENT: 'badge-warning',
      ACTE_NOTARIE: 'badge-primary', LIVRE: 'badge-success', ANNULE: 'badge-error',
    };
    return m[s] ?? '';
  }

  pipelineEntries(v: Record<string, number>): { statut: string; count: number }[] {
    return ['COMPROMIS', 'FINANCEMENT', 'ACTE_NOTARIE']
      .filter(s => v[s] != null)
      .map(s => ({ statut: s, count: v[s] }));
  }

  absorptionWidth(r: number | null): string {
    if (r == null) return '0%';
    return Math.min(r, 100) + '%';
  }

  absorptionClass(r: number | null): string {
    if (r == null) return '';
    if (r >= 70) return 'bar-success';
    if (r >= 40) return 'bar-warning';
    return 'bar-danger';
  }

  isOverdue(d: string): boolean { return new Date(d) < new Date(); }

  formatAmount(n: number | null | undefined): string {
    if (n == null) return '—';
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(1).replace('.', ',') + ' M MAD';
    if (n >= 1_000)     return (n / 1_000).toFixed(0) + ' K MAD';
    return n.toLocaleString('fr-FR') + ' MAD';
  }
}
