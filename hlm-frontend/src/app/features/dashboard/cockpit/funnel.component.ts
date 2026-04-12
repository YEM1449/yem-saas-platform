import { Component, Input } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FunnelSnapshot, FunnelStage } from '../dashboard-cockpit.service';

/**
 * Sales funnel widget — counts at each stage with conversion / drop-off
 * percentages computed against the upstream stage on the backend.
 *
 * <p>Visual: stacked horizontal bars whose width is proportional to the
 * stage count vs the funnel head, plus an inline drop-off badge.
 */
@Component({
  selector: 'app-funnel',
  standalone: true,
  imports: [CommonModule, DecimalPipe],
  template: `
    <div class="widget">
      <div class="widget-header">
        <span class="widget-title">Entonnoir commercial complet</span>
        <span class="widget-sub">Conversion entre étapes</span>
      </div>

      @if (!data || data.stages.length === 0) {
        <div class="empty-state">
          <strong>Pas encore de données</strong>
          <span>L'entonnoir s'affichera dès le premier prospect enregistré.</span>
        </div>
      } @else {
        <div class="funnel">
          @for (stage of data.stages; track stage.stage; let i = $index) {
            <div class="funnel-step">
              <div class="funnel-step-head">
                <span class="funnel-step-label">{{ stage.label }}</span>
                <span class="funnel-step-count">{{ stage.count | number:'1.0-0' }}</span>
              </div>
              <div class="funnel-bar-track">
                <div class="funnel-bar-fill"
                     [style.width.%]="barWidth(stage)"
                     [style.background]="stageColor(i)"></div>
              </div>
              @if (i > 0) {
                <div class="funnel-step-meta">
                  @if (stage.conversionRate != null) {
                    <span class="funnel-conv"
                          [class.funnel-conv-good]="stage.conversionRate >= 50"
                          [class.funnel-conv-warn]="stage.conversionRate < 25">
                      {{ stage.conversionRate }}% convertis
                    </span>
                    <span class="funnel-drop">−{{ stage.dropOffRate }}% perdus</span>
                  } @else {
                    <span class="funnel-empty">Pas de référence amont</span>
                  }
                </div>
              }
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .widget {
      background: #fff;
      border: 1px solid #e5e7eb;
      border-radius: 12px;
      padding: 18px 18px 14px;
    }
    .widget-header {
      display: flex; align-items: baseline; justify-content: space-between;
      margin-bottom: 14px;
    }
    .widget-title {
      font-size: 14px; font-weight: 700; color: #111827;
    }
    .widget-sub {
      font-size: 11px; color: #6b7280; text-transform: uppercase; letter-spacing: 0.04em;
    }
    .funnel { display: flex; flex-direction: column; gap: 12px; }
    .funnel-step { display: flex; flex-direction: column; gap: 4px; }
    .funnel-step-head {
      display: flex; align-items: baseline; justify-content: space-between;
      font-size: 13px;
    }
    .funnel-step-label { color: #374151; font-weight: 600; }
    .funnel-step-count { color: #111827; font-weight: 700; font-variant-numeric: tabular-nums; }
    .funnel-bar-track {
      height: 10px;
      background: #f3f4f6;
      border-radius: 999px;
      overflow: hidden;
    }
    .funnel-bar-fill {
      height: 100%;
      border-radius: 999px;
      transition: width 280ms ease;
      min-width: 4px;
    }
    .funnel-step-meta {
      display: flex; gap: 10px; font-size: 11px;
      padding-left: 2px;
    }
    .funnel-conv {
      color: #4b5563; font-weight: 600;
      padding: 1px 7px; border-radius: 999px; background: #f3f4f6;
    }
    .funnel-conv-good { color: #047857; background: #ecfdf5; }
    .funnel-conv-warn { color: #b91c1c; background: #fef2f2; }
    .funnel-drop { color: #9ca3af; }
    .funnel-empty { color: #9ca3af; font-style: italic; }
    .empty-state {
      padding: 28px 12px;
      text-align: center;
      border: 1.5px dashed #e5e7eb;
      border-radius: 10px;
      display: flex; flex-direction: column; gap: 4px;
      color: #6b7280;
    }
    .empty-state strong { color: #374151; font-size: 13px; }
  `],
})
export class FunnelComponent {
  @Input() data: FunnelSnapshot | null = null;

  private readonly palette = ['#6366f1', '#8b5cf6', '#f59e0b', '#3b82f6', '#10b981'];

  stageColor(index: number): string {
    return this.palette[index % this.palette.length];
  }

  barWidth(stage: FunnelStage): number {
    if (!this.data || this.data.stages.length === 0) return 0;
    const head = this.data.stages[0].count;
    if (head <= 0) return 0;
    return Math.min(100, (stage.count / head) * 100);
  }
}
