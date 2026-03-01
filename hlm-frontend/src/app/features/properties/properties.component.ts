import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { PropertyService } from './property.service';
import { ImportResult, Property } from '../../core/models/property.model';
import { ErrorResponse } from '../../core/models/error-response.model';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-properties',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './properties.component.html',
  styleUrl: './properties.component.css',
})
export class PropertiesComponent implements OnInit {
  private svc = inject(PropertyService);
  private router = inject(Router);
  private auth = inject(AuthService);

  properties: Property[] = [];
  loading = true;
  error = '';

  importLoading = false;
  importResult: ImportResult | null = null;
  importError = '';

  get canImport(): boolean {
    const role = this.auth.user?.role;
    return role === 'ROLE_ADMIN' || role === 'ROLE_MANAGER';
  }

  ngOnInit(): void {
    this.svc.list().subscribe({
      next: (data) => {
        this.properties = data;
        this.loading = false;
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        const body = err.error as ErrorResponse | null;
        if (err.status === 401) {
          this.error = 'Session expired. Please log in again.';
        } else if (err.status === 403) {
          this.error = 'Access denied. Your role does not permit viewing properties.';
        } else if (body?.message) {
          this.error = body.message;
        } else {
          this.error = `Failed to load properties (${err.status})`;
        }
      },
    });
  }

  goToDetail(propertyId: string): void {
    this.router.navigate(['/app/properties', propertyId]);
  }

  onImportFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    this.importLoading = true;
    this.importResult = null;
    this.importError = '';

    this.svc.importCsv(file).subscribe({
      next: (result) => {
        this.importResult = result;
        this.importLoading = false;
        input.value = '';
        if (result.errors.length === 0) {
          // Reload the list after successful import
          this.svc.list().subscribe({ next: (data) => (this.properties = data) });
        }
      },
      error: (err: HttpErrorResponse) => {
        this.importLoading = false;
        const body = err.error as ErrorResponse | null;
        // 422 carries ImportResult with row errors in the response body
        if (err.status === 422 && err.error?.imported !== undefined) {
          this.importResult = err.error as ImportResult;
        } else {
          this.importError = body?.message ?? `Import failed (${err.status})`;
        }
        input.value = '';
      },
    });
  }
}
