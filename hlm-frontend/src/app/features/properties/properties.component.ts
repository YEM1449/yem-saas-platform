import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { PropertyService } from './property.service';
import { ImportResult, Property } from '../../core/models/property.model';
import { ErrorResponse } from '../../core/models/error-response.model';
import { AuthService } from '../../core/auth/auth.service';

interface CreatePropertyForm {
  type: string;
  referenceCode: string;
  title: string;
  price: number | null;
  city: string;
  surfaceAreaSqm: number | null;
  bedrooms: number | null;
  description: string;
  status: string;
}

@Component({
  selector: 'app-properties',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './properties.component.html',
  styleUrl: './properties.component.css',
})
export class PropertiesComponent implements OnInit {
  private svc    = inject(PropertyService);
  private router = inject(Router);
  private auth   = inject(AuthService);

  properties: Property[] = [];
  loading = true;
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
    'APARTMENT', 'VILLA', 'HOUSE', 'STUDIO', 'DUPLEX',
    'PENTHOUSE', 'LAND', 'OFFICE', 'COMMERCIAL', 'OTHER',
  ];

  readonly statusOptions = [
    { value: 'DRAFT',  label: 'Draft (not yet on market)' },
    { value: 'ACTIVE', label: 'Active (available for reservation)' },
  ];

  form: CreatePropertyForm = {
    type: '', referenceCode: '', title: '',
    price: null, city: '', surfaceAreaSqm: null, bedrooms: null,
    description: '', status: 'DRAFT',
  };

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
      type: '', referenceCode: '', title: '',
      price: null, city: '', surfaceAreaSqm: null, bedrooms: null,
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
    if (!this.form.type || !this.form.referenceCode.trim() || !this.form.title.trim()) {
      this.submitError = 'Type, reference code and title are required.';
      return;
    }
    this.submitting  = true;
    this.submitError = '';

    this.svc.create({
      type:          this.form.type,
      referenceCode: this.form.referenceCode.trim(),
      title:         this.form.title.trim(),
      description:   this.form.description.trim() || null,
      price:         this.form.price,
      city:          this.form.city.trim() || null,
      surfaceAreaSqm: this.form.surfaceAreaSqm,
      bedrooms:      this.form.bedrooms,
      status:        this.form.status,
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
        this.submitError = body?.message ?? `Failed to create property (${err.status})`;
      },
    });
  }

  badgeClass(status: string): string {
    return 'badge badge-' + status.toLowerCase();
  }
}
