import {
  Component, inject, OnInit, OnDestroy,
  ViewChild, ElementRef, ChangeDetectorRef,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
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
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './cash-dashboard.component.html',
  styleUrl: './cash-dashboard.component.css',
})
export class CashDashboardComponent implements OnInit, OnDestroy {
  @ViewChild('kpiCanvas')   kpiRef!:   ElementRef<HTMLCanvasElement>;
  @ViewChild('agingCanvas') agingRef!: ElementRef<HTMLCanvasElement>;

  private charts: Chart[] = [];
  private cdr = inject(ChangeDetectorRef);
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
      error: () => { this.error = 'Failed to load cash dashboard'; this.loading = false; },
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
          labels: ['Expected', 'Issued', 'Collected', 'Overdue'],
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
            label: 'Amount (MAD)',
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
