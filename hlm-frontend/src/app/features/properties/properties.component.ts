import { Component, inject, OnInit } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { ActivatedRoute, Router } from '@angular/router';
import { take } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { PropertyService } from './property.service';
import { ImportResult, Property } from '../../core/models/property.model';
import { ErrorResponse } from '../../core/models/error-response.model';
import { AuthService } from '../../core/auth/auth.service';
import { ProjectService } from '../projects/project.service';
import { Project } from '../../core/models/project.model';
import { ImmeubleService, Immeuble } from '../immeubles/immeuble.service';
import { HlmCardComponent } from '../../core/components/hlm-card.component';

interface CreatePropertyForm {
  projectId: string;
  type: string;
  referenceCode: string;
  title: string;
  price: number | null;
  currency: string;
  city: string;
  region: string;
  address: string;
  description: string;
  status: string;
  surfaceAreaSqm: number | null;
  landAreaSqm: number | null;
  bedrooms: number | null;
  bathrooms: number | null;
  floorNumber: number | null;
  floors: number | null;
  parkingSpaces: number | null;
  hasPool: boolean;
  hasGarden: boolean;
  buildingYear: number | null;
  buildingName: string;
}

@Component({
  selector: 'app-properties',
  standalone: true,
  imports: [FormsModule, DecimalPipe, HlmCardComponent, TranslatePipe],
  templateUrl: './properties.component.html',
  styleUrl: './properties.component.css',
})
export class PropertiesComponent implements OnInit {
  private svc         = inject(PropertyService);
  private i18n        = inject(I18nService);
  private projectSvc  = inject(ProjectService);
  private immeubleSvc = inject(ImmeubleService);
  private router      = inject(Router);
  private route       = inject(ActivatedRoute);
  private auth        = inject(AuthService);

  properties: Property[]  = [];
  projects: Project[]     = [];
  immeubles: Immeuble[]   = [];
  loading        = true;
  projectsLoading = false;
  error          = '';

  /** Count of loaded properties (the page fetches the full capped set — #023). */
  totalElements = 0;

  searchQuery    = '';
  filterProjectId  = '';
  filterImmeubleId = '';
  filterType       = '';
  filterStatus     = '';

  // ── Agent matching refinements (client-side, on the loaded list) ─────────
  // The questions an agent actually asks finding a unit for a buyer:
  // "what's available, in this budget, cheapest first".
  availableOnly = false;
  priceMin: number | null = null;
  priceMax: number | null = null;
  sortBy: 'default' | 'price-asc' | 'price-desc' | 'surface-desc' = 'default';

  importLoading  = false;
  importResult: ImportResult | null = null;
  importError    = '';

  showModal   = false;
  submitting  = false;
  submitError = '';

  // ── View mode ───────────────────────────────────────────────────────────
  viewMode: 'cards' | 'list' = 'cards';

  // ── Multi-select ────────────────────────────────────────────────────────
  selectedIds   = new Set<string>();
  bulkLoading   = false;
  bulkError     = '';
  bulkResult: { updated: number; skipped: number } | null = null;

  readonly propertyTypes = [
    'VILLA', 'APPARTEMENT', 'STUDIO', 'T2', 'T3',
    'DUPLEX', 'COMMERCE', 'LOT', 'TERRAIN_VIERGE'];

  // Labels resolved in the template via 'properties.statusOpt.<value>'.
  readonly statusOptions = [{ value: 'DRAFT' }, { value: 'ACTIVE' }];

  form: CreatePropertyForm = {
    projectId: '', type: '', referenceCode: '', title: '',
    price: null, currency: 'MAD', city: '', region: '', address: '',
    surfaceAreaSqm: null, landAreaSqm: null, bedrooms: null, bathrooms: null,
    floorNumber: null, floors: null, parkingSpaces: null,
    hasPool: false, hasGarden: false, buildingYear: null, buildingName: '',
    description: '', status: 'DRAFT',
  };

