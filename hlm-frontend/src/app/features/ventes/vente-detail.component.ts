import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import {
  VenteService, Vente, VenteStatut, EcheanceStatut,
  CreateEcheanceRequest, UpdateVenteStatutRequest
} from './vente.service';
import { AuthService } from '../../core/auth/auth.service';
import { PipelineStepperComponent } from './pipeline-stepper.component';
import { AdvancePipelineDialogComponent } from './advance-pipeline-dialog.component';

@Component({
  selector: 'app-vente-detail',
  standalone: true,
  imports: [
    CommonModule, FormsModule, DatePipe, DecimalPipe, TranslateModule,
    RouterLink, PipelineStepperComponent, AdvancePipelineDialogComponent,
  ],
  templateUrl: './vente-detail.component.html',
  styleUrl: './vente-detail.component.css',
})
export class VenteDetailComponent implements OnInit {
  private svc   = inject(VenteService);
  private auth  = inject(AuthService);
  private route = inject(ActivatedRoute);

  vente    = signal<Vente | null>(null);
  error    = signal('');
  inviting = signal(false);
  inviteMsg   = signal('');
  inviteError = signal('');
  echError = signal('');

  showEcheanceForm  = false;
  showAdvanceDialog = false;
  savingStatut      = false;
  docUploading      = signal(false);
  docError          = signal('');

  ech: CreateEcheanceRequest = { libelle: '', montant: 0, dateEcheance: '' };

  get canWrite(): boolean {
    const r = this.auth.user?.role;
    return r === 'ROLE_ADMIN' || r === 'ROLE_MANAGER';
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.svc.get(id).subscribe({
      next:  (v) => this.vente.set(v),
      error: ()  => this.error.set('Vente introuvable.'),
    });
  }

  openAdvanceDialog(): void  { this.showAdvanceDialog = true; }
  closeAdvanceDialog(): void { this.showAdvanceDialog = false; }

  saveStatut(id: string, req: UpdateVenteStatutRequest): void {
    this.savingStatut = true;
    this.svc.updateStatut(id, req).subscribe({
      next: (v) => {
        this.vente.set(v);
        this.savingStatut = false;
        this.showAdvanceDialog = false;
      },
      error: () => { this.savingStatut = false; },
    });
  }

  addEcheance(venteId: string): void {
    this.echError.set('');
    if (!this.ech.libelle || !this.ech.montant || !this.ech.dateEcheance) {
      this.echError.set('Tous les champs sont requis.');
      return;
    }
    this.svc.addEcheance(venteId, this.ech).subscribe({
      next: () => {
        this.showEcheanceForm = false;
        this.ech = { libelle: '', montant: 0, dateEcheance: '' };
        this.reload(venteId);
      },
      error: () => this.echError.set('Erreur lors de l\'ajout.'),
    });
  }

  markPaid(venteId: string, echeanceId: string): void {
    const today = new Date().toISOString().slice(0, 10);
    this.svc.updateEcheanceStatut(venteId, echeanceId, {
      statut: 'PAYEE',
      datePaiement: today,
    }).subscribe({ next: () => this.reload(venteId) });
  }

  inviteBuyer(venteId: string): void {
    this.inviting.set(true);
    this.inviteMsg.set('');
    this.inviteError.set('');
    this.svc.inviteBuyer(venteId).subscribe({
      next:  () => { this.inviting.set(false); this.inviteMsg.set('Invitation envoyée avec succès.'); },
      error: () => { this.inviting.set(false); this.inviteError.set('Erreur lors de l\'envoi.'); },
    });
  }

  onDocFileSelected(event: Event, venteId: string): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.docUploading.set(true);
    this.docError.set('');
    this.svc.uploadDocument(venteId, file).subscribe({
      next: () => { this.docUploading.set(false); this.reload(venteId); },
      error: () => { this.docUploading.set(false); this.docError.set('Échec du téléversement.'); },
    });
    input.value = '';
  }

  isTerminal(s: VenteStatut): boolean {
    return s === 'LIVRE' || s === 'ANNULE';
  }

  private reload(id: string): void {
    this.svc.get(id).subscribe({ next: (v) => this.vente.set(v) });
  }

  statutLabel(s: VenteStatut): string {
    const labels: Record<VenteStatut, string> = {
      COMPROMIS: 'Compromis', FINANCEMENT: 'Financement',
      ACTE_NOTARIE: 'Acte notarié', LIVRE: 'Livré', ANNULE: 'Annulé',
    };
    return labels[s] ?? s;
  }

  statutClass(s: VenteStatut): string {
    const classes: Record<VenteStatut, string> = {
      COMPROMIS: 'badge-info', FINANCEMENT: 'badge-warning',
      ACTE_NOTARIE: 'badge-primary', LIVRE: 'badge-success', ANNULE: 'badge-error',
    };
    return classes[s] ?? '';
  }

  echLabel(s: EcheanceStatut): string {
    return { EN_ATTENTE: 'En attente', PAYEE: 'Payée', EN_RETARD: 'En retard' }[s] ?? s;
  }

  echClass(s: EcheanceStatut): string {
    return { EN_ATTENTE: 'badge-info', PAYEE: 'badge-success', EN_RETARD: 'badge-error' }[s] ?? '';
  }
}
