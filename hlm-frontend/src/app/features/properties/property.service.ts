import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ImportResult, Property, PropertyMedia } from '../../core/models/property.model';

export interface CreatePropertyRequest {
  type: string;
  title: string;
  referenceCode: string;
  price: number;
  currency?: string | null;
  commissionRate?: number | null;
  estimatedValue?: number | null;
  address?: string | null;
  city?: string | null;
  region?: string | null;
  postalCode?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  titleDeedNumber?: string | null;
  cadastralReference?: string | null;
  ownerName?: string | null;
  legalStatus?: string | null;
  surfaceAreaSqm?: number | null;
  landAreaSqm?: number | null;
  bedrooms?: number | null;
  bathrooms?: number | null;
  floors?: number | null;
  parkingSpaces?: number | null;
  hasGarden?: boolean | null;
  hasPool?: boolean | null;
  buildingYear?: number | null;
  floorNumber?: number | null;
  zoning?: string | null;
  isServiced?: boolean | null;
  description?: string | null;
  notes?: string | null;
  listedForSale?: boolean | null;
  projectId: string;
  buildingName?: string | null;
}

export interface UpdatePropertyRequest {
  title?: string | null;
  description?: string | null;
  notes?: string | null;
  price?: number | null;
  status?: string | null;
  address?: string | null;
  city?: string | null;
  region?: string | null;
  postalCode?: string | null;
  legalStatus?: string | null;
  surfaceAreaSqm?: number | null;
  landAreaSqm?: number | null;
  bedrooms?: number | null;
  bathrooms?: number | null;
  floors?: number | null;
  parkingSpaces?: number | null;
  hasGarden?: boolean | null;
  hasPool?: boolean | null;
  buildingYear?: number | null;
  floorNumber?: number | null;
  zoning?: string | null;
  isServiced?: boolean | null;
  listedForSale?: boolean | null;
  projectId?: string | null;
  buildingName?: string | null;
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

  /** Get a single property by ID. */
  getById(id: string): Observable<Property> {
    return this.http.get<Property>(`${this.apiUrl}/api/properties/${id}`);
  }

  /** Update an existing property (ADMIN / MANAGER only). */
  update(id: string, req: UpdatePropertyRequest): Observable<Property> {
    return this.http.put<Property>(`${this.apiUrl}/api/properties/${id}`, req);
  }

  /** Soft-delete a property (ADMIN only). */
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/api/properties/${id}`);
  }
}