  get needsSurface(): boolean  { return ['VILLA','APPARTEMENT','STUDIO','T2','T3','DUPLEX','COMMERCE'].includes(this.form.type); }
  get needsLand(): boolean     { return ['VILLA','LOT','TERRAIN_VIERGE'].includes(this.form.type); }
  get needsBedrooms(): boolean { return ['VILLA','APPARTEMENT','T2','T3','DUPLEX'].includes(this.form.type); }
  get needsFloorNumber(): boolean { return ['APPARTEMENT','STUDIO','T2','T3'].includes(this.form.type); }
  get needsTotalFloors(): boolean { return this.form.type === 'DUPLEX'; }

  get canImport(): boolean {
    const r = this.auth.user?.role;
    return r === 'ROLE_ADMIN' || r === 'ROLE_MANAGER';
  }
  get canCreate(): boolean { return this.canImport; }

  get filtered(): Property[] {
    const q = this.searchQuery.toLowerCase().trim();
    const list = this.properties.filter(p => {
      if (q && !(
        (p.title ?? '').toLowerCase().includes(q) ||
        p.referenceCode.toLowerCase().includes(q) ||
        (p.city  ?? '').toLowerCase().includes(q) ||
        p.type.toLowerCase().includes(q)
      )) return false;
      if (this.availableOnly && p.status !== 'ACTIVE') return false;
      if (this.priceMin != null && (p.price ?? 0) < this.priceMin) return false;
      if (this.priceMax != null && (p.price ?? Number.MAX_SAFE_INTEGER) > this.priceMax) return false;
      return true;
    });
    switch (this.sortBy) {
      case 'price-asc':    return [...list].sort((a, b) => (a.price ?? Infinity) - (b.price ?? Infinity));
      case 'price-desc':   return [...list].sort((a, b) => (b.price ?? -Infinity) - (a.price ?? -Infinity));
      case 'surface-desc': return [...list].sort((a, b) => (b.surfaceAreaSqm ?? 0) - (a.surfaceAreaSqm ?? 0));
      default:             return list;
    }
  }

  /** Live availability snapshot of the current result set — count of available
   *  units, plus the price range over the priced ones (unpriced/0 excluded so
   *  the floor isn't a misleading "0 MAD"). */
  get availableSummary(): { count: number; min: number | null; max: number | null } {
    const available = this.filtered.filter(p => p.status === 'ACTIVE');
    const prices = available
      .map(p => p.price)
      .filter((x): x is number => x != null && x > 0);
    return {
      count: available.length,
      min: prices.length ? Math.min(...prices) : null,
      max: prices.length ? Math.max(...prices) : null,
    };
  }

  hasRefinements(): boolean {
    return this.availableOnly || this.priceMin != null || this.priceMax != null || this.sortBy !== 'default';
  }

  clearRefinements(): void {
    this.availableOnly = false;
    this.priceMin = null;
    this.priceMax = null;
    this.sortBy = 'default';
  }

