import {
  Component, OnInit, OnDestroy, Input,
  ChangeDetectionStrategy, ChangeDetectorRef, signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription, Subject, takeUntil, catchError, EMPTY } from 'rxjs';

import { ProjectViewer3dComponent } from '../project-viewer-3d/project-viewer-3d.component';
import { Viewer3dApiService }       from '../../services/viewer-3d-api.service';
import { LotStatusSnapshot, LOT_STATUS_COLORS, LOT_STATUS_LABELS, LotDisplayStatus } from '../../models/lot-3d-status.model';

interface KpiEntry {
  label:  string;
  value:  string;
  sub?:   string;
  color?: string;
}

/**
 * Feature 2 — 3D Dashboard Tab.
 *
 * Lazy-loaded as a child of the existing commercial dashboard.
 * Embeds the ProjectViewer3dComponent (Feature 1) and adds a
 * floating KPI overlay panel and PDF export.
 */
@Component({
  selector: 'app-dashboard-3d-tab',
  standalone: true,
  imports: [CommonModule, FormsModule, ProjectViewer3dComponent],
  templateUrl: './dashboard-3d-tab.component.html',
  styleUrl:    './dashboard-3d-tab.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Dashboard3dTabComponent implements OnInit, OnDestroy {

  @Input({ required: true }) projetId!: string;

  kpiVisible   = signal(true);
  exporting    = signal(false);
  statuses:    LotStatusSnapshot[] = [];
  filteredStatut: string = '';

  readonly statusOptions = (Object.keys(LOT_STATUS_COLORS) as LotDisplayStatus[]).map(k => ({
    value: k, label: LOT_STATUS_LABELS[k],
  }));

  kpis = signal<KpiEntry[]>([]);

  private destroy$ = new Subject<void>();

  constructor(
    private api: Viewer3dApiService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.api.getStatusSnapshot(this.projetId).pipe(
      catchError(() => EMPTY),
      takeUntil(this.destroy$)
    ).subscribe(statuses => {
      this.statuses = statuses;
      this.kpis.set(this.computeKpis(statuses));
      this.cdr.markForCheck();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  toggleKpi(): void {
    this.kpiVisible.update(v => !v);
  }

  /** Called when a lot is selected in the 3D viewer (via CustomEvent bubbling). */
  onLotSelected(event: Event): void {
    const detail = (event as CustomEvent).detail;
    // Could open LotDetailPanel here — wired via event bubbling
    console.log('[Dashboard3D] lot-selected', detail);
  }

  async exportPdf(): Promise<void> {
    this.exporting.set(true);
    this.cdr.markForCheck();
    try {
      // Dynamic import to keep three.js and html2canvas out of the main bundle
      const [html2canvasModule, jsPDFModule] = await Promise.all([
        import('html2canvas' as any).catch(() => null),
        import('jspdf' as any).catch(() => null),
      ]);

      if (!html2canvasModule || !jsPDFModule) {
        alert('Export PDF non disponible (modules html2canvas/jsPDF introuvables).');
        return;
      }

      const html2canvas = html2canvasModule.default;
      const jsPDF       = jsPDFModule.jsPDF ?? jsPDFModule.default;

      const container = document.querySelector('.dashboard-3d-host') as HTMLElement;
      if (!container) return;

      const canvas = await html2canvas(container, { useCORS: true, scale: 1.5 });
      const imgData = canvas.toDataURL('image/jpeg', 0.85);

      const pdf = new jsPDF({ orientation: 'landscape', unit: 'px', format: [canvas.width, canvas.height] });
      pdf.addImage(imgData, 'JPEG', 0, 0, canvas.width, canvas.height);
      pdf.save(`rapport-3d-${this.projetId}-${new Date().toISOString().slice(0,10)}.pdf`);
    } finally {
      this.exporting.set(false);
      this.cdr.markForCheck();
    }
  }

  private computeKpis(statuses: LotStatusSnapshot[]): KpiEntry[] {
    if (!statuses.length) return [];

    const total      = statuses.length;
    const disponible = statuses.filter(s => s.statut === 'DISPONIBLE').length;
    const reserve    = statuses.filter(s => s.statut === 'RESERVE').length;
    const vendu      = statuses.filter(s => s.statut === 'VENDU').length;
    const livre      = statuses.filter(s => s.statut === 'LIVRE').length;

    const sum = (arr: LotStatusSnapshot[]) =>
      arr.reduce((acc, s) => acc + (s.prix ?? 0), 0);

    const caRealise   = sum(statuses.filter(s => s.statut === 'VENDU'));
    const caPrevision = sum(statuses.filter(s => s.statut === 'RESERVE'));

    const fmt = (n: number) =>
      new Intl.NumberFormat('fr-MA', { notation: 'compact', maximumFractionDigits: 1 }).format(n) + ' MAD';
    const pct = (n: number) => `${((n / total) * 100).toFixed(0)}%`;

    return [
      { label: 'Disponibles',     value: String(disponible), sub: pct(disponible), color: '#3B82F6' },
      { label: 'Réservés',        value: String(reserve),    sub: pct(reserve),    color: '#F59E0B' },
      { label: 'Vendus',          value: String(vendu),      sub: pct(vendu),       color: '#10B981' },
      { label: 'Livrés',          value: String(livre),      sub: pct(livre),       color: '#6B7280' },
      { label: 'CA réalisé',      value: caRealise   > 0 ? fmt(caRealise)   : '—' },
      { label: 'CA prévisionnel', value: caPrevision > 0 ? fmt(caPrevision) : '—' },
    ];
  }
}
