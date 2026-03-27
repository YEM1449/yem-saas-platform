import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ProjectService } from './project.service';
import { Project, ProjectKpi } from '../../core/models/project.model';
import { ErrorResponse } from '../../core/models/error-response.model';
import { AuthService } from '../../core/auth/auth.service';
import { environment } from '../../../environments/environment';

interface DocumentItem {
  id: string;
  fileName: string;
  contentType: string;
  createdAt: string;
}

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
  private http = inject(HttpClient);
  auth = inject(AuthService);

  project: Project | null = null;
  kpi: ProjectKpi | null = null;
  documents: DocumentItem[] = [];

  loadingProject = true;
  loadingKpi = true;
  projectError = '';
  kpiError = '';
  logoUploading = false;
  docUploading = false;

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

  get isAdminOrManager(): boolean {
    const r = this.auth.user?.role;
    return r === 'ROLE_ADMIN' || r === 'ROLE_MANAGER';
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

    this.loadDocuments(id);

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

  loadDocuments(id: string): void {
    this.http.get<DocumentItem[]>(
      `${environment.apiUrl}/api/documents?entityType=PROJECT&entityId=${id}`
    ).subscribe({ next: (docs) => this.documents = docs, error: () => {} });
  }

  onLogoFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file || !this.project) return;
    this.logoUploading = true;
    this.svc.uploadLogo(this.project.id, file).subscribe({
      next: (p) => { this.project = p; this.logoUploading = false; },
      error: () => { this.logoUploading = false; },
    });
  }

  deleteLogo(): void {
    if (!this.project) return;
    this.svc.deleteLogo(this.project.id).subscribe({
      next: () => { if (this.project) this.project = { ...this.project, logoUrl: null }; },
      error: () => {},
    });
  }

  onDocFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file || !this.project) return;
    this.docUploading = true;
    const form = new FormData();
    form.append('file', file);
    this.http.post(
      `${environment.apiUrl}/api/documents?entityType=PROJECT&entityId=${this.project.id}`,
      form
    ).subscribe({
      next: () => { this.docUploading = false; this.loadDocuments(this.project!.id); },
      error: () => { this.docUploading = false; },
    });
  }

  deleteDocument(docId: string): void {
    this.http.delete(`${environment.apiUrl}/api/documents/${docId}`).subscribe({
      next: () => this.documents = this.documents.filter(d => d.id !== docId),
      error: () => {},
    });
  }

  isImage(contentType: string): boolean {
    return contentType?.startsWith('image/') ?? false;
  }

  private formatAmount(value: number): string {
    if (!value) return '0';
    return new Intl.NumberFormat('fr-MA', { style: 'decimal', maximumFractionDigits: 2 }).format(value);
  }
}
