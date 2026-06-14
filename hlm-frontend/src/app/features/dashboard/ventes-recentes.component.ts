import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { RecentVenteRow } from './home-dashboard.service';

@Component({
  selector: 'app-ventes-recentes',
  standalone: true,
  imports: [RouterLink, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './ventes-recentes.component.html',
  styleUrl: './ventes-recentes.component.css',
})
export class VentesRecentesComponent {
  @Input() ventes: RecentVenteRow[] = [];

  statutClass(s: string): string {
    const m: Record<string, string> = {
      COMPROMIS: 'badge-info', FINANCEMENT: 'badge-warning',
      ACTE: 'badge-primary', LIVRE_DEFINITIF: 'badge-success', ANNULE: 'badge-error',
    };
    return 'badge ' + (m[s] ?? '');
  }

  statutLabel(s: string): string {
    const labels: Record<string, string> = {
      PROSPECT: 'Prospect', OPTION: 'Option', RESERVE: 'Réservé',
      EN_RETRACTATION: 'Rétractation', ACOMPTE: 'Acompte', COMPROMIS: 'Compromis',
      FINANCEMENT: 'Financement', ACTE: 'Acte', LIVRE_AVEC_RESERVES: 'Livré (réserves)',
      RESERVES_LEVEES: 'Réserves levées', LIVRE_DEFINITIF: 'Livré', ANNULE: 'Annulé',
    };
    return labels[s] ?? s;
  }

  formatAmount(n: number | null | undefined): string {
    if (n == null || n === 0) return '—';
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(1).replace('.', ',') + ' M MAD';
    if (n >= 1_000)     return (n / 1_000).toFixed(0) + ' K MAD';
    return n.toLocaleString('fr-FR') + ' MAD';
  }
}
