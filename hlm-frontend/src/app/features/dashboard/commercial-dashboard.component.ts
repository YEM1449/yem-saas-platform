import {
  Component, inject, OnInit, OnDestroy, AfterViewInit,
  ViewChild, ElementRef, ChangeDetectorRef,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import {
  Chart,
  BarController, BarElement, CategoryScale, LinearScale,
  DoughnutController, ArcElement,
  Tooltip, Legend,
} from 'chart.js';
import { AuthService } from '../../core/auth/auth.service';
import { ProjectService } from '../projects/project.service';
import { CommercialDashboardService } from './commercial-dashboard.service';
import {
  CommercialDashboardSummary,
  DashboardParams,
} from '../../core/models/commercial-dashboard.model';
import { Project } from '../../core/models/project.model';

// Register only what we use
Chart.register(BarController, BarElement, CategoryScale, LinearScale,
  DoughnutController, ArcElement, Tooltip, Legend);

@Component({
  selector: 'app-commercial-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TranslateModule],
  templateUrl: './commercial-dashboard.component.html',
  styleUrl: './commercial-dashboard.component.css',
})
export class CommercialDashboardComponent implements OnInit, OnDestroy, AfterViewInit {
  @ViewChild('salesByDayCanvas')    salesByDayRef!:    ElementRef<HTMLCanvasElement>;
  @ViewChild('depositsByDayCanvas') depositsByDayRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('salesByProjectCanvas') salesByProjectRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('salesByAgentCanvas')  salesByAgentRef!:  ElementRef<HTMLCanvasElement>;
  @ViewChild('inventoryCanvas')     inventoryRef!:     ElementRef<HTMLCanvasElement>;
  @ViewChild('prospectsCanvas')     prospectsRef!:     ElementRef<HTMLCanvasElement>;

  private charts: Chart[] = [];
  private cdr = inject(ChangeDetectorRef);

  private svc     = inject(CommercialDashboardService);
  private auth    = inject(AuthService);
  private projSvc = inject(ProjectService);
  private router  = inject(Router);

  summary: CommercialDashboardSummary | null = null;
  projects: Project[] = [];

  loading = false;
  error   = '';

  // Filters
  fromDate  = '';
  toDate    = '';
  projectId = '';
  agentId   = '';

  viewReady = false;

  get isAdminOrManager(): boolean {
    const role = this.auth.user?.role;
    return role === 'ROLE_ADMIN' || role === 'ROLE_MANAGER';
  }

  get inventoryEntries(): { label: string; value: number }[] {
    if (!this.summary?.inventoryByStatus) return [];
    return Object.entries(this.summary.inventoryByStatus).map(([k, v]) => ({ label: k, value: v }));
  }

  get inventoryTypeEntries(): { label: string; value: number }[] {
    if (!this.summary?.inventoryByType) return [];
    return Object.entries(this.summary.inventoryByType).map(([k, v]) => ({ label: k, value: v }));
  }

  get pipelineEntries(): { statut: string; count: number }[] {
    if (!this.summary?.ventesParStatut) return [];
    return Object.entries(this.summary.ventesParStatut)
      .map(([statut, count]) => ({ statut, count: Number(count) }))
      .sort((a, b) => b.count - a.count);
  }

  get totalActivePipeline(): number {
    return this.pipelineEntries.reduce((s, e) => s + e.count, 0);
  }

  goVentes(statut?: string): void {
    const extras = statut ? { queryParams: { statut } } : {};
    this.router.navigate(['/app/ventes'], extras);
  }

  goReservations(): void { this.router.navigate(['/app/reservations']); }
  goProperties(status?: string): void {
    const extras = status ? { queryParams: { status } } : {};
    this.router.navigate(['/app/properties'], extras);
  }

  statutLabel(s: string): string {
    const map: Record<string, string> = {
      COMPROMIS:     'Compromis',
      FINANCEMENT:   'Financement',
      ACTE_NOTARIE:  'Acte notarié',
      LIVRE:         'Livré',
      ANNULE:        'Annulé',
    };
    return map[s] ?? s;
  }

  statutColor(s: string): string {
    const map: Record<string, string> = {
      COMPROMIS:    '#42a5f5',
      FINANCEMENT:  '#66bb6a',
      ACTE_NOTARIE: '#ab47bc',
      LIVRE:        '#26c6da',
      ANNULE:       '#ef5350',
    };
    return map[s] ?? '#bdbdbd';
  }

  ngOnInit(): void {
    if (this.isAdminOrManager) {
      this.projSvc.list(true).subscribe({ next: p => (this.projects = p) });
    }
    this.load();
  }

  ngAfterViewInit(): void {
    this.viewReady = true;
  }

  ngOnDestroy(): void {
    this.destroyCharts();
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
        this.cdr.detectChanges();
        // Allow DOM to render canvases before building charts
        setTimeout(() => this.buildCharts(), 50);
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        if (err.status === 403) {
          this.error = 'Dashboard not available for your role.';
        } else {
          this.error = err.error?.message ?? `Failed to load dashboard (${err.status})`;
        }
      },
    });
  }

  private destroyCharts(): void {
    this.charts.forEach(c => c.destroy());
    this.charts = [];
  }

  private buildCharts(): void {
    if (!this.summary) return;
    this.destroyCharts();

    const s = this.summary;

    // ── Sales by day (bar) ────────────────────────────────────────────
    if (this.salesByDayRef?.nativeElement && s.salesAmountByDay?.length) {
      this.charts.push(new Chart(this.salesByDayRef.nativeElement, {
        type: 'bar',
        data: {
          labels: s.salesAmountByDay.map(p => p.date),
          datasets: [{
            label: 'Sales (MAD)',
            data: s.salesAmountByDay.map(p => p.amount),
            backgroundColor: 'rgba(67,160,71,0.75)',
            borderColor: '#43a047',
            borderWidth: 1,
          }],
        },
        options: {
          responsive: true,
          maintainAspectRatio: true,
          plugins: { legend: { display: false } },
          scales: {
            y: { beginAtZero: true, ticks: { maxTicksLimit: 5 } },
            x: { ticks: { maxRotation: 45, maxTicksLimit: 10 } },
          },
        },
      }));
    }

    // ── Deposits by day (bar) ─────────────────────────────────────────
    if (this.depositsByDayRef?.nativeElement && s.depositsAmountByDay?.length) {
      this.charts.push(new Chart(this.depositsByDayRef.nativeElement, {
        type: 'bar',
        data: {
          labels: s.depositsAmountByDay.map(p => p.date),
          datasets: [{
            label: 'Deposits (MAD)',
            data: s.depositsAmountByDay.map(p => p.amount),
            backgroundColor: 'rgba(25,118,210,0.75)',
            borderColor: '#1976d2',
            borderWidth: 1,
          }],
        },
        options: {
          responsive: true,
          maintainAspectRatio: true,
          plugins: { legend: { display: false } },
          scales: {
            y: { beginAtZero: true, ticks: { maxTicksLimit: 5 } },
            x: { ticks: { maxRotation: 45, maxTicksLimit: 10 } },
          },
        },
      }));
    }

    // ── Sales by project (horizontal bar) ────────────────────────────
    if (this.salesByProjectRef?.nativeElement && s.salesByProject?.length) {
      this.charts.push(new Chart(this.salesByProjectRef.nativeElement, {
        type: 'bar',
        data: {
          labels: s.salesByProject.map(r => r.projectName ?? 'N/A'),
          datasets: [{
            label: 'Amount (MAD)',
            data: s.salesByProject.map(r => r.salesAmount),
            backgroundColor: 'rgba(67,160,71,0.7)',
          }],
        },
        options: {
          indexAxis: 'y',
          responsive: true,
          maintainAspectRatio: false,
          plugins: { legend: { display: false } },
          scales: { x: { beginAtZero: true } },
        },
      }));
    }

    // ── Sales by agent (horizontal bar) ──────────────────────────────
    if (this.salesByAgentRef?.nativeElement && s.salesByAgent?.length) {
      this.charts.push(new Chart(this.salesByAgentRef.nativeElement, {
        type: 'bar',
        data: {
          labels: s.salesByAgent.map(r => r.agentEmail?.split('@')[0] ?? r.agentId?.substring(0, 8)),
          datasets: [{
            label: 'Amount (MAD)',
            data: s.salesByAgent.map(r => r.salesAmount),
            backgroundColor: 'rgba(25,118,210,0.7)',
          }],
        },
        options: {
          indexAxis: 'y',
          responsive: true,
          maintainAspectRatio: false,
          plugins: { legend: { display: false } },
          scales: { x: { beginAtZero: true } },
        },
      }));
    }

    // ── Inventory by status (doughnut) ───────────────────────────────
    if (this.inventoryRef?.nativeElement && this.inventoryEntries.length) {
      const statusColors: Record<string, string> = {
        ACTIVE: '#e8f5e9', RESERVED: '#fff3e0', SOLD: '#e3f2fd',
        DRAFT: '#f3e5f5',
      };
      this.charts.push(new Chart(this.inventoryRef.nativeElement, {
        type: 'doughnut',
        data: {
          labels: this.inventoryEntries.map(e => e.label),
          datasets: [{
            data: this.inventoryEntries.map(e => e.value),
            backgroundColor: this.inventoryEntries.map(e =>
              statusColors[e.label] ?? 'rgba(158,158,158,0.5)'),
            borderWidth: 2,
          }],
        },
        options: {
          responsive: true,
          maintainAspectRatio: true,
          plugins: { legend: { position: 'bottom' } },
        },
      }));
    }

    // ── Prospect sources (doughnut) ───────────────────────────────────
    if (this.prospectsRef?.nativeElement && s.prospectsBySource?.length) {
      const palette = ['#42a5f5','#66bb6a','#ffa726','#ef5350','#ab47bc','#26c6da'];
      this.charts.push(new Chart(this.prospectsRef.nativeElement, {
        type: 'doughnut',
        data: {
          labels: s.prospectsBySource.map(r => r.source ?? 'Unknown'),
          datasets: [{
            data: s.prospectsBySource.map(r => r.count),
            backgroundColor: s.prospectsBySource.map((_, i) => palette[i % palette.length]),
            borderWidth: 2,
          }],
        },
        options: {
          responsive: true,
          maintainAspectRatio: true,
          plugins: { legend: { position: 'bottom' } },
        },
      }));
    }
  }

  chartHeight(items: unknown[]): number {
    return Math.max(100, items.length * 32);
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
