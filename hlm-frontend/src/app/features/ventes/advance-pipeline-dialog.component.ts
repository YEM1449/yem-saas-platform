import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { VenteStatut, UpdateVenteStatutRequest } from './vente.service';

/** Allowed forward transitions per current statut (excludes ANNULE which is separate). */
const NEXT_MAP: Partial<Record<VenteStatut, VenteStatut>> = {
  COMPROMIS:    'FINANCEMENT',
  FINANCEMENT:  'ACTE_NOTARIE',
  ACTE_NOTARIE: 'LIVRE',
};

const LABELS: Record<VenteStatut, string> = {
  COMPROMIS:    'Compromis',
  FINANCEMENT:  'Financement',
  ACTE_NOTARIE: 'Acte notarié',
  LIVRE:        'Livré',
  ANNULE:       'Annulé',
};

@Component({
  selector: 'app-advance-pipeline-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './advance-pipeline-dialog.component.html',
  styleUrl: './advance-pipeline-dialog.component.css',
})
export class AdvancePipelineDialogComponent {
  @Input() currentStatut: VenteStatut = 'COMPROMIS';
  @Input() saving = false;

  @Output() confirmed = new EventEmitter<UpdateVenteStatutRequest>();
  @Output() cancelled = new EventEmitter<void>();

  cancelMode = false;
  dateTransition = '';
  notes = '';

  get nextStatut(): VenteStatut | null {
    return NEXT_MAP[this.currentStatut] ?? null;
  }

  get isTerminal(): boolean {
    return this.currentStatut === 'LIVRE' || this.currentStatut === 'ANNULE';
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
    return target === 'ACTE_NOTARIE' || target === 'LIVRE';
  }

  get targetDateLabel(): string {
    const target = this.cancelMode ? 'ANNULE' : this.nextStatut;
    if (target === 'ACTE_NOTARIE') return 'Date de signature de l\'acte';
    if (target === 'LIVRE')        return 'Date de livraison réelle';
    return 'Date';
  }

  confirm(): void {
    const targetStatut: VenteStatut = this.cancelMode ? 'ANNULE' : (this.nextStatut!);
    this.confirmed.emit({
      statut: targetStatut,
      dateTransition: this.dateTransition || null,
      notes: this.notes.trim() || null,
    });
  }

  cancel(): void {
    this.cancelled.emit();
  }
}
