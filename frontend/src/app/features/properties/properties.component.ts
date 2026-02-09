import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { PropertyService } from './property.service';
import { Property } from '../../core/models/property.model';
import { ErrorResponse } from '../../core/models/error-response.model';

@Component({
  selector: 'app-properties',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './properties.component.html',
  styleUrl: './properties.component.css',
})
export class PropertiesComponent implements OnInit {
  private svc = inject(PropertyService);

  properties: Property[] = [];
  loading = true;
  error = '';

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
}
