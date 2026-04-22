import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReceivablesDashboardService } from '../dashboard/receivables-dashboard.service';
import {
  ReceivablesDashboard,
  OverdueByProjectRow,
} from '../../core/models/receivables-dashboard.model';

interface AgingRow {
  label: string;
  count: number;
  amount: number;
}

@Component({
  selector: 'app-creances-report',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './creances-report.component.html',
  styleUrl: './creances-report.component.css',
})
export class CreancesReportComponent implements OnInit {
  private svc = inject(ReceivablesDashboardService);

  data      = signal<ReceivablesDashboard | null>(null);
  loading   = signal(true);
  error     = signal('');

  ngOnInit(): void {
    this.svc.getSummary().subscribe({
      next:  d  => { this.data.set(d); this.loading.set(false); },
      error: () => { this.error.set('Erreur lors du chargement.'); this.loading.set(false); },
    });
  }

  get agingRows(): AgingRow[] {
    const d = this.data();
    if (!d) return [];
    return [
      { label: 'Courant (non échu)',  count: d.current.count,    amount: d.current.amount },
      { label: '1 – 30 jours',        count: d.days30.count,     amount: d.days30.amount },
      { label: '31 – 60 jours',       count: d.days60.count,     amount: d.days60.amount },
      { label: '61 – 90 jours',       count: d.days90.count,     amount: d.days90.amount },
      { label: '> 90 jours',          count: d.days90plus.count, amount: d.days90plus.amount },
    ];
  }

  get totalBucketCount(): number {
    return this.agingRows.reduce((s, r) => s + r.count, 0);
  }

  printPage(): void { window.print(); }

  formatAmount(n: number | null | undefined): string {
    if (n == null || n === 0) return '—';
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + ' M MAD';
    if (n >= 1_000)     return (n / 1_000).toFixed(0) + ' K MAD';
    return n.toLocaleString('fr-FR') + ' MAD';
  }

  formatPct(n: number | null): string {
    return n != null ? n.toFixed(1) + ' %' : '—';
  }

  isOverdue(r: AgingRow): boolean {
    return r.label !== 'Courant (non échu)';
  }
}
