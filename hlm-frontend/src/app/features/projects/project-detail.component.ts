import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ProjectService } from './project.service';
import { Project, ProjectKpi } from '../../core/models/project.model';
import { ErrorResponse } from '../../core/models/error-response.model';

@Component({
  selector: 'app-project-detail',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './project-detail.component.html',
  styleUrl: './project-detail.component.css',
})
export class ProjectDetailComponent implements OnInit {
  private svc = inject(ProjectService);
  private route = inject(ActivatedRoute);

  project: Project | null = null;
  kpi: ProjectKpi | null = null;

  loadingProject = true;
  loadingKpi = true;
  projectError = '';
  kpiError = '';

  get projectId(): string {
    return this.route.snapshot.paramMap.get('id') ?? '';
  }

  get kpiEntries(): { label: string; value: string }[] {
    if (!this.kpi) return [];
    return [
      { label: 'Total Properties', value: String(this.kpi.totalProperties) },
      { label: 'Total Deposits', value: String(this.kpi.depositsCount) },
      { label: 'Deposits Amount', value: this.formatAmount(this.kpi.depositsTotalAmount) },
      { label: 'Sales (Confirmed)', value: String(this.kpi.salesCount) },
      { label: 'Sales Amount', value: this.formatAmount(this.kpi.salesTotalAmount) },
    ];
  }

  get typeEntries(): { label: string; value: number }[] {
    if (!this.kpi?.propertiesByType) return [];
    return Object.entries(this.kpi.propertiesByType).map(([k, v]) => ({ label: k, value: v }));
  }

  get statusEntries(): { label: string; value: number }[] {
    if (!this.kpi?.statusBreakdown) return [];
    return Object.entries(this.kpi.statusBreakdown).map(([k, v]) => ({ label: k, value: v }));
  }

  ngOnInit(): void {
    const id = this.projectId;

    this.svc.getById(id).subscribe({
      next: (p) => {
        this.project = p;
        this.loadingProject = false;
      },
      error: (err: HttpErrorResponse) => {
        this.loadingProject = false;
        const body = err.error as ErrorResponse | null;
        this.projectError = err.status === 404
          ? 'Project not found.'
          : (body?.message ?? `Failed to load project (${err.status})`);
      },
    });

    this.svc.getKpis(id).subscribe({
      next: (k) => {
        this.kpi = k;
        this.loadingKpi = false;
      },
      error: (err: HttpErrorResponse) => {
        this.loadingKpi = false;
        if (err.status === 403) {
          this.kpiError = 'KPI data is not available for your role.';
        } else {
          const body = err.error as ErrorResponse | null;
          this.kpiError = body?.message ?? `Failed to load KPIs (${err.status})`;
        }
      },
    });
  }

  private formatAmount(value: number): string {
    if (!value) return '0';
    return new Intl.NumberFormat('fr-MA', { style: 'decimal', maximumFractionDigits: 2 }).format(value);
  }
}
