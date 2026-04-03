import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { VenteService, Vente, VenteStatut } from './vente.service';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-vente-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe, DecimalPipe, TranslateModule],
  template: `
    <div class="page-container">
      <div class="page-header">
        <h1 class="page-title">Ventes</h1>
        <button *ngIf="canWrite" class="btn btn-primary" (click)="showCreate = true"
                data-testid="create-vente">
          + Nouvelle vente
        </button>
      </div>

      <div class="filter-bar">
        <select [(ngModel)]="filterStatut" class="form-control" style="width:200px">
          <option value="">Tous les statuts</option>
          <option *ngFor="let s of statuts" [value]="s">{{ statutLabel(s) }}</option>
        </select>
      </div>

      <div *ngIf="loading()" class="loading">Chargement…</div>
      <div *ngIf="error()" class="alert alert-error">{{ error() }}</div>

      <div *ngIf="!loading()" class="table-container">
        <table class="data-table" *ngIf="filtered.length; else empty">
          <thead>
            <tr>
              <th>Bien</th>
              <th>Contact</th>
              <th>Statut</th>
              <th>Prix de vente</th>
              <th>Date compromis</th>
              <th>Créé le</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let v of filtered">
              <td>{{ v.propertyId }}</td>
              <td>{{ v.contactId }}</td>
              <td><span class="badge" [class]="statutClass(v.statut)">{{ statutLabel(v.statut) }}</span></td>
              <td>{{ v.prixVente | number:'1.0-0' }} MAD</td>
              <td>{{ v.dateCompromis | date:'dd/MM/yyyy' }}</td>
              <td>{{ v.createdAt | date:'dd/MM/yyyy' }}</td>
              <td><a [routerLink]="['/app/ventes', v.id]" class="btn btn-sm btn-outline">Détail</a></td>
            </tr>
          </tbody>
        </table>
        <ng-template #empty>
          <div class="empty-state">Aucune vente enregistrée.</div>
        </ng-template>
      </div>
    </div>
  `,
})
export class VenteListComponent implements OnInit {
  private svc  = inject(VenteService);
  private auth = inject(AuthService);

  ventes  = signal<Vente[]>([]);
  loading = signal(true);
  error   = signal('');
  showCreate = false;
  filterStatut = '';

  statuts: VenteStatut[] = ['COMPROMIS', 'FINANCEMENT', 'ACTE_NOTARIE', 'LIVRE', 'ANNULE'];

  get canWrite(): boolean {
    const r = this.auth.user?.role;
    return r === 'ROLE_ADMIN' || r === 'ROLE_MANAGER';
  }

  get filtered(): Vente[] {
    if (!this.filterStatut) return this.ventes();
    return this.ventes().filter(v => v.statut === this.filterStatut);
  }

  ngOnInit(): void {
    this.svc.list().subscribe({
      next:  (data) => { this.ventes.set(data); this.loading.set(false); },
      error: ()     => { this.error.set('Erreur lors du chargement des ventes.'); this.loading.set(false); },
    });
  }

  statutLabel(s: VenteStatut): string {
    const labels: Record<VenteStatut, string> = {
      COMPROMIS:    'Compromis',
      FINANCEMENT:  'Financement',
      ACTE_NOTARIE: 'Acte notarié',
      LIVRE:        'Livré',
      ANNULE:       'Annulé',
    };
    return labels[s] ?? s;
  }

  statutClass(s: VenteStatut): string {
    const classes: Record<VenteStatut, string> = {
      COMPROMIS:    'badge-info',
      FINANCEMENT:  'badge-warning',
      ACTE_NOTARIE: 'badge-primary',
      LIVRE:        'badge-success',
      ANNULE:       'badge-error',
    };
    return classes[s] ?? '';
  }
}
