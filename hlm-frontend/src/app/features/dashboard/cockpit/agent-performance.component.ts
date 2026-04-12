import { Component, Input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AgentPerformance, AgentRow } from '../dashboard-cockpit.service';

type SortKey = 'totalCA' | 'totalSales' | 'conversionRate' | 'avgDealSize' | 'avgDaysToClose' | 'activePipeline';

@Component({
  selector: 'app-agent-performance',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="widget">
      <div class="widget-header">
        <span class="widget-title">Performance agents</span>
        <span class="widget-sub">Toute la société</span>
      </div>

      @if (!data || data.agents.length === 0) {
        <div class="empty-state">Pas encore de données de vente par agent.</div>
      } @else {
        <div class="table-wrap">
          <table class="perf-table">
            <thead>
              <tr>
                <th>#</th>
                <th>Agent</th>
                <th class="num sortable" (click)="sort('totalCA')">CA livré {{ sortIcon('totalCA') }}</th>
                <th class="num sortable" (click)="sort('totalSales')">Ventes {{ sortIcon('totalSales') }}</th>
                <th class="num sortable" (click)="sort('conversionRate')">Conv. {{ sortIcon('conversionRate') }}</th>
                <th class="num sortable" (click)="sort('avgDealSize')">Ticket moy. {{ sortIcon('avgDealSize') }}</th>
                <th class="num sortable" (click)="sort('avgDaysToClose')">Délai moy. {{ sortIcon('avgDaysToClose') }}</th>
                <th class="num sortable" (click)="sort('activePipeline')">Pipeline {{ sortIcon('activePipeline') }}</th>
              </tr>
            </thead>
            <tbody>
              @for (a of sorted(); track a.agentId; let i = $index) {
                <tr [class.top-performer]="i === 0 && sorted().length > 1"
                    [class.bottom-performer]="i === sorted().length - 1 && sorted().length > 2">
                  <td class="rank">{{ i + 1 }}</td>
                  <td class="name">{{ a.agentName }}</td>
                  <td class="num">{{ fmt(a.totalCA) }}</td>
                  <td class="num">{{ a.totalSales }}</td>
                  <td class="num">{{ a.conversionRate != null ? a.conversionRate + '%' : '—' }}</td>
                  <td class="num">{{ a.avgDealSize != null ? fmt(a.avgDealSize) : '—' }}</td>
                  <td class="num">{{ a.avgDaysToClose != null ? a.avgDaysToClose + 'j' : '—' }}</td>
                  <td class="num">{{ a.activePipeline }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .widget { background:#fff; border:1px solid #e5e7eb; border-radius:12px; padding:18px; }
    .widget-header { display:flex; align-items:baseline; justify-content:space-between; margin-bottom:14px; }
    .widget-title { font-size:14px; font-weight:700; color:#111827; }
    .widget-sub { font-size:11px; color:#6b7280; text-transform:uppercase; letter-spacing:.04em; }
    .table-wrap { overflow-x:auto; }
    .perf-table { width:100%; border-collapse:collapse; font-size:13px; }
    .perf-table th { color:#6b7280; font-weight:600; font-size:11px; text-transform:uppercase; padding:6px 8px; border-bottom:1px solid #e5e7eb; text-align:left; white-space:nowrap; }
    .perf-table td { padding:8px; border-bottom:1px solid #f3f4f6; }
    .num { text-align:right; font-variant-numeric:tabular-nums; }
    .rank { color:#9ca3af; font-weight:600; width:30px; }
    .name { font-weight:600; color:#111827; }
    .sortable { cursor:pointer; user-select:none; }
    .sortable:hover { color:#4f46e5; }
    .top-performer td { background:#ecfdf5; }
    .top-performer .name { color:#047857; }
    .bottom-performer td { background:#fef2f2; }
    .bottom-performer .name { color:#b91c1c; }
    .empty-state { padding:20px; text-align:center; color:#6b7280; }
  `],
})
export class AgentPerformanceComponent {
  @Input() set data(value: AgentPerformance | null) {
    this._data = value;
    this.applySort();
  }
  get data(): AgentPerformance | null { return this._data; }

  private _data: AgentPerformance | null = null;
  private sortKey: SortKey = 'totalCA';
  private sortAsc = false;

  sorted = signal<AgentRow[]>([]);

  sort(key: SortKey): void {
    if (this.sortKey === key) {
      this.sortAsc = !this.sortAsc;
    } else {
      this.sortKey = key;
      this.sortAsc = false;
    }
    this.applySort();
  }

  sortIcon(key: SortKey): string {
    if (this.sortKey !== key) return '';
    return this.sortAsc ? '↑' : '↓';
  }

  private applySort(): void {
    if (!this._data) { this.sorted.set([]); return; }
    const key = this.sortKey;
    const dir = this.sortAsc ? 1 : -1;
    const arr = [...this._data.agents].sort((a, b) => {
      const av = (a as any)[key] ?? -Infinity;
      const bv = (b as any)[key] ?? -Infinity;
      return (av - bv) * dir;
    });
    this.sorted.set(arr);
  }

  fmt(n: number | null | undefined): string {
    if (n == null || n === 0) return '—';
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(1).replace('.', ',') + ' M';
    if (n >= 1_000) return Math.round(n / 1_000) + ' K';
    return n.toLocaleString('fr-FR');
  }
}
