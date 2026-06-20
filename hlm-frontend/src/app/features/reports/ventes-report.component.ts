import { Component, inject, OnInit, signal } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { take } from 'rxjs';
import { ReportExportService, ReportFilters, ReportStatut } from './report-export.service';
import { VenteService, Vente } from '../ventes/vente.service';

@Component({
  selector: 'app-ventes-report',
  standalone: true,
  imports: [FormsModule, DatePipe, TranslatePipe],
  templateUrl: './ventes-report.component.html',
  styleUrl: './ventes-report.component.css',
})
export class VentesReportComponent implements OnInit {
  private i18n = inject(I18nService);
  private svc    = inject(ReportExportService);
  private venteSvc = inject(VenteService);
  private route  = inject(ActivatedRoute);

  from   = '';
  to     = '';
  statut: ReportStatut | '' = '';

  ventes   = signal<Vente[]>([]);
  loading  = signal(false);
  exporting = signal(false);
  error    = signal('');

  readonly STATUTS: { value: ReportStatut | ''; label: string }[] = [
    { value: '', label: this.i18n.instant('reports.common.allStatuses') },
    { value: 'PROSPECT',            label: this.i18n.instant('ventes.statut.PROSPECT') },
    { value: 'OPTION',              label: this.i18n.instant('ventes.statut.OPTION') },
    { value: 'RESERVE',             label: this.i18n.instant('ventes.statut.RESERVE') },
    { value: 'EN_RETRACTATION',     label: this.i18n.instant('ventes.statut.EN_RETRACTATION') },
    { value: 'ACOMPTE',             label: this.i18n.instant('ventes.statut.ACOMPTE') },
    { value: 'COMPROMIS',           label: this.i18n.instant('ventes.statut.COMPROMIS') },
    { value: 'FINANCEMENT',         label: this.i18n.instant('ventes.statut.FINANCEMENT') },
    { value: 'ACTE',                label: this.i18n.instant('ventes.statut.ACTE') },
    { value: 'LIVRE_AVEC_RESERVES', label: this.i18n.instant('ventes.statut.LIVRE_AVEC_RESERVES') },
    { value: 'RESERVES_LEVEES',     label: this.i18n.instant('ventes.statut.RESERVES_LEVEES') },
    { value: 'LIVRE_DEFINITIF',     label: this.i18n.instant('ventes.statut.LIVRE_DEFINITIF') },
    { value: 'ANNULE',              label: this.i18n.instant('ventes.statut.ANNULE') }];

  

  ngOnInit(): void {
    this.route.queryParamMap.pipe(take(1)).subscribe(p => {
      const s = p.get('statut') as ReportStatut | null;
      if (s) this.statut = s;
      const from = p.get('from');
      const to   = p.get('to');
      if (from) this.from = from;
      if (to)   this.to   = to;
      this.loadPreview();
    });
  }

  loadPreview(): void {
    this.loading.set(true);
    this.error.set('');
    this.venteSvc.list().subscribe({
      next: all => {
        let items = all;
        if (this.statut) items = items.filter(v => v.statut === this.statut);
        if (this.from)   items = items.filter(v => v.createdAt >= this.from + 'T00:00:00');
        if (this.to)     items = items.filter(v => v.createdAt <= this.to   + 'T23:59:59');
        this.ventes.set(items);
        this.loading.set(false);
      },
      error: () => { this.error.set('Erreur lors du chargement.'); this.loading.set(false); },
    });
  }

  exportPdf(): void {
    this.exporting.set(true);
    this.svc.downloadVentesPdf(this.filters()).subscribe({
      next: blob => {
        this.svc.triggerDownload(blob, `rapport_ventes_${today()}.pdf`);
        this.exporting.set(false);
      },
      error: () => { this.error.set('Erreur export PDF.'); this.exporting.set(false); },
    });
  }

  exportCsv(): void {
    this.exporting.set(true);
    this.svc.downloadVentesCsv(this.filters()).subscribe({
      next: blob => {
        this.svc.triggerDownload(blob, `rapport_ventes_${today()}.csv`);
        this.exporting.set(false);
      },
      error: () => { this.error.set('Erreur export CSV.'); this.exporting.set(false); },
    });
  }

  get totalCA(): number {
    return this.ventes().reduce((s, v) => s + (v.prixVente ?? 0), 0);
  }

  formatAmount(n: number): string {
    if (n === 0) return '—';
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + ' M MAD';
    if (n >= 1_000)     return (n / 1_000).toFixed(0) + ' K MAD';
    return n.toLocaleString('fr-FR') + ' MAD';
  }

  statutLabel(s: string): string { return this.i18n.instant('ventes.statut.' + s); }

  private filters(): ReportFilters {
    return {
      from:   this.from   || undefined,
      to:     this.to     || undefined,
      statut: this.statut || undefined,
    };
  }
}

function today(): string {
  return new Date().toISOString().slice(0, 10);
}
