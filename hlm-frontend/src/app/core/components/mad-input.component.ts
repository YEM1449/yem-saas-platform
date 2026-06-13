import { Component, Input, forwardRef, ChangeDetectionStrategy } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

/**
 * Formatted MAD currency input (C-005).
 *
 * Accepts a numeric model value via ngModel / reactive forms.
 * While focused, the user types raw digits. On blur the value is rendered
 * as "1 250 000 MAD" using fr-MA locale formatting.
 *
 * Usage:
 *   <app-mad-input [(ngModel)]="prixVente" name="prixVente" inputClass="form-control" />
 */
@Component({
  selector: 'app-mad-input',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <input
      type="text"
      inputmode="numeric"
      [value]="displayValue"
      [placeholder]="placeholder"
      [disabled]="isDisabled"
      [class]="inputClass"
      [attr.data-testid]="testId"
      (focus)="onFocus($event)"
      (input)="onInput($event)"
      (blur)="onBlur()"
    />
  `,
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => MadInputComponent),
    multi: true,
  }],
})
export class MadInputComponent implements ControlValueAccessor {
  @Input() placeholder = '0 MAD';
  @Input() inputClass  = 'form-control';
  @Input() testId: string | null = null;

  displayValue = '';
  isDisabled   = false;

  private rawValue: number | null = null;
  private onChange  = (_: number | null) => {};
  private onTouched = () => {};

  onFocus(e: FocusEvent): void {
    // Show raw number while the user is typing
    (e.target as HTMLInputElement).value = this.rawValue == null ? '' : String(this.rawValue);
  }

  onInput(e: Event): void {
    const stripped = (e.target as HTMLInputElement).value.replace(/[^\d]/g, '');
    this.rawValue  = stripped === '' ? null : parseInt(stripped, 10);
    this.onChange(this.rawValue);
  }

  onBlur(): void {
    this.displayValue = this.format(this.rawValue);
    this.onTouched();
  }

  // ── ControlValueAccessor ────────────────────────────────────────────────────

  writeValue(v: number | null): void {
    this.rawValue    = v ?? null;
    this.displayValue = this.format(this.rawValue);
  }

  registerOnChange(fn: (_: number | null) => void): void { this.onChange = fn; }
  registerOnTouched(fn: () => void): void               { this.onTouched = fn; }
  setDisabledState(d: boolean): void                    { this.isDisabled = d; }

  private format(v: number | null): string {
    if (v == null) return '';
    return new Intl.NumberFormat('fr-MA', { maximumFractionDigits: 0 }).format(v) + ' MAD';
  }
}
