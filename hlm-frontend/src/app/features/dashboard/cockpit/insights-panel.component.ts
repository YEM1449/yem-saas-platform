import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { SmartInsight, InsightType } from '../dashboard-cockpit.service';

@Component({
  selector: 'app-insights-panel',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    @if (insights.length === 0) {
      <!-- nothing to show -->
    } @else {
      <div class="insights-panel">
        <div class="insights-header">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" class="insights-icon">
            <path d="M8 1.5a4.5 4.5 0 0 1 2.5 8.24V11a1 1 0 0 1-1 1H6.5a1 1 0 0 1-1-1V9.74A4.5 4.5 0 0 1 8 1.5Z"
                  stroke="currentColor" stroke-width="1.4" stroke-linejoin="round"/>
            <path d="M6.5 13h3M7 14.5h2" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
          </svg>
          <span class="insights-title">Insights</span>
          <span class="insights-count">{{ insights.length }}</span>
        </div>
        <div class="insights-list">
          @for (i of insights; track i.id) {
            <div class="insight-card" [class]="'insight-' + i.type.toLowerCase()">
              <div class="insight-badge">{{ typeIcon(i.type) }}</div>
              <div class="insight-body">
                <div class="insight-title">{{ i.title }}</div>
                <div class="insight-desc">{{ i.description }}</div>
              </div>
              @if (i.actionLabel && i.actionRoute) {
                <a [routerLink]="i.actionRoute" class="insight-action">{{ i.actionLabel }} →</a>
              }
            </div>
          }
        </div>
      </div>
    }
  `,
  styles: [`
    :host { display: block; }
    .insights-panel { background:#fff; border:1px solid #e5e7eb; border-radius:12px; padding:16px; }
    .insights-header { display:flex; align-items:center; gap:8px; margin-bottom:12px; }
    .insights-icon { color:#6366f1; }
    .insights-title { font-size:14px; font-weight:700; color:#111827; }
    .insights-count { font-size:11px; font-weight:600; color:#6366f1; background:#eef2ff; border-radius:999px; padding:1px 8px; }
    .insights-list { display:flex; flex-direction:column; gap:8px; }
    .insight-card { display:flex; align-items:flex-start; gap:10px; padding:10px 12px; border-radius:8px; border-left:3px solid transparent; }
    .insight-risk { background:#fef2f2; border-left-color:#ef4444; }
    .insight-opportunity { background:#ecfdf5; border-left-color:#10b981; }
    .insight-trend { background:#eff6ff; border-left-color:#3b82f6; }
    .insight-info { background:#f9fafb; border-left-color:#9ca3af; }
    .insight-badge { font-size:16px; flex-shrink:0; margin-top:1px; }
    .insight-body { flex:1; min-width:0; }
    .insight-title { font-size:13px; font-weight:600; color:#111827; }
    .insight-desc { font-size:12px; color:#6b7280; margin-top:2px; line-height:1.4; }
    .insight-action { font-size:12px; font-weight:600; color:#4f46e5; text-decoration:none; white-space:nowrap; flex-shrink:0; align-self:center; }
    .insight-action:hover { text-decoration:underline; }
  `],
})
export class InsightsPanelComponent {
  @Input() insights: SmartInsight[] = [];

  typeIcon(type: InsightType): string {
    switch (type) {
      case 'RISK':        return '\u26A0\uFE0F';
      case 'OPPORTUNITY': return '\u2728';
      case 'TREND':       return '\uD83D\uDCC8';
      case 'INFO':        return '\u2139\uFE0F';
      default:            return '\u2022';
    }
  }
}
