import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { PropertyService } from './property.service';
import { ImportResult, Property } from '../../core/models/property.model';
import { ErrorResponse } from '../../core/models/error-response.model';
import { AuthService } from '../../core/auth/auth.service';
import { ProjectService } from '../projects/project.service';
import { Project } from '../../core/models/project.model';

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
  // Type-specific fields
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
  imports: [CommonModule, FormsModule],
  templateUrl: './properties.component.html',
  styleUrl: './properties.component.css',
})
export class PropertiesComponent implements OnInit {
  private svc        = inject(PropertyService);
  private projectSvc = inject(ProjectService);
  private router     = inject(Router);
  private auth       = inject(AuthService);

  properties: Property[] = [];
  projects: Project[] = [];
  loading = true;
  projectsLoading = false;
  error   = '';

  /** Live search */
  searchQuery = '';

  /** CSV import state */
  importLoading = false;
  importResult: ImportResult | null = null;
  importError = '';

  /** Manual create modal state */
  showModal   = false;
  submitting  = false;
  submitError = '';

  readonly propertyTypes = [
    'VILLA', 'APPARTEMENT', 'STUDIO', 'T2', 'T3',
    'DUPLEX', 'COMMERCE', 'LOCAL', 'TERRAIN',
  ];

  readonly statusOptions = [
    { value: 'DRAFT',  label: 'Draft (not yet on market)' },
    { value: 'ACTIVE', label: 'Active (available for reservation)' },
  ];

  form: CreatePropertyForm = {
    projectId: '', type: '', referenceCode: '', title: '',
    price: null, currency: 'MAD', city: '', region: '', address: '',
    surfaceAreaSqm: null, landAreaSqm: null, bedrooms: null, bathrooms: null,
    floorNumber: null, floors: null, parkingSpaces: null,
    hasPool: false, hasGarden: false, buildingYear: null, buildingName: '',
    description: '', status: 'DRAFT',
  };

  /** Returns whether the selected type requires surfaceAreaSqm */
  get needsSurface(): boolean {
    return ['VILLA','APPARTEMENT','STUDIO','T2','T3','DUPLEX','COMMERCE','LOCAL'].includes(this.form.type);
  }

  /** Returns whether the selected type requires landAreaSqm */
  get needsLand(): boolean {
    return ['VILLA','TERRAIN'].includes(this.form.type);
  }

  /** Returns whether the selected type requires bedrooms / bathrooms */
  get needsBedrooms(): boolean {
    return ['VILLA','APPARTEMENT','STUDIO','T2','T3','DUPLEX'].includes(this.form.type);
  }

  /** Returns whether the selected type requires floorNumber */
  get needsFloorNumber(): boolean {
    return ['APPARTEMENT'].includes(this.form.type);
  }

  get canImport(): boolean {
    const r = this.auth.user?.role;
    return r === 'ROLE_ADMIN' || r === 'ROLE_MANAGER';
  }

  /** Alias for readability in template */
  get canCreate(): boolean { return this.canImport; }

  get filtered(): Property[] {
    const q = this.searchQuery.toLowerCase().trim();
    if (!q) return this.properties;
    return this.properties.filter(p =>
      (p.title          ?? '').toLowerCase().includes(q) ||
      p.referenceCode.toLowerCase().includes(q)           ||
      (p.city           ?? '').toLowerCase().includes(q)  ||
      p.type.toLowerCase().includes(q)
    );
  }

  ngOnInit(): void {
    this.loadList();
    this.loadProjects();
  }

  loadProjects(): void {
    this.projectsLoading = true;
    this.projectSvc.list().subscribe({
      next: (data) => { this.projects = data; this.projectsLoading = false; },
      error: () => { this.projectsLoading = false; },
    });
  }

  loadList(): void {
    this.loading = true;
    this.error   = '';
    this.svc.list().subscribe({
      next: (data) => { this.properties = data; this.loading = false; },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        const body = err.error as ErrorResponse | null;
        if      (err.status === 401) this.error = 'Session expired. Please log in again.';
        else if (err.status === 403) this.error = 'Access denied.';
        else                          this.error = body?.message ?? `Failed to load properties (${err.status})`;
      },
    });
  }

  goToDetail(propertyId: string): void {
    this.router.navigate(['/app/properties', propertyId]);
  }

  /* ── CSV Import ─────────────────────────────────────────────── */
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

  /* ── Manual Create ──────────────────────────────────────────── */
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

  closeModal(): void {
    if (this.submitting) return;
    this.showModal = false;
  }

  submitCreate(): void {
    if (!this.form.projectId) {
      this.submitError = 'Le projet est obligatoire.';
      return;
    }
    if (!this.form.type || !this.form.referenceCode.trim() || !this.form.title.trim()) {
      this.submitError = 'Le type, le code de reference et le titre sont obligatoires.';
      return;
    }
    if (this.form.price === null || this.form.price === undefined) {
      this.submitError = 'Le prix est obligatoire.';
      return;
    }
    // Type-specific required field validation
    if (this.needsSurface && !this.form.surfaceAreaSqm) {
      this.submitError = 'La surface (m²) est obligatoire pour ce type de bien.';
      return;
    }
    if (this.needsLand && !this.form.landAreaSqm) {
      this.submitError = 'La superficie du terrain est obligatoire pour ce type de bien.';
      return;
    }
    if (this.needsBedrooms && (!this.form.bedrooms || !this.form.bathrooms)) {
      this.submitError = 'Le nombre de chambres et de salles de bain est obligatoire pour ce type de bien.';
      return;
    }
    if (this.needsFloorNumber && this.form.floorNumber === null) {
      this.submitError = "Le numero d'etage est obligatoire pour un appartement.";
      return;
    }
    this.submitting  = true;
    this.submitError = '';

    this.svc.create({
      projectId:      this.form.projectId,
      type:           this.form.type,
      referenceCode:  this.form.referenceCode.trim(),
      title:          this.form.title.trim(),
      description:    this.form.description.trim() || null,
      price:          this.form.price!,
      currency:       this.form.currency || 'MAD',
      city:           this.form.city.trim() || null,
      region:         this.form.region.trim() || null,
      address:        this.form.address.trim() || null,
      surfaceAreaSqm: this.form.surfaceAreaSqm,
      landAreaSqm:    this.form.landAreaSqm,
      bedrooms:       this.form.bedrooms,
      bathrooms:      this.form.bathrooms,
      floorNumber:    this.form.floorNumber,
      floors:         this.form.floors,
      parkingSpaces:  this.form.parkingSpaces,
      hasPool:        this.form.hasPool || null,
      hasGarden:      this.form.hasGarden || null,
      buildingYear:   this.form.buildingYear,
      buildingName:   this.form.buildingName.trim() || null,
      listedForSale:  this.form.status === 'ACTIVE',
    }).subscribe({
      next: (created) => {
        this.submitting = false;
        this.properties = [created, ...this.properties];
        this.showModal  = false;
      },
      error: (err: HttpErrorResponse) => {
        this.submitting  = false;
        const body = err.error as ErrorResponse | null;
        this.submitError = body?.message ?? `Failed to create property (${err.status})`;
      },
    });
  }

  badgeClass(status: string): string {
    return 'badge badge-' + status.toLowerCase();
  }
}
