import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Standard empty state with optional action slot. Standardises the dashed-border
 * empty pattern that is currently re-implemented per feature.
 *
 * Usage:
 *   <ui-empty-state icon="📭" title="Aucune vente"
 *                   message="Créez votre première vente pour démarrer le pipeline.">
 *     <ui-button variant="primary" (click)="create()">Nouvelle vente</ui-button>
 *   </ui-empty-state>
 */
@Component({
  selector: 'ui-empty-state',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="ui-empty">
      @if (icon) { <div class="ui-empty-icon" aria-hidden="true">{{ icon }}</div> }
      <h3 class="ui-empty-title">{{ title }}</h3>
      @if (message) { <p class="ui-empty-msg">{{ message }}</p> }
      <div class="ui-empty-actions"><ng-content></ng-content></div>
    </div>
  `,
  styles: [`
    .ui-empty {
      display: flex; flex-direction: column; align-items: center; text-align: center;
      gap: var(--sp-2, 8px); padding: var(--sp-12, 48px) var(--sp-6, 24px);
      border: 1px dashed var(--c-border-strong); border-radius: var(--r-md, 8px);
      background: var(--c-surface);
    }
    .ui-empty-icon { font-size: 2rem; opacity: .8; }
    .ui-empty-title { margin: 0; font-size: var(--text-lg, 1.125rem); font-weight: 600; color: var(--c-text); }
    .ui-empty-msg { margin: 0; max-width: 42ch; font-size: var(--text-sm, .875rem); color: var(--c-text-secondary); }
    .ui-empty-actions { display: flex; gap: var(--sp-2, 8px); margin-top: var(--sp-2, 8px); }
    .ui-empty-actions:empty { display: none; }
  `],
})
export class UiEmptyStateComponent {
  @Input() icon?: string;
  @Input() title = '';
  @Input() message?: string;
}
