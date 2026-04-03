import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import {
  VenteService, Vente, VenteStatut, EcheanceStatut,
  CreateEcheanceRequest, UpdateVenteStatutRequest
} from './vente.service';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-vente-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe, DecimalPipe, TranslateModule],
  template: `
    <div class="page-container" *ngIf="vente() as v; else loading">

      <!-- Header -->
      <div class="page-header">
        <h1 class="page-title">Vente · <span class="badge" [class]="statutClass(v.statut)">{{ statutLabel(v.statut) }}</span></h1>
        <div class="actions" *ngIf="canWrite">
          <button class="btn btn-outline" (click)="toggleStatutForm()">Changer statut</button>
          <button class="btn btn-primary" (click)="inviteBuyer(v.id)" [disabled]="inviting()">
            {{ inviting() ? 'Envoi…' : '✉ Inviter acquéreur' }}
          </button>
        </div>
      </div>

      <div *ngIf="inviteMsg()" class="alert alert-success">{{ inviteMsg() }}</div>
      <div *ngIf="inviteError()" class="alert alert-error">{{ inviteError() }}</div>

      <!-- Statut transition form -->
      <div *ngIf="showStatutForm" class="card mb-4">
        <h3>Changer le statut</h3>
        <div class="form-row">
          <select [(ngModel)]="newStatut" class="form-control">
            <option *ngFor="let s of statuts" [value]="s">{{ statutLabel(s) }}</option>
          </select>
          <input type="date" [(ngModel)]="dateTransition" class="form-control" placeholder="Date">
          <button class="btn btn-primary" (click)="saveStatut(v.id)">Enregistrer</button>
          <button class="btn btn-outline" (click)="showStatutForm = false">Annuler</button>
        </div>
      </div>

      <!-- Info grid -->
      <div class="detail-grid">
        <div class="detail-card">
          <h3>Informations</h3>
          <dl>
            <dt>Prix de vente</dt><dd>{{ v.prixVente | number:'1.0-0' }} MAD</dd>
            <dt>Date compromis</dt><dd>{{ v.dateCompromis | date:'dd/MM/yyyy' }}</dd>
            <dt>Date acte notarié</dt><dd>{{ v.dateActeNotarie | date:'dd/MM/yyyy' }}</dd>
            <dt>Livraison prévue</dt><dd>{{ v.dateLivraisonPrevue | date:'dd/MM/yyyy' }}</dd>
            <dt>Livraison réelle</dt><dd>{{ v.dateLivraisonReelle | date:'dd/MM/yyyy' }}</dd>
            <dt>Notes</dt><dd>{{ v.notes }}</dd>
          </dl>
        </div>
      </div>

      <!-- Échéancier -->
      <div class="section">
        <div class="section-header">
          <h2>Échéancier</h2>
          <button *ngIf="canWrite" class="btn btn-sm btn-outline" (click)="showEcheanceForm = !showEcheanceForm">
            + Ajouter
          </button>
        </div>

        <div *ngIf="showEcheanceForm" class="card mb-3">
          <h4>Nouvelle échéance</h4>
          <div class="form-row">
            <input type="text" [(ngModel)]="ech.libelle" class="form-control" placeholder="Libellé" data-testid="ech-libelle">
            <input type="number" [(ngModel)]="ech.montant" class="form-control" placeholder="Montant" data-testid="ech-montant">
            <input type="date" [(ngModel)]="ech.dateEcheance" class="form-control" data-testid="ech-date">
            <button class="btn btn-primary" (click)="addEcheance(v.id)" data-testid="ech-submit">Enregistrer</button>
            <button class="btn btn-outline" (click)="showEcheanceForm = false">Annuler</button>
          </div>
          <div *ngIf="echError()" class="alert alert-error mt-2">{{ echError() }}</div>
        </div>

        <table class="data-table" *ngIf="v.echeances.length; else noEch">
          <thead>
            <tr><th>Libellé</th><th>Montant</th><th>Échéance</th><th>Statut</th><th>Paiement</th><th *ngIf="canWrite"></th></tr>
          </thead>
          <tbody>
            <tr *ngFor="let e of v.echeances">
              <td>{{ e.libelle }}</td>
              <td>{{ e.montant | number:'1.0-0' }} MAD</td>
              <td>{{ e.dateEcheance | date:'dd/MM/yyyy' }}</td>
              <td><span class="badge" [class]="echClass(e.statut)">{{ echLabel(e.statut) }}</span></td>
              <td>{{ e.datePaiement | date:'dd/MM/yyyy' }}</td>
              <td *ngIf="canWrite">
                <button *ngIf="e.statut !== 'PAYEE'" class="btn btn-sm btn-outline"
                        (click)="markPaid(v.id, e.id)">Marquer payée</button>
              </td>
            </tr>
          </tbody>
        </table>
        <ng-template #noEch><div class="empty-state">Aucune échéance.</div></ng-template>
      </div>

      <!-- Documents -->
      <div class="section" *ngIf="v.documents.length">
        <h2>Documents</h2>
        <ul class="doc-list">
          <li *ngFor="let d of v.documents">{{ d.nomFichier }} · {{ d.createdAt | date:'dd/MM/yyyy' }}</li>
        </ul>
      </div>
    </div>

    <ng-template #loading>
      <div *ngIf="error(); else spinner" class="alert alert-error">{{ error() }}</div>
      <ng-template #spinner><div class="loading">Chargement…</div></ng-template>
    </ng-template>
  `,
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

  showStatutForm  = false;
  showEcheanceForm = false;
  newStatut: VenteStatut = 'FINANCEMENT';
  dateTransition = '';
  ech: CreateEcheanceRequest = { libelle: '', montant: 0, dateEcheance: '' };

  statuts: VenteStatut[] = ['COMPROMIS', 'FINANCEMENT', 'ACTE_NOTARIE', 'LIVRE', 'ANNULE'];

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

  toggleStatutForm(): void {
    this.showStatutForm = !this.showStatutForm;
    if (this.vente()) this.newStatut = this.vente()!.statut;
  }

  saveStatut(id: string): void {
    const req: UpdateVenteStatutRequest = {
      statut: this.newStatut,
      dateTransition: this.dateTransition || null,
    };
    this.svc.updateStatut(id, req).subscribe({
      next: (v) => { this.vente.set(v); this.showStatutForm = false; },
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
    this.svc.updateEcheanceStatut(venteId, echeanceId, { statut: 'PAYEE', datePaiement: today }).subscribe({
      next: () => this.reload(venteId),
    });
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

  private reload(id: string): void {
    this.svc.get(id).subscribe({ next: (v) => this.vente.set(v) });
  }

  statutLabel(s: VenteStatut): string {
    return { COMPROMIS: 'Compromis', FINANCEMENT: 'Financement', ACTE_NOTARIE: 'Acte notarié', LIVRE: 'Livré', ANNULE: 'Annulé' }[s] ?? s;
  }

  statutClass(s: VenteStatut): string {
    return { COMPROMIS: 'badge-info', FINANCEMENT: 'badge-warning', ACTE_NOTARIE: 'badge-primary', LIVRE: 'badge-success', ANNULE: 'badge-error' }[s] ?? '';
  }

  echLabel(s: EcheanceStatut): string {
    return { EN_ATTENTE: 'En attente', PAYEE: 'Payée', EN_RETARD: 'En retard' }[s] ?? s;
  }

  echClass(s: EcheanceStatut): string {
    return { EN_ATTENTE: 'badge-info', PAYEE: 'badge-success', EN_RETARD: 'badge-error' }[s] ?? '';
  }
}
