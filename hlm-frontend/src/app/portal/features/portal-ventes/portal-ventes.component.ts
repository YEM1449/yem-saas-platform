import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { PortalVentesService } from '../../core/portal-ventes.service';
import { Vente, EcheanceStatut } from '../../../features/ventes/vente.service';
import { PipelineStepperComponent } from '../../../features/ventes/pipeline-stepper.component';

@Component({
  selector: 'app-portal-ventes',
  standalone: true,
  imports: [CommonModule, DatePipe, DecimalPipe, PipelineStepperComponent],
  templateUrl: './portal-ventes.component.html',
  styleUrl: './portal-ventes.component.css',
})
export class PortalVentesComponent implements OnInit {
  private svc = inject(PortalVentesService);

  ventes  = signal<Vente[]>([]);
  loading = signal(true);
  error   = signal('');

  ngOnInit(): void {
    this.svc.list().subscribe({
      next:  (data) => { this.ventes.set(data); this.loading.set(false); },
      error: ()     => { this.error.set('Erreur lors du chargement.'); this.loading.set(false); },
    });
  }

  echLabel(s: EcheanceStatut): string {
    return { EN_ATTENTE: 'En attente', PAYEE: 'Payée', EN_RETARD: 'En retard' }[s] ?? s;
  }

  echClass(s: EcheanceStatut): string {
    return { EN_ATTENTE: 'badge-info', PAYEE: 'badge-success', EN_RETARD: 'badge-error' }[s] ?? '';
  }

  paidTotal(v: Vente): number {
    return v.echeances.filter(e => e.statut === 'PAYEE').reduce((sum, e) => sum + e.montant, 0);
  }

  remainingTotal(v: Vente): number {
    return v.echeances.filter(e => e.statut !== 'PAYEE').reduce((sum, e) => sum + e.montant, 0);
  }
}
