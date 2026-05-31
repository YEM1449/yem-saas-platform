import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Status pill that maps a domain status onto the canonical status palette
 * (the same `--status-*` tokens used by `.badge-*` across the app), so every
 * status chip in the product renders identically regardless of feature.
 *
 * Covers the current property/vente lifecycle plus forward-looking construction
 * statuses (EN_TRAVAUX, ACHEVEE, LIVREE) so the same component serves both domains.
 *
 * Usage:
 *   <ui-status-pill status="ACTIVE" />
 *   <ui-status-pill [status]="vente.statut" />
 *   <ui-status-pill status="EN_TRAVAUX" label="En travaux" />
 */
@Component({
  selector: 'ui-status-pill',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="ui-pill" [style.background]="tone.bg" [style.color]="tone.fg">{{ label || (status | titlecase) }}</span>`,
  styles: [`
    .ui-pill {
      display: inline-flex; align-items: center;
      padding: 2px 10px; border-radius: 999px;
      font-size: var(--text-xs); font-weight: 600;
      line-height: 1.5; white-space: nowrap;
    }
  `],
})
export class UiStatusPillComponent {
  /** Domain status key (case-insensitive). */
  @Input() status = '';
  /** Optional display override; defaults to a title-cased status. */
  @Input() label?: string;

  private static readonly PALETTE: Record<string, { bg: string; fg: string }> = {
    // Sales / inventory lifecycle
    DRAFT:     { bg: '#f1f5f9', fg: '#475569' },
    BROUILLON: { bg: '#f1f5f9', fg: '#475569' },
    ACTIVE:    { bg: '#dcfce7', fg: '#15803d' },
    DISPONIBLE:{ bg: '#dcfce7', fg: '#15803d' },
    RESERVED:  { bg: '#ffedd5', fg: '#c2410c' },
    RESERVE:   { bg: '#ffedd5', fg: '#c2410c' },
    SOLD:      { bg: '#e2e8f0', fg: '#1e293b' },
    VENDU:     { bg: '#e2e8f0', fg: '#1e293b' },
    WITHDRAWN: { bg: '#f1f5f9', fg: '#94a3b8' },
    RETIRE:    { bg: '#f1f5f9', fg: '#94a3b8' },
    ARCHIVED:  { bg: '#f1f5f9', fg: '#94a3b8' },
    LIVRE:     { bg: '#dbeafe', fg: '#1d4ed8' },
    // Pipeline / vente
    COMPROMIS:    { bg: '#dcfce7', fg: '#15803d' },
    FINANCEMENT:  { bg: '#fef9c3', fg: '#a16207' },
    ACTE_NOTARIE: { bg: '#dbeafe', fg: '#1d4ed8' },
    ANNULE:       { bg: '#fee2e2', fg: '#b91c1c' },
    // Generic
    CONFIRMED: { bg: '#dcfce7', fg: '#15803d' },
    PENDING:   { bg: '#fef9c3', fg: '#a16207' },
    EXPIRED:   { bg: '#fee2e2', fg: '#b91c1c' },
    CANCELLED: { bg: '#fee2e2', fg: '#b91c1c' },
    // Forward-looking: tranche / construction
    EN_PREPARATION:       { bg: '#f1f5f9', fg: '#475569' },
    EN_COMMERCIALISATION: { bg: '#dcfce7', fg: '#15803d' },
    EN_TRAVAUX:           { bg: '#fef9c3', fg: '#a16207' },
    ACHEVEE:              { bg: '#dbeafe', fg: '#1d4ed8' },
    LIVREE:               { bg: '#e0e7ff', fg: '#4338ca' },
  };

  get tone(): { bg: string; fg: string } {
    return UiStatusPillComponent.PALETTE[(this.status || '').toUpperCase()]
      ?? { bg: '#f1f5f9', fg: '#475569' };
  }
}
