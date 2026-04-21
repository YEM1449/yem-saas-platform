import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MonthlyTrendPoint } from './home-dashboard.service';

@Component({
  selector: 'app-mini-bar-chart',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mbc-wrap">
      @for (p of points; track p.yearMonth; let i = $index) {
        <div class="mbc-col">
          <div class="mbc-bar-wrap" [title]="formatAmount(p.caSigne)">
            <div class="mbc-bar"
                 [style.height.%]="barHeight(i)"
                 [class.mbc-bar-current]="i === points.length - 1"
                 [class.mbc-bar-zero]="p.caSigne === 0">
            </div>
          </div>
          <div class="mbc-label">{{ p.label }}</div>
          <div class="mbc-val">{{ formatShort(p.caSigne) }}</div>
        </div>
      }
    </div>
  `,
  styles: [`
    .mbc-wrap {
      display: flex; align-items: flex-end; gap: 8px;
      height: 120px; padding: 0 4px; box-sizing: border-box;
    }
    .mbc-col {
      flex: 1; display: flex; flex-direction: column;
      align-items: center; height: 100%;
    }
    .mbc-bar-wrap {
      flex: 1; width: 100%; display: flex;
      align-items: flex-end; cursor: default;
    }
    .mbc-bar {
      width: 100%; min-height: 4px; border-radius: 3px 3px 0 0;
      background: #bfdbfe; transition: height .3s ease;
    }
    .mbc-bar.mbc-bar-current { background: #2563eb; }
    .mbc-bar.mbc-bar-zero    { background: #e5e7eb; min-height: 4px; }
    .mbc-label {
      font-size: 10px; color: #94a3b8; margin-top: 4px;
      white-space: nowrap; text-align: center;
    }
    .mbc-val {
      font-size: 9px; color: #64748b; text-align: center;
      white-space: nowrap; max-width: 100%; overflow: hidden; text-overflow: ellipsis;
    }
  `],
})
export class MiniBarChartComponent {
  @Input() points: MonthlyTrendPoint[] = [];

  private get maxCA(): number {
    return Math.max(...this.points.map(p => p.caSigne), 1);
  }

  barHeight(i: number): number {
    const max = this.maxCA;
    if (max === 0) return 0;
    const pct = (this.points[i].caSigne / max) * 90; // max 90% of container
    return Math.max(pct, this.points[i].caSigne > 0 ? 5 : 0);
  }

  formatAmount(n: number): string {
    if (n === 0) return '—';
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(1).replace('.', ',') + ' M MAD';
    if (n >= 1_000)     return (n / 1_000).toFixed(0) + ' K MAD';
    return n.toLocaleString('fr-FR') + ' MAD';
  }

  formatShort(n: number): string {
    if (n === 0) return '—';
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(1).replace('.', ',') + 'M';
    if (n >= 1_000)     return (n / 1_000).toFixed(0) + 'K';
    return n.toLocaleString('fr-FR');
  }
}
