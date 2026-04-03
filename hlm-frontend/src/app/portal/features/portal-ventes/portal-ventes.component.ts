import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { PortalVentesService } from '../../core/portal-ventes.service';
import { Vente, VenteStatut, EcheanceStatut } from '../../../features/ventes/vente.service';

@Component({
  selector: 'app-portal-ventes',
  standalone: true,
  imports: [CommonModule, DatePipe, DecimalPipe],
  template: `
    <div class="portal-section">
      <h2 class="portal-section-title">Mes Ventes</h2>

      <div *ngIf="loading()" class="portal-loading">Chargement…</div>
      <div *ngIf="error()" class="portal-error">{{ error() }}</div>

      <div *ngFor="let v of ventes()" class="portal-card">
        <!-- Pipeline stepper -->
        <div class="pipeline-stepper">
          <div *ngFor="let s of pipeline" class="step"
               [class.active]="v.statut === s"
               [class.done]="isStepDone(v.statut, s)">
            {{ statutLabel(s) }}
          </div>
        </div>

        <!-- Summary -->
        <div class="vente-summary">
          <div class="summary-row">
            <span>Prix de vente</span>
            <strong>{{ v.prixVente | number:'1.0-0' }} MAD</strong>
          </div>
          <div class="summary-row" *ngIf="v.dateLivraisonPrevue">
            <span>Livraison prévue</span>
            <strong>{{ v.dateLivraisonPrevue | date:'dd/MM/yyyy' }}</strong>
          </div>
        </div>

        <!-- Échéancier -->
        <div *ngIf="v.echeances.length" class="echeancier">
          <h4>Échéancier</h4>
          <table class="portal-table">
            <thead>
              <tr><th>Libellé</th><th>Montant</th><th>Échéance</th><th>Statut</th></tr>
            </thead>
            <tbody>
              <tr *ngFor="let e of v.echeances">
                <td>{{ e.libelle }}</td>
                <td>{{ e.montant | number:'1.0-0' }} MAD</td>
                <td>{{ e.dateEcheance | date:'dd/MM/yyyy' }}</td>
                <td><span class="badge" [class]="echClass(e.statut)">{{ echLabel(e.statut) }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Documents -->
        <div *ngIf="v.documents.length" class="doc-section">
          <h4>Documents</h4>
          <ul>
            <li *ngFor="let d of v.documents">{{ d.nomFichier }}</li>
          </ul>
        </div>
      </div>

      <div *ngIf="!loading() && !ventes().length" class="portal-empty">
        Aucune vente enregistrée pour votre dossier.
      </div>
    </div>
  `,
})
export class PortalVentesComponent implements OnInit {
  private svc = inject(PortalVentesService);

  ventes  = signal<Vente[]>([]);
  loading = signal(true);
  error   = signal('');

  pipeline: VenteStatut[] = ['COMPROMIS', 'FINANCEMENT', 'ACTE_NOTARIE', 'LIVRE'];

  ngOnInit(): void {
    this.svc.list().subscribe({
      next:  (data) => { this.ventes.set(data); this.loading.set(false); },
      error: ()     => { this.error.set('Erreur lors du chargement.'); this.loading.set(false); },
    });
  }

  isStepDone(current: VenteStatut, step: VenteStatut): boolean {
    const order: VenteStatut[] = ['COMPROMIS', 'FINANCEMENT', 'ACTE_NOTARIE', 'LIVRE'];
    return order.indexOf(current) > order.indexOf(step);
  }

  statutLabel(s: VenteStatut): string {
    return { COMPROMIS: 'Compromis', FINANCEMENT: 'Financement', ACTE_NOTARIE: 'Acte notarié', LIVRE: 'Livré', ANNULE: 'Annulé' }[s] ?? s;
  }

  echLabel(s: EcheanceStatut): string {
    return { EN_ATTENTE: 'En attente', PAYEE: 'Payée', EN_RETARD: 'En retard' }[s] ?? s;
  }

  echClass(s: EcheanceStatut): string {
    return { EN_ATTENTE: 'badge-info', PAYEE: 'badge-success', EN_RETARD: 'badge-error' }[s] ?? '';
  }
}
