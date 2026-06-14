import { Component, Input, Output, EventEmitter } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { VenteStatut, MotifAnnulation, UpdateVenteStatutRequest } from './vente.service';

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
  EN_RETRACTATION:     'ACOMPTE',
  ACOMPTE:             'COMPROMIS',
  COMPROMIS:           'FINANCEMENT',
  FINANCEMENT:         'ACTE',
  ACTE:                'LIVRE_DEFINITIF',
  RESERVES_LEVEES:     'LIVRE_DEFINITIF',
};

/** For statuts whose next step is a dedicated guarded action, the label to point the user at. */
const GUARDED_ACTION: Partial<Record<VenteStatut, string>> = {
  PROSPECT:            'Confirmer la réservation',
  OPTION:              'Confirmer la réservation',
  LIVRE_AVEC_RESERVES: 'Lever les réserves',
};

const LABELS: Record<VenteStatut, string> = {
  PROSPECT:            'Prospect',
  OPTION:              'Option',
  RESERVE:             'Réservé',
  EN_RETRACTATION:     'Délai de rétractation',
  ACOMPTE:             'Acompte',
  COMPROMIS:           'Compromis',
  FINANCEMENT:         'Financement',
  ACTE:                'Acte notarié',
  LIVRE_AVEC_RESERVES: 'Livré (réserves)',
  RESERVES_LEVEES:     'Réserves levées',
  LIVRE_DEFINITIF:     'Livré',
  ANNULE:              'Annulé',
};

@Component({
  selector: 'app-advance-pipeline-dialog',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './advance-pipeline-dialog.component.html',
  styleUrl: './advance-pipeline-dialog.component.css',
})
export class AdvancePipelineDialogComponent {
  @Input() currentStatut: VenteStatut = 'COMPROMIS';
  @Input() saving = false;

  @Output() confirmed = new EventEmitter<UpdateVenteStatutRequest>();
  @Output() cancelled = new EventEmitter<void>();

  cancelMode       = false;
  dateTransition   = '';
  datePvReception  = '';
  notes            = '';
  motifAnnulation: MotifAnnulation | null = null;

  readonly motifAnnulationOptions: { value: MotifAnnulation; label: string }[] = [
    { value: 'CREDIT_REFUSE',    label: 'Crédit refusé' },
    { value: 'DESISTEMENT_ACHETEUR', label: 'Désistement acheteur' },
    { value: 'CSP_NON_REALISEE', label: 'Condition suspensive non réalisée' },
    { value: 'ACCORD_PARTIES',   label: 'Accord entre parties' },
    { value: 'LITIGE',           label: 'Litige' },
    { value: 'AUTRE',            label: 'Autre' }];

  get nextStatut(): VenteStatut | null {
    return NEXT_MAP[this.currentStatut] ?? null;
  }

  /**
   * Label of the dedicated guarded action when the current statut has no generic forward step
   * (e.g. PROSPECT/OPTION → "Confirmer la réservation"). The generic advance dialog must NOT drive
   * these transitions — the backend rejects them (EX-001) — so we point the user at the right panel.
   */
  get guardedAction(): string | null {
    return GUARDED_ACTION[this.currentStatut] ?? null;
  }

  get isTerminal(): boolean {
    return this.currentStatut === 'LIVRE_DEFINITIF' || this.currentStatut === 'ANNULE';
  }

  get nextLabel(): string {
    return this.nextStatut ? LABELS[this.nextStatut] : '';
  }

  get currentLabel(): string {
    return LABELS[this.currentStatut];
  }

  /** True when the target statut (next or ANNULE) needs a date. */
  get targetNeedsDate(): boolean {
    const target = this.cancelMode ? 'ANNULE' : this.nextStatut;
    return target === 'ACTE' || target === 'LIVRE_DEFINITIF';
  }

  get hintTitle(): string {
    if (this.cancelMode) return 'Annulation irréversible';
    const map: Partial<Record<VenteStatut, string>> = {
      FINANCEMENT:     'Passage en phase Financement',
      ACTE:            'Signature de l\'Acte Notarié',
      LIVRE_DEFINITIF: 'Livraison du bien',
    };
    return this.nextStatut ? (map[this.nextStatut] ?? '') : '';
  }

  get hintBody(): string {
    if (this.cancelMode) {
      return 'Cette action met fin définitivement à la vente. Le motif sera enregistré pour la traçabilité. L\'acquéreur n\'est pas notifié automatiquement.';
    }
    const map: Partial<Record<VenteStatut, string>> = {
      FINANCEMENT:     'Le dossier de financement de l\'acquéreur est en cours d\'instruction. Renseignez la date limite de la condition suspensive de crédit ci-dessous si applicable.',
      ACTE:            'La signature de l\'acte authentique devant notaire officialise le transfert de propriété. La date de signature est requise.',
      LIVRE_DEFINITIF: 'Le bien est remis à l\'acquéreur. Renseignez la date de livraison réelle. Vous pouvez également saisir la date du PV de réception si disponible.',
    };
    return this.nextStatut ? (map[this.nextStatut] ?? '') : '';
  }

  get targetDateLabel(): string {
    const target = this.cancelMode ? 'ANNULE' : this.nextStatut;
    if (target === 'ACTE')            return 'Date de signature de l\'acte';
    if (target === 'LIVRE_DEFINITIF') return 'Date de livraison réelle';
    return 'Date';
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
