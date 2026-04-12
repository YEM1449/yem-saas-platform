import { Component, Input, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SparklinePoint } from '../dashboard-cockpit.service';

/**
 * Inline SVG sparkline for KPI cards. Pure stateless presentation:
 * normalises the input series into the configured viewport and draws a
 * single polyline + an end-of-series dot.
 */
@Component({
  selector: 'app-sparkline',
  standalone: true,
  imports: [CommonModule],
  template: `
    <svg class="sparkline" [attr.viewBox]="viewBox" preserveAspectRatio="none"
         [attr.aria-label]="ariaLabel">
      @if (points.length > 1) {
        <polyline [attr.points]="polyline" [attr.stroke]="color" />
        @if (lastPoint) {
          <circle [attr.cx]="lastPoint.x" [attr.cy]="lastPoint.y" r="1.6" [attr.fill]="color" />
        }
      } @else {
        <line x1="0" [attr.y1]="height/2" [attr.x2]="width" [attr.y2]="height/2"
              stroke="#e5e7eb" stroke-dasharray="2 2"/>
      }
    </svg>
  `,
  styles: [`
    .sparkline {
      display: block;
      width: 100%;
      height: 36px;
      stroke-width: 1.6;
      fill: none;
      stroke-linejoin: round;
      stroke-linecap: round;
    }
  `],
})
export class SparklineComponent {
  @Input() data: SparklinePoint[] = [];
  @Input() color = '#6366f1';
  @Input() ariaLabel = 'Tendance sur 12 semaines';

  readonly width = 120;
  readonly height = 36;

  get viewBox(): string {
    return `0 0 ${this.width} ${this.height}`;
  }

  get points(): { x: number; y: number }[] {
    if (!this.data || this.data.length === 0) return [];
    const values = this.data.map(p => p.value ?? 0);
    const max = Math.max(...values);
    const min = Math.min(...values);
    const range = max - min || 1;
    const stepX = this.data.length > 1 ? this.width / (this.data.length - 1) : 0;
    const padTop = 4;
    const usable = this.height - padTop * 2;
    return this.data.map((p, i) => ({
      x: i * stepX,
      y: padTop + (1 - ((p.value ?? 0) - min) / range) * usable,
    }));
  }

  get polyline(): string {
    return this.points.map(p => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ');
  }

  get lastPoint(): { x: number; y: number } | null {
    const arr = this.points;
    return arr.length ? arr[arr.length - 1] : null;
  }
}
