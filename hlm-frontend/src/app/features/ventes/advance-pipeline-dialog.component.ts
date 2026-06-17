import { Component, Input, Output, EventEmitter, inject } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { VenteStatut, MotifAnnulation, UpdateVenteStatutRequest } from './vente.service';
import { I18nService } from '../../core/i18n/i18n.service';

/**
 * Canonical single-step forward transition per current statut, **for the generic advance flow only**
 * (ANNULE is handled separately). Stages with a dedicated guarded entry point are intentionally absent:
 * the backend rejects entering them via PATCH /statut (EX-001), so they are driven from the dedicated
 * VEFA action panels instead (see {@link guardedAction}):
 *   • PROSPECT / OPTION   → "Confirmer la réservation" (deposit ≤ 5%, cooling-off)
 *   • LIVRE_AVEC_RESERVES → "Lever les réserves"
 */
const NEXT_MAP: Partial<Record<VenteStatut, VenteStatut>> = {
  RESERVE:             'ACOMPTE',
  ACOMPTE:             'COMPROMIS',
  COMPROMIS:           'FINANCEMENT',
  FINANCEMENT:         'ACTE',
  ACTE:                'LIVRE_DEFINITIF',
  RESERVES_LEVEES:     'LIVRE_DEFINITIF',
};

/**
 * Statuts whose next step is a dedicated guarded action (the user is pointed at the right panel).
 * EN_RETRACTATION is here too: during the legal cooling-off window the sale cannot be advanced
 * generically (the backend blocks it, EX-011) — the buyer either withdraws via the dedicated panel
 * or the window closes automatically and the sale moves on. The forward "Avancer" step is hidden.
 * Labels are resolved from the i18n catalog ('ventes.advance.guarded.*').
 */
const GUARDED_STATUTS = new Set<VenteStatut>(['PROSPECT', 'OPTION', 'EN_RETRACTATION', 'LIVRE_AVEC_RESERVES']);

@Component({
  selector: 'app-advance-pipeline-dialog',
  standalone: true,
  imports: [FormsModule, TranslatePipe],
  templateUrl: './advance-pipeline-dialog.component.html',
  styleUrl: './advance-pipeline-dialog.component.css',
})
export class AdvancePipelineDialogComponent {
  private i18n = inject(I18nService);

  @Input() currentStatut: VenteStatut = 'COMPROMIS';
  @Input() saving = false;

  @Output() confirmed = new EventEmitter<UpdateVenteStatutRequest>();
  @Output() cancelled = new EventEmitter<void>();

  cancelMode       = false;
  dateTransition   = '';
  datePvReception  = '';
  notes            = '';
  motifAnnulation: MotifAnnulation | null = null;

  // Labels resolved in the template via 'ventes.motif.<value>'.
  readonly motifAnnulationOptions: MotifAnnulation[] =
    ['CREDIT_REFUSE', 'DESISTEMENT_ACHETEUR', 'CSP_NON_REALISEE', 'ACCORD_PARTIES', 'LITIGE', 'AUTRE'];

  get nextStatut(): VenteStatut | null {
    return NEXT_MAP[this.currentStatut] ?? null;
  }

  /**
   * Label of the dedicated guarded action when the current statut has no generic forward step
   * (e.g. PROSPECT/OPTION → "Confirmer la réservation"). The generic advance dialog must NOT drive
   * these transitions — the backend rejects them (EX-001) — so we point the user at the right panel.
   */
  get guardedAction(): string | null {
    return GUARDED_STATUTS.has(this.currentStatut)
      ? this.i18n.instant('ventes.advance.guarded.' + this.currentStatut)
      : null;
  }

  get isTerminal(): boolean {
    return this.currentStatut === 'LIVRE_DEFINITIF' || this.currentStatut === 'ANNULE';
  }

  get nextLabel(): string {
    return this.nextStatut ? this.i18n.instant('ventes.statut.' + this.nextStatut) : '';
  }

  get currentLabel(): string {
    return this.i18n.instant('ventes.statut.' + this.currentStatut);
  }

  /** True when the target statut (next or ANNULE) needs a date. */
  get targetNeedsDate(): boolean {
    const target = this.cancelMode ? 'ANNULE' : this.nextStatut;
    return target === 'ACTE' || target === 'LIVRE_DEFINITIF';
  }

  /** Stages with bespoke hint copy in the catalog ('ventes.advance.hintTitle/hintBody.*'). */
  private static readonly HINT_STAGES = new Set<VenteStatut>(['FINANCEMENT', 'ACTE', 'LIVRE_DEFINITIF']);

  get hintTitle(): string {
    if (this.cancelMode) return this.i18n.instant('ventes.advance.hintTitle.cancel');
    return this.nextStatut && AdvancePipelineDialogComponent.HINT_STAGES.has(this.nextStatut)
      ? this.i18n.instant('ventes.advance.hintTitle.' + this.nextStatut) : '';
  }

  get hintBody(): string {
    if (this.cancelMode) return this.i18n.instant('ventes.advance.hintBody.cancel');
    return this.nextStatut && AdvancePipelineDialogComponent.HINT_STAGES.has(this.nextStatut)
      ? this.i18n.instant('ventes.advance.hintBody.' + this.nextStatut) : '';
  }

  get targetDateLabel(): string {
    const target = this.cancelMode ? 'ANNULE' : this.nextStatut;
    if (target === 'ACTE')            return this.i18n.instant('ventes.advance.dateLabel.ACTE');
    if (target === 'LIVRE_DEFINITIF') return this.i18n.instant('ventes.advance.dateLabel.LIVRE_DEFINITIF');
    return this.i18n.instant('ventes.advance.dateLabel.default');
  }

  get confirmDisabled(): boolean {
    if (this.saving) return true;
    // No generic forward step available (guarded action) and not cancelling → nothing to confirm.
    if (!this.cancelMode && !this.nextStatut) return true;
    if (this.targetNeedsDate && !this.dateTransition) return true;
    if (this.cancelMode && !this.motifAnnulation) return true;
    return false;
  }

  confirm(): void {
    const targetStatut: VenteStatut = this.cancelMode ? 'ANNULE' : (this.nextStatut!);
    this.confirmed.emit({
      statut:           targetStatut,
      motifAnnulation:  this.cancelMode ? this.motifAnnulation : null,
      dateTransition:   this.dateTransition || null,
      datePvReception:  targetStatut === 'LIVRE_DEFINITIF' && this.datePvReception
                          ? this.datePvReception : null,
      notes:            this.notes.trim() || null,
    });
  }

  cancel(): void {
    this.cancelled.emit();
  }
}
