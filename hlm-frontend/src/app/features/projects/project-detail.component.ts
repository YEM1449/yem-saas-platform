import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { ProjectService } from './project.service';
import { Project, ProjectKpi } from '../../core/models/project.model';
import { ErrorResponse } from '../../core/models/error-response.model';
import { AuthService } from '../../core/auth/auth.service';
import { DocumentListComponent } from '../documents/document-list.component';
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
  imports: [CommonModule, FormsModule, RouterLink, TranslateModule, DocumentListComponent],
  templateUrl: './project-detail.component.html',
  styleUrl: './project-detail.component.css',
})
export class ProjectDetailComponent implements OnInit, OnDestroy {
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

  /** Inline edit state */
  editing = false;
  editSaving = false;
  editError = '';
  editForm = { name: '', description: '' };

  /** Archive confirmation */
  archiving = false;

  logoSrc: string | null = null;
  private logoObjectUrl: string | null = null;

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
        if (p.logoUrl) this.fetchLogoAsBlob(p.logoUrl);
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

  startEdit(): void {
    if (!this.project) return;
    this.editForm = { name: this.project.name, description: this.project.description ?? '' };
    this.editError = '';
    this.editing = true;
  }

  cancelEdit(): void { this.editing = false; }

  saveEdit(): void {
    if (!this.project || !this.editForm.name.trim()) return;
    this.editSaving = true;
    this.editError = '';
    this.svc.update(this.project.id, {
      name: this.editForm.name.trim(),
      description: this.editForm.description.trim() || undefined,
    }).subscribe({
      next: (p) => { this.project = p; this.editing = false; this.editSaving = false; },
      error: (err: HttpErrorResponse) => {
        this.editSaving = false;
        const body = err.error as ErrorResponse | null;
        this.editError = body?.message ?? 'Erreur lors de la sauvegarde.';
      },
    });
  }

  archiveProject(): void {
    if (!this.project || !confirm(`Archiver le projet "${this.project.name}" ?`)) return;
    this.archiving = true;
    this.svc.archive(this.project.id).subscribe({
      next: () => { if (this.project) this.project = { ...this.project, status: 'ARCHIVED' }; this.archiving = false; },
      error: () => { this.archiving = false; },
    });
  }

  /** Progress bar width % for a status/type relative to totalProperties */
  barWidth(count: number): number {
    if (!this.kpi || !this.kpi.totalProperties) return 0;
    return Math.round(count / this.kpi.totalProperties * 100);
  }

  statusLabel(key: string): string {
    const map: Record<string, string> = {
      AVAILABLE: 'Disponible', RESERVED: 'Réservé', SOLD: 'Vendu',
      UNDER_CONSTRUCTION: 'En construction', UNAVAILABLE: 'Indisponible',
    };
    return map[key] ?? key;
  }

  statusColor(key: string): string {
    const map: Record<string, string> = {
      AVAILABLE: '#10b981', RESERVED: '#f59e0b', SOLD: '#3b82f6',
      UNDER_CONSTRUCTION: '#8b5cf6', UNAVAILABLE: '#94a3b8',
    };
    return map[key] ?? '#6366f1';
  }

  ngOnDestroy(): void {
    this.revokeLogo();
  }

  onLogoFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file || !this.project) return;
    this.logoUploading = true;
    this.svc.uploadLogo(this.project.id, file).subscribe({
      next: (p) => {
        this.project = p;
        this.logoUploading = false;
        if (p.logoUrl) this.fetchLogoAsBlob(p.logoUrl);
      },
      error: () => { this.logoUploading = false; },
    });
  }

  deleteLogo(): void {
    if (!this.project) return;
    this.svc.deleteLogo(this.project.id).subscribe({
      next: () => {
        if (this.project) this.project = { ...this.project, logoUrl: null };
        this.revokeLogo();
      },
      error: () => {},
    });
  }

  private fetchLogoAsBlob(relativeUrl: string): void {
    this.http.get(`${environment.apiUrl}${relativeUrl}`, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        this.revokeLogo();
        this.logoObjectUrl = URL.createObjectURL(blob);
        this.logoSrc = this.logoObjectUrl;
      },
      error: () => { this.logoSrc = null; },
    });
  }

  private revokeLogo(): void {
    if (this.logoObjectUrl) {
      URL.revokeObjectURL(this.logoObjectUrl);
      this.logoObjectUrl = null;
    }
    this.logoSrc = null;
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
