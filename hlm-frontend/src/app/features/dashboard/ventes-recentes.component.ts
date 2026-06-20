import { Component, Input, ChangeDetectionStrategy, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { DatePipe } from '@angular/common';
import { RecentVenteRow } from './home-dashboard.service';

@Component({
  selector: 'app-ventes-recentes',
  standalone: true,
  imports: [RouterLink, DatePipe, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './ventes-recentes.component.html',
  styleUrl: './ventes-recentes.component.css',
})
export class VentesRecentesComponent {
  private i18n = inject(I18nService);
  @Input() ventes: RecentVenteRow[] = [];

  statutClass(s: string): string {
    const m: Record<string, string> = {
      COMPROMIS: 'badge-info', FINANCEMENT: 'badge-warning',
      ACTE: 'badge-primary', LIVRE_DEFINITIF: 'badge-success', ANNULE: 'badge-error',
    };
    return 'badge ' + (m[s] ?? '');
  }

  statutLabel(s: string): string {
    return this.i18n.instant('ventes.statut.' + s);
  }

  formatAmount(n: number | null | undefined): string {
    if (n == null || n === 0) return '—';
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(1).replace('.', ',') + ' M MAD';
    if (n >= 1_000)     return (n / 1_000).toFixed(0) + ' K MAD';
    return n.toLocaleString('fr-FR') + ' MAD';
  }
}
