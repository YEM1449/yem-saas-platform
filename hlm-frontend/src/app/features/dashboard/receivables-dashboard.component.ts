import {
  Component, inject, OnInit, OnDestroy,
  ViewChild, ElementRef, ChangeDetectorRef,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import {
  Chart,
  BarController, BarElement, CategoryScale, LinearScale,
  DoughnutController, ArcElement,
  Tooltip, Legend,
} from 'chart.js';
import { AuthService } from '../../core/auth/auth.service';
import { ReceivablesDashboardService, VenteReceivablesSummary } from './receivables-dashboard.service';
import { ReceivablesDashboard } from '../../core/models/receivables-dashboard.model';

Chart.register(BarController, BarElement, CategoryScale, LinearScale,
  DoughnutController, ArcElement, Tooltip, Legend);

@Component({
  selector: 'app-receivables-dashboard',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './receivables-dashboard.component.html',
  styleUrl: './receivables-dashboard.component.css',
})
export class ReceivablesDashboardComponent implements OnInit, OnDestroy {
  @ViewChild('agingCanvas')   agingRef!:   ElementRef<HTMLCanvasElement>;
  @ViewChild('projectCanvas') projectRef!: ElementRef<HTMLCanvasElement>;

  private charts: Chart[] = [];
  private cdr = inject(ChangeDetectorRef);
  private svc  = inject(ReceivablesDashboardService);
  private auth = inject(AuthService);

  data: ReceivablesDashboard | null = null;
  venteReceivables: VenteReceivablesSummary | null = null;
  loading = false;
  error   = '';

  get isAdminOrManager(): boolean {
    const role = this.auth.user?.role;
    return role === 'ROLE_ADMIN' || role === 'ROLE_MANAGER';
  }

  ngOnInit(): void {
    this.load();
    this.loadVenteReceivables();
  }

  loadVenteReceivables(): void {
    this.svc.getVenteReceivables().subscribe({
      next: d => { this.venteReceivables = d; },
      error: () => { /* non-blocking — vente receivables section stays hidden */ },
    });
  }

  ngOnDestroy(): void {
    this.charts.forEach(c => c.destroy());
  }

  load(): void {
    this.loading = true;
    this.error   = '';
    this.svc.getSummary().subscribe({
      next: d => {
        this.data = d;
        this.loading = false;
        this.cdr.detectChanges();
        setTimeout(() => this.buildCharts(), 50);
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        this.error = err.error?.message ?? `Failed to load (${err.status})`;
      },
    });
  }

  private buildCharts(): void {
    if (!this.data) return;
    this.charts.forEach(c => c.destroy());
    this.charts = [];
    const d = this.data;

    // ── Aging buckets doughnut ────────────────────────────────────
    if (this.agingRef?.nativeElement) {
      this.charts.push(new Chart(this.agingRef.nativeElement, {
        type: 'doughnut',
        data: {
          labels: ['Not yet due', '1–30 d', '31–60 d', '61–90 d', '>90 d'],
          datasets: [{
            data: [
              d.current.amount,
              d.days30.amount,
              d.days60.amount,
              d.days90.amount,
              d.days90plus.amount,
            ],
            backgroundColor: ['#66bb6a','#ffa726','#ef5350','#c62828','#7b1fa2'],
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

    // ── Overdue by project (horizontal bar) ───────────────────────
    if (this.projectRef?.nativeElement && d.overdueByProject?.length) {
      this.charts.push(new Chart(this.projectRef.nativeElement, {
        type: 'bar',
        data: {
          labels: d.overdueByProject.map(r => r.projectName ?? r.projectId?.substring(0, 8)),
          datasets: [{
            label: 'Overdue (MAD)',
            data: d.overdueByProject.map(r => r.overdueAmount),
            backgroundColor: 'rgba(230,81,0,0.75)',
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
  }

  chartHeight(items: unknown[]): number {
    return Math.max(120, items.length * 32);
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
