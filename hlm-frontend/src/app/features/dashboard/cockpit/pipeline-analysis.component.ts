import { Component, Input, inject} from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { I18nService } from '../../../core/i18n/i18n.service';

import { PipelineAnalysis, AtRiskDeal } from '../dashboard-cockpit.service';

@Component({
  selector: 'app-pipeline-analysis',
  standalone: true,
  imports: [TranslatePipe],
  template: `
    <div class="widget">
      <div class="widget-header">
        <span class="widget-title">{{ 'dashboard.cockpit.pipeline.title' | translate }}</span>
        <span class="widget-sub">{{ 'dashboard.cockpit.pipeline.sub' | translate }}</span>
      </div>

      @if (!data) {
        <div class="empty-state">{{ 'dashboard.cockpit.pipeline.loading' | translate }}</div>
      } @else {
        <!-- Summary row -->
        <div class="summary-row">
          <div class="summary-card">
            <div class="summary-label">{{ 'dashboard.cockpit.pipeline.weighted' | translate }}</div>
            <div class="summary-value accent">{{ fmt(data.totalWeightedValue) }}</div>
          </div>
          <div class="summary-card">
            <div class="summary-label">{{ 'dashboard.cockpit.pipeline.raw' | translate }}</div>
            <div class="summary-value">{{ fmt(data.totalRawValue) }}</div>
          </div>
        </div>

        <!-- Stage breakdown -->
        @if (data.stages.length > 0) {
          <table class="stage-table">
            <thead>
              <tr>
                <th>{{ 'dashboard.cockpit.pipeline.thStage' | translate }}</th>
                <th class="num">{{ 'dashboard.cockpit.pipeline.thDossiers' | translate }}</th>
                <th class="num">{{ 'dashboard.cockpit.pipeline.thCaBrut' | translate }}</th>
                <th class="num">{{ 'dashboard.cockpit.pipeline.thCaWeighted' | translate }}</th>
                <th class="num">{{ 'dashboard.cockpit.pipeline.thProb' | translate }}</th>
                <th class="num">{{ 'dashboard.cockpit.pipeline.thAge' | translate }}</th>
              </tr>
            </thead>
            <tbody>
              @for (s of data.stages; track s.statut) {
                <tr>
                  <td><span class="statut-badge" [attr.data-statut]="s.statut">{{ label(s.statut) }}</span></td>
                  <td class="num">{{ s.count }}</td>
                  <td class="num">{{ fmt(s.rawValue) }}</td>
                  <td class="num">{{ fmt(s.weightedValue) }}</td>
                  <td class="num">{{ s.defaultProbability }}%</td>
                  <td class="num" [class.aging-warn]="s.avgAgingDays > 30">{{ s.avgAgingDays }}j</td>
                </tr>
              }
            </tbody>
          </table>
        }

        <!-- At-risk deals are owned by the Sales cockpit worklist above this
             widget ("Affaires à relancer") — not duplicated here. This widget
             keeps the per-stage pipeline-health breakdown only. -->
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .widget { background:#fff; border:1px solid #e5e7eb; border-radius:12px; padding:18px; }
    .widget-header { display:flex; align-items:baseline; justify-content:space-between; margin-bottom:14px; }
    .widget-title { font-size:14px; font-weight:700; color:#111827; }
    .widget-sub { font-size:11px; color:#6b7280; text-transform:uppercase; letter-spacing:.04em; }
    .summary-row { display:flex; gap:12px; margin-bottom:16px; }
    .summary-card { flex:1; background:#f9fafb; border-radius:10px; padding:12px 14px; }
    .summary-label { font-size:11px; color:#6b7280; font-weight:600; text-transform:uppercase; margin-bottom:4px; }
    .summary-value { font-size:18px; font-weight:800; color:#111827; }
    .summary-value.accent { color:#6366f1; }
    .stage-table { width:100%; border-collapse:collapse; font-size:13px; }
    .stage-table th { color:#6b7280; font-weight:600; font-size:11px; text-transform:uppercase; padding:6px 8px; border-bottom:1px solid #e5e7eb; text-align:left; }
    .stage-table td { padding:8px; border-bottom:1px solid #f3f4f6; }
    .num { text-align:right; font-variant-numeric:tabular-nums; }
    .aging-warn { color:#b91c1c; font-weight:700; }
    .statut-badge { padding:2px 8px; border-radius:999px; font-size:11px; font-weight:600; background:#f3f4f6; color:#374151; }
    .statut-badge[data-statut="COMPROMIS"] { background:#eef2ff; color:#4f46e5; }
    .statut-badge[data-statut="FINANCEMENT"] { background:#fef3c7; color:#b45309; }
    .statut-badge[data-statut="ACTE"] { background:#dbeafe; color:#15803d; }
    .statut-badge.sm { font-size:10px; padding:1px 6px; }
    .risk-section { margin-top:16px; }
    .risk-head { display:flex; align-items:center; gap:8px; margin-bottom:8px; }
    .risk-title { font-size:13px; font-weight:700; color:#b91c1c; }
    .risk-count { background:#fef2f2; color:#b91c1c; font-size:11px; font-weight:700; padding:2px 8px; border-radius:999px; }
    .risk-list { display:flex; flex-direction:column; gap:4px; }
    .risk-row { display:flex; align-items:center; gap:8px; padding:8px 10px; border-radius:8px; background:#fafafa; font-size:12px; text-decoration:none; color:inherit; transition:background 120ms; }
    .risk-row:hover { background:#f3f4f6; }
    .risk-ref { font-weight:700; color:#6366f1; min-width:120px; }
    .risk-contact { flex:1; color:#374151; }
    .risk-aging { font-weight:700; color:#b91c1c; }
    .risk-amount { font-weight:600; color:#111827; min-width:80px; text-align:right; }
    .empty-state { padding:20px; text-align:center; color:#6b7280; }
  `],
})
export class PipelineAnalysisComponent {
  private i18n = inject(I18nService);
  @Input() data: PipelineAnalysis | null = null;

  label(s: string): string { return this.i18n.instant('ventes.statut.' + s); }

  fmt(n: number | null | undefined): string {
    if (n == null || n === 0) return '—';
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(1).replace('.', ',') + ' M';
    if (n >= 1_000) return Math.round(n / 1_000) + ' K';
    return n.toLocaleString('fr-FR');
  }
}
