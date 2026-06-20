import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ProjectService } from './project.service';
import { absorptionRate, absorptionTone as toneBucket } from '../../core/utils/absorption';
import { Project, ProjectKpi } from '../../core/models/project.model';
import { ErrorResponse } from '../../core/models/error-response.model';
import { AuthService } from '../../core/auth/auth.service';
import { DocumentListComponent } from '../documents/document-list.component';
import { BuildingViewComponent } from './building-view/building-view.component';
import { Viewer3dApiService } from '../../modules/viewer-3d/services/viewer-3d-api.service';
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
  imports: [FormsModule, RouterLink, DocumentListComponent, BuildingViewComponent, DatePipe, DecimalPipe, TranslatePipe],
  templateUrl: './project-detail.component.html',
  styleUrl: './project-detail.component.css',
})
export class ProjectDetailComponent implements OnInit, OnDestroy {
  private i18n = inject(I18nService);
  private svc = inject(ProjectService);
  private route = inject(ActivatedRoute);
  private http = inject(HttpClient);
  private viewer3d = inject(Viewer3dApiService);
  auth = inject(AuthService);

  activeTab: 'apercu' | 'plan' | 'documents' = 'apercu';

  /** True once a 3D model is confirmed present — gates the "Visualiseur 3D" entry for agents. */
  has3dModel = false;

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

  // ── Absorption headline (the project's lead commercial metric) ──────────
  /** Canonical absorption (sold / commercialised) — see core/utils/absorption. */
  get absorptionPct(): number | null {
    const sb = this.kpi?.statusBreakdown;
    if (!sb) return null;
    return absorptionRate(sb['ACTIVE'] ?? 0, sb['RESERVED'] ?? 0, sb['SOLD'] ?? 0);
  }
  get availableCount(): number { return this.kpi?.statusBreakdown?.['ACTIVE'] ?? 0; }
  get reservedCount(): number  { return this.kpi?.statusBreakdown?.['RESERVED'] ?? 0; }
  get soldCount(): number      { return this.kpi?.statusBreakdown?.['SOLD'] ?? 0; }
  get absorptionTone(): string {
    const tone = toneBucket(this.absorptionPct);
    return tone ? `abs-${tone}` : '';
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

  /** Show the 3D viewer entry when a model exists, or to admins/managers so they can set one up. */
  get show3dEntry(): boolean {
    return this.has3dModel || this.isAdminOrManager;
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
          ? this.i18n.instant('projects.detail.errors.notFound')
          : (body?.message ?? this.i18n.instant('projects.detail.errors.loadFailed', { status: err.status }));
      },
    });

    this.loadDocuments(id);

    // Detect a 3D model so agents only see the viewer entry when there's something to view.
    // 404 (no model yet) leaves has3dModel=false; ADMIN/MANAGER still get the entry to set it up.
    this.viewer3d.getModel(id).subscribe({
      next: () => { this.has3dModel = true; },
      error: () => { this.has3dModel = false; },
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
          this.kpiError = body?.message ?? this.i18n.instant('projects.detail.errors.kpiLoadFailed', { status: err.status });
        }
      },
    });
  }

  loadDocuments(id: string): void {
    this.http.get<DocumentItem[]>(
      `${environment.apiUrl}/api/documents?entityType=PROJECT&entityId=${id}`
    ).subscribe({ next: (docs) => this.documents = docs, error: () => {} });
  }

  heroInitials(name: string): string {
    return name.split(/\s+/).slice(0, 2).map(w => w[0]?.toUpperCase() ?? '').join('');
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
      version: this.project.version,
    }).subscribe({
      next: (p) => { this.project = p; this.editing = false; this.editSaving = false; },
      error: (err: HttpErrorResponse) => {
        this.editSaving = false;
        const body = err.error as ErrorResponse | null;
        // 409 = another user saved while this form was open (EX-003): tell them to reload.
        this.editError = err.status === 409
          ? 'Ce projet a été modifié entre-temps. Rechargez la page et réessayez.'
          : body?.message ?? 'Erreur lors de la sauvegarde.';
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
    return this.i18n.instant('projects.detail.status.' + key);
  }

  statusColor(key: string): string {
    const map: Record<string, string> = {
      DRAFT: '#cbd5e1', ACTIVE: '#22c55e', RESERVED: '#ea580c',
      SOLD: '#1e293b', WITHDRAWN: '#94a3b8', ARCHIVED: '#94a3b8',
      // legacy aliases (defensive)
      AVAILABLE: '#22c55e', UNDER_CONSTRUCTION: '#8b5cf6', UNAVAILABLE: '#94a3b8',
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