  formatPrice(n: number): string {
    return new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 }).format(n) + ' MAD';
  }

  // ── Selection helpers ────────────────────────────────────────────────────
  get selectedCount(): number { return this.selectedIds.size; }

  get isAllSelected(): boolean {
    return this.filtered.length > 0 && this.filtered.every(p => this.selectedIds.has(p.id));
  }

  get isSomeSelected(): boolean {
    return this.selectedIds.size > 0 && !this.isAllSelected;
  }

  isSelected(id: string): boolean { return this.selectedIds.has(id); }

  toggleSelect(id: string, event: Event): void {
    event.stopPropagation();
    if (this.selectedIds.has(id)) this.selectedIds.delete(id);
    else                          this.selectedIds.add(id);
    this.bulkResult = null;
    this.bulkError  = '';
  }

  toggleAll(): void {
    if (this.isAllSelected) {
      this.selectedIds.clear();
    } else {
      this.filtered.forEach(p => this.selectedIds.add(p.id));
    }
    this.bulkResult = null;
    this.bulkError  = '';
  }

  clearSelection(): void {
    this.selectedIds.clear();
    this.bulkResult = null;
    this.bulkError  = '';
  }

  // ── View toggle ──────────────────────────────────────────────────────────
  setView(mode: 'cards' | 'list'): void {
    this.viewMode = mode;
    localStorage.setItem('properties_view_mode', mode);
    this.selectedIds.clear();
  }

  // ── Bulk action ──────────────────────────────────────────────────────────
  bulkAction(status: string): void {
    if (this.selectedIds.size === 0 || this.bulkLoading) return;
    this.bulkLoading = true;
    this.bulkError   = '';
    this.bulkResult  = null;

    this.svc.bulkSetStatus([...this.selectedIds], status).subscribe({
      next: (result) => {
        this.bulkLoading = false;
        this.bulkResult  = result;
        this.selectedIds.clear();
        this.loadList();
      },
      error: (err: HttpErrorResponse) => {
        this.bulkLoading = false;
        this.bulkError = (err.error as ErrorResponse)?.message ?? this.i18n.instant('ventes.create.genericError', { status: err.status });
      },
    });
  }

  ngOnInit(): void {
    const saved = localStorage.getItem('properties_view_mode');
    if (saved === 'list' || saved === 'cards') this.viewMode = saved;
    this.route.queryParamMap.pipe(take(1)).subscribe(params => {
      const status    = params.get('status');
      const projectId = params.get('projectId');
      const type      = params.get('type');
      if (status)    this.filterStatus    = status;
      if (projectId) this.filterProjectId = projectId;
      if (type)      this.filterType      = type;
    });
    this.loadList();
    this.loadProjects();
  }

  loadProjects(): void {
    this.projectsLoading = true;
    this.projectSvc.list(true).subscribe({
      next: (data) => { this.projects = data; this.projectsLoading = false; },
      error: () => { this.projectsLoading = false; },
    });
  }

  loadList(): void {
    this.loading = true;
    this.error   = '';
    const params: Record<string, string> = {};
    if (this.filterProjectId)  params['projectId']  = this.filterProjectId;
    if (this.filterImmeubleId) params['immeubleId']  = this.filterImmeubleId;
    if (this.filterType)       params['type']        = this.filterType;
    if (this.filterStatus)     params['status']      = this.filterStatus;
    // This page filters/sorts/searches client-side over the full set, so it fetches all
    // matching rows (server-capped at 2000 — #023) rather than 24-per-page, which would
    // break "search finds any property". Server-side pagination via PropertyService.listPage()
    // awaits moving the free-text search + price/sort to the backend (follow-up).
    this.svc.list(params).subscribe({
      next: (data) => { this.properties = data; this.totalElements = data.length; this.loading = false; },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        const body = err.error as ErrorResponse | null;
        if      (err.status === 401) this.error = this.i18n.instant('properties.errors.sessionExpired');
        else if (err.status === 403) this.error = this.i18n.instant('properties.errors.accessDenied');
        else                         this.error = body?.message ?? this.i18n.instant('ventes.create.genericError', { status: err.status });
      },
    });
  }

  onFilterProjectChange(): void {
    this.filterImmeubleId = '';
    if (this.filterProjectId) {
      this.immeubleSvc.list(this.filterProjectId).subscribe({
        next: (data) => this.immeubles = data,
        error: () => this.immeubles = [],
      });
    } else {
      this.immeubles = [];
    }
    this.loadList();
  }

  onFilterChange(): void { this.loadList(); }

  resetFilters(): void {
    this.filterProjectId = '';
    this.filterImmeubleId = '';
    this.filterType = '';
    this.filterStatus = '';
    this.immeubles = [];
    this.loadList();
  }

  get activeFilterCount(): number {
    return [this.filterProjectId, this.filterImmeubleId, this.filterType, this.filterStatus]
      .filter(v => !!v).length;
  }

  goToDetail(propertyId: string): void {
    this.router.navigate(['/app/properties', propertyId]);
  }

  /* ── CSV Import ──────────────────────────────────────────────────────── */
  onImportFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file  = input.files?.[0];
    if (!file) return;

    this.importLoading = true;
    this.importResult  = null;
    this.importError   = '';

    this.svc.importCsv(file).subscribe({
      next: (result) => {
        this.importResult  = result;
        this.importLoading = false;
        input.value = '';
        if (result.errors.length === 0) {
          this.svc.list().subscribe({ next: (data) => (this.properties = data) });
        }
      },
      error: (err: HttpErrorResponse) => {
        this.importLoading = false;
        const body = err.error as ErrorResponse | null;
        if (err.status === 422 && err.error?.imported !== undefined) {
          this.importResult = err.error as ImportResult;
        } else {
          this.importError = body?.message ?? `Import failed (${err.status})`;
        }
        input.value = '';
      },
    });
  }

  /* ── Manual Create ───────────────────────────────────────────────────── */
  openModal(): void {
    this.form = {
      projectId: '', type: '', referenceCode: '', title: '',
      price: null, currency: 'MAD', city: '', region: '', address: '',
      surfaceAreaSqm: null, landAreaSqm: null, bedrooms: null, bathrooms: null,
      floorNumber: null, floors: null, parkingSpaces: null,
      hasPool: false, hasGarden: false, buildingYear: null, buildingName: '',
      description: '', status: 'DRAFT',
    };
    this.submitError = '';
    this.showModal   = true;
  }

  closeModal(): void { if (!this.submitting) this.showModal = false; }

  submitCreate(): void {
    if (!this.form.projectId) { this.submitError = this.i18n.instant('properties.errors.projectRequired'); return; }
    if (!this.form.type || !this.form.referenceCode.trim() || !this.form.title.trim()) {
      this.submitError = this.i18n.instant('properties.errors.mainFieldsRequired'); return;
    }
    if (this.form.price === null) { this.submitError = this.i18n.instant('properties.errors.priceRequired'); return; }
    if (this.needsSurface && !this.form.surfaceAreaSqm) {
      this.submitError = this.i18n.instant('properties.errors.surfaceRequired'); return;
    }
    if (this.needsLand && !this.form.landAreaSqm) {
      this.submitError = this.i18n.instant('properties.errors.landRequired'); return;
    }
    if (this.needsBedrooms && (!this.form.bedrooms || !this.form.bathrooms)) {
      this.submitError = this.i18n.instant('properties.errors.roomsRequired'); return;
    }
    if (this.needsFloorNumber && this.form.floorNumber === null) {
      this.submitError = "Le numéro d'étage est obligatoire pour ce type de bien."; return;
    }
    if (this.needsTotalFloors && this.form.floors === null) {
      this.submitError = "Le nombre total d'étages est obligatoire pour un duplex."; return;
    }

    this.submitting  = true;
    this.submitError = '';

    this.svc.create({
      projectId: this.form.projectId,
      type: this.form.type,
      referenceCode: this.form.referenceCode.trim(),
      title: this.form.title.trim(),
      description: this.form.description.trim() || null,
      price: this.form.price!,
      currency: this.form.currency || 'MAD',
      city: this.form.city.trim() || null,
      region: this.form.region.trim() || null,
      address: this.form.address.trim() || null,
      surfaceAreaSqm: this.form.surfaceAreaSqm,
      landAreaSqm: this.form.landAreaSqm,
      bedrooms: this.form.bedrooms,
      bathrooms: this.form.bathrooms,
      floorNumber: this.form.floorNumber,
      floors: this.form.floors,
      parkingSpaces: this.form.parkingSpaces,
      hasPool: this.form.hasPool || null,
      hasGarden: this.form.hasGarden || null,
      buildingYear: this.form.buildingYear,
      buildingName: this.form.buildingName.trim() || null,
      listedForSale: this.form.status === 'ACTIVE',
    }).subscribe({
      next: (created) => {
        this.submitting = false;
        this.properties = [created, ...this.properties];
        this.showModal  = false;
      },
      error: (err: HttpErrorResponse) => {
        this.submitting  = false;
        const body = err.error as ErrorResponse | null;
        this.submitError = body?.message ?? this.i18n.instant('ventes.create.genericError', { status: err.status });
      },
    });
  }

  /* ── Display helpers ─────────────────────────────────────────────────── */
  badgeClass(status: string): string { return 'badge badge-' + status.toLowerCase(); }

  statusLabel(status: string): string {
    return this.i18n.instant('properties.status.' + status);
  }

  typeLabel(type: string): string {
    return this.i18n.instant('properties.type.' + type);
  }
}
