import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { ImportResult, Property, PropertyMedia } from '../../core/models/property.model';
import { PagedResult } from '../../core/models/page-response.model';

/** Page size used by `list()` to fetch "all" matching rows for bounded callers (#023). */
const ALL_SIZE = 2000;

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
  immeubleId?: string | null;
  buildingName?: string | null;
}

export interface UpdatePropertyRequest {
  title?: string | null;
  description?: string | null;
  notes?: string | null;
  price?: number | null;
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
  immeubleId?: string | null;
  buildingName?: string | null;
}

export interface PropertyListParams {
  projectId?: string;
  immeubleId?: string;
  type?: string;
  status?: string;
}

/** VEFA commercial sheet. prixTtc is computed server-side; logementSocial is write-only (suggests TVA). */
export interface PropertyCommercial {
  propertyId?: string;
  etage?: number | null;
  orientation?: string | null;
  vue?: string | null;
  surfaceHabitable?: number | null;
  surfaceTerrasse?: number | null;
  surfaceCave?: number | null;
  surfaceParking?: number | null;
  parkingInclus?: boolean;
  caveIncluse?: boolean;
  prixHt?: number | null;
  tvaTaux?: number | null;
  prixTtc?: number | null;
  logementSocial?: boolean | null;
  penaliteRetardJournalier?: number | null;
  chargesCoproMensuelles?: number | null;
  planAppartementKey?: string | null;
}

@Injectable({ providedIn: 'root' })
export class PropertyService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;

  /**
   * All matching properties (bounded callers: a building's units, a project's lots, pickers).
   * Backend is paginated (#023); request a large page and unwrap. Use `listPage()` for the
   * main properties page where real pagination is wanted.
   */
  list(params?: PropertyListParams): Observable<Property[]> {
    return this.http.get<PagedResult<Property>>(`${this.apiUrl}/api/properties`,
      { params: { ...this.filterParams(params), size: String(ALL_SIZE) } })
      .pipe(map(r => r.content));
  }

  /** Paginated filtered list for the main properties page (#023). */
  listPage(params: PropertyListParams | undefined, page: number, size: number):
      Observable<PagedResult<Property>> {
    return this.http.get<PagedResult<Property>>(`${this.apiUrl}/api/properties`,
      { params: { ...this.filterParams(params), page: String(page), size: String(size) } });
  }

  private filterParams(params?: PropertyListParams): Record<string, string> {
    const p: Record<string, string> = {};
    if (params?.projectId) p['projectId'] = params.projectId;
    if (params?.immeubleId) p['immeubleId'] = params.immeubleId;
    if (params?.type) p['type'] = params.type;
    if (params?.status) p['status'] = params.status;
    return p;
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

  // ── VEFA commercial sheet (HT/TVA/TTC) ──────────────────────────────────
  getCommercial(id: string): Observable<PropertyCommercial> {
    return this.http.get<PropertyCommercial>(`${this.apiUrl}/api/properties/${id}/commercial`);
  }

  updateCommercial(id: string, req: PropertyCommercial): Observable<PropertyCommercial> {
    return this.http.patch<PropertyCommercial>(`${this.apiUrl}/api/properties/${id}/commercial`, req);
  }

  /**
   * Change editorial status (ADMIN / MANAGER).
   * Only DRAFT / ACTIVE / WITHDRAWN / ARCHIVED are accepted.
   * RESERVED and SOLD are managed by the reservation/contract workflow.
   */
  setStatus(id: string, status: string): Observable<Property> {
    return this.http.patch<Property>(`${this.apiUrl}/api/properties/${id}/status`, { status });
  }

  /** Soft-delete a property (ADMIN only). */
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/api/properties/${id}`);
  }

  /**
   * Apply a single editorial status to multiple properties at once.
   * RESERVED/SOLD cannot be set here. Properties already in those statuses are skipped.
   */
  bulkSetStatus(ids: string[], status: string): Observable<{ updated: number; skipped: number }> {
    return this.http.patch<{ updated: number; skipped: number }>(
      `${this.apiUrl}/api/properties/bulk-status`,
      { ids, status }
    );
  }
}
