import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ImportResult, Property, PropertyMedia } from '../../core/models/property.model';

export interface CreatePropertyRequest {
  type: string;
  referenceCode: string;
  title: string;
  description?: string | null;
  price?: number | null;
  city?: string | null;
  address?: string | null;
  region?: string | null;
  surfaceAreaSqm?: number | null;
  bedrooms?: number | null;
  bathrooms?: number | null;
  projectId?: string | null;
  status?: string;
  listedForSale?: boolean;
}

@Injectable({ providedIn: 'root' })
export class PropertyService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;

  list(): Observable<Property[]> {
    return this.http.get<Property[]>(`${this.apiUrl}/api/properties`);
  }

  listMedia(propertyId: string): Observable<PropertyMedia[]> {
    return this.http.get<PropertyMedia[]>(`${this.apiUrl}/api/properties/${propertyId}/media`);
  }

  uploadMedia(propertyId: string, file: File): Observable<PropertyMedia> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<PropertyMedia>(`${this.apiUrl}/api/properties/${propertyId}/media`, form);
  }

  deleteMedia(mediaId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/api/media/${mediaId}`);
  }

  downloadMediaUrl(mediaId: string): string {
    return `${this.apiUrl}/api/media/${mediaId}/download`;
  }

  importCsv(file: File): Observable<ImportResult> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ImportResult>(`${this.apiUrl}/api/properties/import`, form);
  }

  /** Create a single property manually (ADMIN / MANAGER only). */
  create(req: CreatePropertyRequest): Observable<Property> {
    return this.http.post<Property>(`${this.apiUrl}/api/properties`, req);
  }
}
