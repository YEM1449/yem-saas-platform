import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { DashboardAlert } from '../dashboard-cockpit.service';

/**
 * Stacked alerts panel surfaced at the top of the executive cockpit.
 *
 * <p>Sorts incoming alerts by severity (CRITICAL → WARNING → INFO) and
 * collapses to an "all clear" tile when the list is empty.
 */
@Component({
  selector: 'app-alerts-panel',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="alerts-panel">
      <div class="alerts-head">
        <span class="alerts-title">Signaux opérationnels</span>
        <span class="alerts-count" [class.alerts-count-zero]="sorted.length === 0">
          {{ sorted.length }}
        </span>
      </div>

      @if (sorted.length === 0) {
        <div class="alerts-clear" data-testid="alerts-all-clear">
          <svg width="18" height="18" viewBox="0 0 18 18" fill="none">
            <circle cx="9" cy="9" r="7" stroke="#10b981" stroke-width="1.6"/>
            <path d="M6 9.5l2 2 4-4" stroke="#10b981" stroke-width="1.6"
                  stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          <span>Aucun signal critique. Les indicateurs sont dans le vert.</span>
        </div>
      } @else {
        <ul class="alerts-list">
          @for (a of sorted; track a.id) {
            <li class="alert-item" [attr.data-severity]="a.severity">
              <div class="alert-bar"></div>
              <div class="alert-body">
                <div class="alert-head-row">
                  <span class="alert-cat">{{ a.category }}</span>
                  <span class="alert-sev">{{ severityLabel(a.severity) }}</span>
                </div>
                <div class="alert-title">{{ a.title }}</div>
                <div class="alert-msg">{{ a.message }}</div>
              </div>
              @if (a.ctaRoute) {
                <a class="alert-cta" [routerLink]="a.ctaRoute">
                  {{ a.ctaLabel || 'Ouvrir' }} →
                </a>
              }
            </li>
          }
        </ul>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .alerts-panel {
      background: #fff;
      border: 1px solid #e5e7eb;
      border-radius: 12px;
      padding: 16px 18px 14px;
    }
    .alerts-head {
      display: flex; align-items: center; gap: 10px;
      margin-bottom: 12px;
    }
    .alerts-title {
      font-size: 13px; font-weight: 700; color: #111827;
      text-transform: uppercase; letter-spacing: 0.03em;
    }
    .alerts-count {
      display: inline-flex; align-items: center; justify-content: center;
      min-width: 22px; height: 22px; padding: 0 7px;
      border-radius: 999px;
      background: #fef2f2; color: #b91c1c;
      font-size: 12px; font-weight: 700;
    }
    .alerts-count-zero { background: #ecfdf5; color: #047857; }
    .alerts-clear {
      display: flex; align-items: center; gap: 10px;
      padding: 10px 12px; border-radius: 8px;
      background: #f0fdf4; color: #047857; font-size: 13px;
    }
    .alerts-list {
      list-style: none; padding: 0; margin: 0;
      display: flex; flex-direction: column; gap: 8px;
    }
    .alert-item {
      display: flex; align-items: stretch; gap: 12px;
      background: #fafafa;
      border: 1px solid #f3f4f6;
      border-radius: 10px;
      padding: 12px 14px;
      transition: background 120ms ease;
    }
    .alert-item:hover { background: #f3f4f6; }
    .alert-bar {
      width: 3px; border-radius: 2px;
      background: #9ca3af;
      flex-shrink: 0;
    }
    .alert-item[data-severity="WARNING"] .alert-bar { background: #f59e0b; }
    .alert-item[data-severity="CRITICAL"] .alert-bar { background: #ef4444; }
    .alert-item[data-severity="INFO"] .alert-bar    { background: #3b82f6; }
    .alert-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
    .alert-head-row {
      display: flex; gap: 8px; align-items: center;
      font-size: 10px; text-transform: uppercase; letter-spacing: 0.04em;
    }
    .alert-cat { color: #6b7280; font-weight: 600; }
    .alert-sev { color: #9ca3af; font-weight: 500; }
    .alert-item[data-severity="WARNING"]  .alert-sev { color: #b45309; }
    .alert-item[data-severity="CRITICAL"] .alert-sev { color: #b91c1c; }
    .alert-title {
      font-size: 13px; font-weight: 700; color: #111827;
    }
    .alert-msg {
      font-size: 12px; color: #4b5563; line-height: 1.4;
    }
    .alert-cta {
      align-self: center;
      font-size: 12px; font-weight: 600;
      color: #1d4ed8; text-decoration: none;
      white-space: nowrap;
      padding: 6px 10px; border-radius: 6px;
      transition: background 120ms ease;
    }
    .alert-cta:hover { background: #dbeafe; }
  `],
})
export class AlertsPanelComponent {
  @Input() set alerts(value: DashboardAlert[] | null | undefined) {
    this.sorted = (value ?? []).slice().sort(
      (a, b) => this.sevWeight(b.severity) - this.sevWeight(a.severity)
    );
  }

  sorted: DashboardAlert[] = [];

  severityLabel(s: string): string {
    return s === 'CRITICAL' ? 'Critique' : s === 'WARNING' ? 'Attention' : 'Info';
  }

  private sevWeight(s: string): number {
    return s === 'CRITICAL' ? 3 : s === 'WARNING' ? 2 : 1;
  }
}
