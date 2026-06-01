import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

export type UiButtonVariant = 'primary' | 'secondary' | 'tertiary' | 'danger';
export type UiButtonSize = 'md' | 'sm' | 'xs';

/**
 * Typed, accessible button wrapper over the canonical `.btn` CSS system.
 *
 * Reuses the existing, battle-tested global classes (`btn`, `btn-primary`, …) so it is
 * visually identical to current buttons from day one, while giving the codebase a single
 * typed component to evolve (loading state, icon slots, a11y) instead of ad-hoc `<button class="btn …">`.
 *
 * Native click events bubble normally — bind `(click)` on `<ui-button>` as usual.
 *
 * Usage:
 *   <ui-button variant="primary" (click)="save()">Enregistrer</ui-button>
 *   <ui-button variant="danger" size="sm" [loading]="deleting">Supprimer</ui-button>
 *   <ui-button variant="secondary" type="submit" [disabled]="form.invalid">Valider</ui-button>
 */
@Component({
  selector: 'ui-button',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      [type]="type"
      [class]="classes"
      [class.is-loading]="loading"
      [disabled]="disabled || loading"
      [attr.aria-busy]="loading ? 'true' : null">
      @if (loading) {
        <span class="ui-btn-spinner" aria-hidden="true"></span>
      }
      <span class="ui-btn-label"><ng-content></ng-content></span>
    </button>
  `,
  styles: [`
    :host { display: inline-flex; }
    button { width: 100%; }
    .ui-btn-spinner {
      width: 13px; height: 13px; border-radius: 50%;
      border: 2px solid currentColor; border-right-color: transparent;
      display: inline-block; animation: ui-btn-spin .6s linear infinite;
    }
    .is-loading .ui-btn-label { opacity: .85; }
    @keyframes ui-btn-spin { to { transform: rotate(360deg); } }
    @media (prefers-reduced-motion: reduce) { .ui-btn-spinner { animation: none; } }
  `],
})
export class UiButtonComponent {
  @Input() variant: UiButtonVariant = 'primary';
  @Input() size: UiButtonSize = 'md';
  @Input() type: 'button' | 'submit' | 'reset' = 'button';
  @Input() disabled = false;
  @Input() loading = false;

  /** Maps typed inputs onto the canonical global `.btn` classes. */
  get classes(): string {
    const variantClass = {
      primary: 'btn-primary',
      secondary: 'btn-secondary',
      tertiary: 'btn-ghost',
      danger: 'btn-danger',
    }[this.variant];
    const sizeClass = this.size === 'md' ? '' : ` btn-${this.size}`;
    return `btn ${variantClass}${sizeClass}`;
  }
}
