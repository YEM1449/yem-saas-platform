import { Component, HostBinding, Input } from '@angular/core';

/**
 * Shared card container applying the design-system token styles:
 * radius-lg, shadow-card, surface background.
 *
 * Usage:
 *   <hlm-card [clickable]="true" [selected]="isSelected" (click)="doSomething()">
 *     <div slot="header">…</div>
 *     <div slot="body">…</div>       <!-- optional; bare <ng-content> also projected -->
 *     <div slot="footer">…</div>    <!-- optional -->
 *   </hlm-card>
 */
@Component({
  selector: 'hlm-card',
  standalone: true,
  template: `
    <ng-content select="[slot=header]"></ng-content>
    <div class="hlm-card-body">
      <ng-content select="[slot=body]"></ng-content>
      <ng-content></ng-content>
    </div>
    <ng-content select="[slot=footer]"></ng-content>
  `,
  styles: [`
    :host {
      display: flex;
      flex-direction: column;
      background: var(--c-surface, #fff);
      border-radius: var(--radius-lg, 16px);
      box-shadow: var(--shadow-card, 0 1px 3px rgba(0,0,0,.08), 0 1px 2px rgba(0,0,0,.06));
      overflow: hidden;
      position: relative;
      transition: box-shadow 220ms ease, transform 220ms ease;
    }
    :host(.clickable) {
      cursor: pointer;
    }
    :host(.clickable):hover {
      box-shadow: var(--sh-md, 0 4px 6px -1px rgba(0,0,0,.07), 0 2px 4px -1px rgba(0,0,0,.04));
      transform: translateY(-2px);
    }
    :host(.selected) {
      outline: 2px solid var(--color-primary, #1B5E20);
      outline-offset: -2px;
    }
    :host(:focus-visible) {
      outline: 2px solid var(--color-accent, #F57F17);
      outline-offset: 2px;
    }
    .hlm-card-body {
      flex: 1;
    }
  `]
})
export class HlmCardComponent {
  @Input() @HostBinding('class.clickable') clickable = false;
  @Input() @HostBinding('class.selected')  selected  = false;
}
