import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { AuthService } from '../../core/auth/auth.service';
import { ProjectService } from '../projects/project.service';
import { CommercialDashboardService } from './commercial-dashboard.service';
import {
  CommercialDashboardSummary,
  DailyPoint,
  DashboardParams,
} from '../../core/models/commercial-dashboard.model';
import { Project } from '../../core/models/project.model';

@Component({
  selector: 'app-commercial-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TranslateModule],
  templateUrl: './commercial-dashboard.component.html',
  styleUrl: './commercial-dashboard.component.css',
})
export class CommercialDashboardComponent implements OnInit {
  private svc     = inject(CommercialDashboardService);
  private auth    = inject(AuthService);
  private projSvc = inject(ProjectService);

  summary: CommercialDashboardSummary | null = null;
  projects: Project[] = [];

  loading = false;
  error   = '';

  // Filters
  fromDate  = '';
  toDate    = '';
  projectId = '';
  agentId   = '';

  get isAdminOrManager(): boolean {
    const role = this.auth.user?.role;
    return role === 'ROLE_ADMIN' || role === 'ROLE_MANAGER';
  }

  get inventoryEntries(): { label: string; value: number }[] {
    if (!this.summary?.inventoryByStatus) return [];
    return Object.entries(this.summary.inventoryByStatus)
      .map(([k, v]) => ({ label: k, value: v }));
  }

  get inventoryTypeEntries(): { label: string; value: number }[] {
    if (!this.summary?.inventoryByType) return [];
    return Object.entries(this.summary.inventoryByType)
      .map(([k, v]) => ({ label: k, value: v }));
  }

  get maxSalesDay(): number {
    return Math.max(1, ...(this.summary?.salesAmountByDay ?? []).map(p => p.amount));
  }

  get maxDepositsDay(): number {
    return Math.max(1, ...(this.summary?.depositsAmountByDay ?? []).map(p => p.amount));
  }

  ngOnInit(): void {
    if (this.isAdminOrManager) {
      this.projSvc.list(true).subscribe({ next: p => (this.projects = p) });
    }
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error   = '';
    const params: DashboardParams = {};
    if (this.fromDate)  params['from']      = this.fromDate + 'T00:00:00';
    if (this.toDate)    params['to']        = this.toDate   + 'T23:59:59';
    if (this.projectId) params['projectId'] = this.projectId;
    if (this.agentId)   params['agentId']   = this.agentId;

    this.svc.getSummary(params).subscribe({
      next: data => {
        this.summary = data;
        this.loading = false;
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        if (err.status === 403) {
          this.error = 'Dashboard not available for your role.';
        } else if (err.status === 400) {
          this.error = 'Invalid filter: ' + (err.error?.message ?? 'bad request');
        } else {
          this.error = err.error?.message ?? `Failed to load dashboard (${err.status})`;
        }
      },
    });
  }

  barHeight(point: DailyPoint, maxVal: number, maxPx = 80): number {
    if (!point.amount || maxVal <= 0) return 0;
    return Math.max(2, Math.round((point.amount / maxVal) * maxPx));
  }

  formatAmount(value: number | null | undefined): string {
    if (value == null) return '—';
    return new Intl.NumberFormat('fr-MA', {
      style: 'decimal',
      maximumFractionDigits: 0,
    }).format(value) + ' MAD';
  }

  formatPct(value: number | null | undefined): string {
    if (value == null) return '—';
    return (value * 100).toFixed(1) + '%';
  }
}
