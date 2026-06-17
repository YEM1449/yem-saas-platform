import {
  Component, inject, OnInit, OnDestroy,
  ViewChild, ElementRef, ChangeDetectorRef,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { I18nService } from '../../core/i18n/i18n.service';
import {
  Chart,
  BarController, BarElement, CategoryScale, LinearScale,
  Tooltip, Legend,
} from 'chart.js';
import { PaymentScheduleService } from '../contracts/payment-schedule.service';
import { CashDashboardResponse } from '../../core/models/payment-schedule.model';

Chart.register(BarController, BarElement, CategoryScale, LinearScale, Tooltip, Legend);

@Component({
  selector: 'app-cash-dashboard',
  standalone: true,
  imports: [FormsModule, DecimalPipe, TranslatePipe],
  templateUrl: './cash-dashboard.component.html',
  styleUrl: './cash-dashboard.component.css',
})
export class CashDashboardComponent implements OnInit, OnDestroy {
  @ViewChild('kpiCanvas')   kpiRef!:   ElementRef<HTMLCanvasElement>;
  @ViewChild('agingCanvas') agingRef!: ElementRef<HTMLCanvasElement>;

  private charts: Chart[] = [];
  private cdr = inject(ChangeDetectorRef);
  private i18n = inject(I18nService);
  private svc = inject(PaymentScheduleService);

  data: CashDashboardResponse | null = null;
  loading = false;
  error   = '';

  // Default window: current calendar month
  from = new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().slice(0, 10);
  to   = new Date().toISOString().slice(0, 10);

  ngOnInit(): void { this.load(); }

  ngOnDestroy(): void {
    this.charts.forEach(c => c.destroy());
  }

  load(): void {
    this.loading = true;
    this.error   = '';
    this.svc.getCashDashboard(this.from, this.to).subscribe({
      next: d => {
        this.data = d;
        this.loading = false;
        this.cdr.detectChanges();
        setTimeout(() => this.buildCharts(), 50);
      },
      error: () => { this.error = this.i18n.instant('dashboard.cash.loadError'); this.loading = false; },
    });
  }

  private buildCharts(): void {
    if (!this.data) return;
    this.charts.forEach(c => c.destroy());
    this.charts = [];
    const d = this.data;

    // ── KPI summary bar ───────────────────────────────────────────
    if (this.kpiRef?.nativeElement) {
      this.charts.push(new Chart(this.kpiRef.nativeElement, {
        type: 'bar',
        data: {
          labels: [this.i18n.instant('dashboard.cash.expected'), this.i18n.instant('dashboard.cash.issued'), this.i18n.instant('dashboard.cash.collected'), this.i18n.instant('dashboard.cash.overdue')],
          datasets: [{
            data: [d.expectedInPeriod, d.issuedInPeriod, d.collectedInPeriod, d.overdueAmount],
            backgroundColor: ['#42a5f5','#66bb6a','#26c6da','#ef5350'],
            borderWidth: 0,
          }],
        },
        options: {
          responsive: true,
          maintainAspectRatio: true,
          plugins: { legend: { display: false } },
          scales: { y: { beginAtZero: true, ticks: { maxTicksLimit: 5 } } },
        },
      }));
    }

    // ── Aging buckets bar ─────────────────────────────────────────
    if (this.agingRef?.nativeElement && d.agingBuckets?.length) {
      this.charts.push(new Chart(this.agingRef.nativeElement, {
        type: 'bar',
        data: {
          labels: d.agingBuckets.map(b => b.label),
          datasets: [{
            label: this.i18n.instant('dashboard.cash.amountLabel'),
            data: d.agingBuckets.map(b => b.totalAmount),
            backgroundColor: 'rgba(230,81,0,0.75)',
          }],
        },
        options: {
          responsive: true,
          maintainAspectRatio: true,
          plugins: { legend: { display: false } },
          scales: { y: { beginAtZero: true } },
        },
      }));
    }
  }
}
