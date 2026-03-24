import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DocumentResponse, DocumentEntityType } from './document.model';

@Injectable({ providedIn: 'root' })
export class DocumentService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/documents`;

  list(entityType: DocumentEntityType, entityId: string): Observable<DocumentResponse[]> {
    const params = new HttpParams()
      .set('entityType', entityType)
      .set('entityId', entityId);
    return this.http.get<DocumentResponse[]>(this.base, { params });
  }

  upload(entityType: DocumentEntityType, entityId: string, file: File, description?: string): Observable<DocumentResponse> {
    const params = new HttpParams()
      .set('entityType', entityType)
      .set('entityId', entityId);
    const form = new FormData();
    form.append('file', file);
    if (description) form.append('description', description);
    return this.http.post<DocumentResponse>(this.base, form, { params });
  }

  downloadUrl(id: string): string {
    return `${environment.apiUrl}/api/documents/${id}/download`;
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
