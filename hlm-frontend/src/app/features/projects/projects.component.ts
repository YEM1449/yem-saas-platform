import { Component, inject, OnInit } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ProjectService } from './project.service';
import { absorptionRate, absorptionTone } from '../../core/utils/absorption';
import { Project, ProjectKpi } from '../../core/models/project.model';
import { ErrorResponse } from '../../core/models/error-response.model';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-projects',
  standalone: true,
  imports: [RouterLink, FormsModule, DecimalPipe],
  templateUrl: './projects.component.html',
  styleUrl: './projects.component.css',
})
export class ProjectsComponent implements OnInit {
  private svc    = inject(ProjectService);
  private auth   = inject(AuthService);
  private router = inject(Router);

  projects: Project[] = [];
  loading = true;
  error   = '';

  searchQuery  = '';
  filterStatus = '';

  /** Modal state */
  showModal   = false;
  submitting  = false;
  submitError = '';

  form = { name: '', description: '' };

  get filtered(): Project[] {
    let list = this.projects;
    if (this.filterStatus) list = list.filter(p => p.status === this.filterStatus);
    const q = this.searchQuery.toLowerCase().trim();
    if (q) list = list.filter(p =>
      p.name.toLowerCase().includes(q) || (p.description ?? '').toLowerCase().includes(q)
    );
    return list;
  }

  get activeCount(): number  { return this.projects.filter(p => p.status === 'ACTIVE').length; }
  get archivedCount(): number { return this.projects.filter(p => p.status === 'ARCHIVED').length; }

  projectInitials(name: string): string {
    return name.split(/\s+/).slice(0, 2).map(w => w[0]?.toUpperCase() ?? '').join('');
  }

  daysSince(dateStr: string): number {
    return Math.floor((Date.now() - new Date(dateStr).getTime()) / 86_400_000);
  }

  get canWrite(): boolean {
    const r = this.auth.user?.role;
    return r === 'ROLE_ADMIN' || r === 'ROLE_MANAGER';
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error   = '';
    this.svc.list().subscribe({
      next: (data) => { this.projects = data; this.loading = false; this.loadKpis(); },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        const body = err.error as ErrorResponse | null;
        if      (err.status === 401) this.error = 'Session expired. Please log in again.';
        else if (err.status === 403) this.error = 'Access denied.';
        else                          this.error = body?.message ?? `Failed to load projects (${err.status})`;
      },
    });
  }

  // ── Per-project KPIs (enriches the cards: lots · absorption · sales) ──────
  // A scannable project card should answer "how big, how sold, how much" at a
  // glance — not just a name and a creation date.
  projectKpis = new Map<string, ProjectKpi>();

  private loadKpis(): void {
    // The /kpis endpoint is @PreAuthorize("hasAnyRole('ADMIN','MANAGER')") —
    // agents get 403. Skip the N per-project requests rather than firing them
    // only to fail (and the enriched stats aren't shown to agents anyway).
    if (!this.canWrite) return;
    if (this.projects.length === 0) return;
    forkJoin(
      this.projects.map(p => this.svc.getKpis(p.id).pipe(catchError(() => of(null))))
    ).subscribe(results => {
      results.forEach((k, i) => { if (k) this.projectKpis.set(this.projects[i].id, k); });
    });
  }

  unitsOf(p: Project): number | null {
    return this.projectKpis.get(p.id)?.totalProperties ?? null;
  }

  availableOf(p: Project): number {
    return this.projectKpis.get(p.id)?.statusBreakdown?.['ACTIVE'] ?? 0;
  }

  salesValueOf(p: Project): number {
    return this.projectKpis.get(p.id)?.salesTotalAmount ?? 0;
  }

  /** Canonical absorption (sold / commercialised) — see core/utils/absorption. */
  absorptionOf(p: Project): number | null {
    const sb = this.projectKpis.get(p.id)?.statusBreakdown;
    if (!sb) return null;
    return absorptionRate(sb['ACTIVE'] ?? 0, sb['RESERVED'] ?? 0, sb['SOLD'] ?? 0);
  }

  absorptionToneClass(pct: number | null): string {
    const tone = absorptionTone(pct);
    return tone ? `pabs-${tone}` : '';
  }

  formatValueShort(n: number): string {
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(1).replace(/\.0$/, '') + ' M';
    if (n >= 1_000)     return Math.round(n / 1_000) + ' k';
    return String(n);
  }

  openModal(): void {
    // Navigate to the 5-step wizard for creating programmes with tranches
    this.router.navigate(['/app/projects/new']);
  }

  closeModal(): void {
    if (this.submitting) return;
    this.showModal = false;
  }

  submitCreate(): void {
    if (!this.form.name.trim()) {
      this.submitError = 'Project name is required.';
      return;
    }
    this.submitting  = true;
    this.submitError = '';

    this.svc.create({
      name:        this.form.name.trim(),
      description: this.form.description.trim() || undefined,
    }).subscribe({
      next: (created) => {
        this.submitting = false;
        this.projects   = [created, ...this.projects];
        this.showModal  = false;
      },
      error: (err: HttpErrorResponse) => {
        this.submitting = false;
        const body = err.error as ErrorResponse | null;
        this.submitError = body?.message ?? `Failed to create project (${err.status})`;
      },
    });
  }
}
