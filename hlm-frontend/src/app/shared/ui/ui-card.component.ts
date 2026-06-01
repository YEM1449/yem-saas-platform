import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Generic surface container with an optional header (title + action slot) and body.
 * Wraps the canonical `.card` system so existing visuals are preserved while giving
 * a typed, slot-based component.
 *
 * Usage:
 *   <ui-card title="Coordonnées">
 *     <ui-button card-actions variant="tertiary" size="sm" (click)="edit()">Modifier</ui-button>
 *     …body…
 *   </ui-card>
 *
 *   <ui-card [flush]="true"><table>…</table></ui-card>
 */
@Component({
  selector: 'ui-card',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card">
      @if (title || hasHeaderSlot) {
        <div class="card-header">
          <h3>{{ title }}</h3>
          <div class="ui-card-actions"><ng-content select="[card-actions]"></ng-content></div>
        </div>
      }
      <div [class.card-body]="!flush" [class.card-body-flush]="flush">
        <ng-content></ng-content>
      </div>
    </section>
  `,
  styles: [`
    .card-header { display: flex; align-items: center; justify-content: space-between; }
    .ui-card-actions { display: flex; gap: var(--sp-2, 8px); }
    .ui-card-actions:empty { display: none; }
  `],
})
export class UiCardComponent {
  @Input() title = '';
  /** Render the body without padding (e.g. to host a full-bleed table). */
  @Input() flush = false;
  /** Force the header to render even with an empty title (when only actions are projected). */
  @Input() hasHeaderSlot = false;
}
