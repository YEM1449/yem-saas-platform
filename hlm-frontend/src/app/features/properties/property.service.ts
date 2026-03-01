import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ImportResult, Property, PropertyMedia } from '../../core/models/property.model';

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
}
