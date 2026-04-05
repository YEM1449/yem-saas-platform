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
  templateUrl: './vente-list.component.html',
  styleUrl: './vente-list.component.css',
})
export class VenteListComponent implements OnInit {
  private svc  = inject(VenteService);
  private auth = inject(AuthService);

  ventes  = signal<Vente[]>([]);
  loading = signal(true);
  error   = signal('');
  showCreate = false;
  filterStatut = '';

  readonly statuts: VenteStatut[] = ['COMPROMIS', 'FINANCEMENT', 'ACTE_NOTARIE', 'LIVRE', 'ANNULE'];

  get canWrite(): boolean {
    const r = this.auth.user?.role;
    return r === 'ROLE_ADMIN' || r === 'ROLE_MANAGER';
  }

  get filtered(): Vente[] {
    if (!this.filterStatut) return this.ventes();
    return this.ventes().filter(v => v.statut === this.filterStatut as VenteStatut);
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
