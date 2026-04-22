import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HomeDashboardService, AgentLeaderboardRow } from '../dashboard/home-dashboard.service';
import { ReportExportService, ReportFilters } from './report-export.service';

@Component({
  selector: 'app-agents-report',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './agents-report.component.html',
  styleUrl: './agents-report.component.css',
})
export class AgentsReportComponent implements OnInit {
  private dashSvc = inject(HomeDashboardService);
  private svc     = inject(ReportExportService);

  from      = '';
  to        = '';
  agents    = signal<AgentLeaderboardRow[]>([]);
  loading   = signal(false);
  exporting = signal(false);
  error     = signal('');

  ngOnInit(): void { this.loadPreview(); }

  loadPreview(): void {
    this.loading.set(true);
    this.error.set('');
    this.dashSvc.getSnapshot().subscribe({
      next: snap => {
        this.agents.set(snap.topAgents ?? []);
        this.loading.set(false);
      },
      error: () => { this.error.set('Erreur lors du chargement.'); this.loading.set(false); },
    });
  }

  exportPdf(): void {
    this.exporting.set(true);
    this.svc.downloadAgentsPdf(this.filters()).subscribe({
      next: blob => {
        this.svc.triggerDownload(blob, `rapport_agents_${today()}.pdf`);
        this.exporting.set(false);
      },
      error: () => { this.error.set('Erreur export PDF.'); this.exporting.set(false); },
    });
  }

  exportCsv(): void {
    this.exporting.set(true);
    this.svc.downloadAgentsCsv(this.filters()).subscribe({
      next: blob => {
        this.svc.triggerDownload(blob, `rapport_agents_${today()}.csv`);
        this.exporting.set(false);
      },
      error: () => { this.error.set('Erreur export CSV.'); this.exporting.set(false); },
    });
  }

  get totalCA(): number {
    return this.agents().reduce((s, a) => s + a.totalCA, 0);
  }

  get totalVentes(): number {
    return this.agents().reduce((s, a) => s + a.ventesCount, 0);
  }

  formatAmount(n: number): string {
    if (n === 0) return '—';
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + ' M MAD';
    if (n >= 1_000)     return (n / 1_000).toFixed(0) + ' K MAD';
    return n.toLocaleString('fr-FR') + ' MAD';
  }

  private filters(): ReportFilters {
    return { from: this.from || undefined, to: this.to || undefined };
  }
}

function today(): string { return new Date().toISOString().slice(0, 10); }
