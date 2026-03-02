import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/auth/auth.service';
import { ReceivablesDashboardService } from './receivables-dashboard.service';
import { ReceivablesDashboard } from '../../core/models/receivables-dashboard.model';

@Component({
  selector: 'app-receivables-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './receivables-dashboard.component.html',
})
export class ReceivablesDashboardComponent implements OnInit {
  private svc  = inject(ReceivablesDashboardService);
  private auth = inject(AuthService);

  data: ReceivablesDashboard | null = null;
  loading = false;
  error   = '';

  get isAdminOrManager(): boolean {
    const role = this.auth.user?.role;
    return role === 'ROLE_ADMIN' || role === 'ROLE_MANAGER';
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error   = '';
    this.svc.getSummary().subscribe({
      next: d => { this.data = d; this.loading = false; },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        this.error = err.error?.message ?? `Failed to load (${err.status})`;
      },
    });
  }

  formatAmount(v: number | null | undefined): string {
    if (v == null) return '—';
    return new Intl.NumberFormat('fr-MA', { style: 'decimal', maximumFractionDigits: 0 }).format(v) + ' MAD';
  }

  formatPct(v: number | null | undefined): string {
    if (v == null) return '—';
    return v.toFixed(1) + '%';
  }
}
