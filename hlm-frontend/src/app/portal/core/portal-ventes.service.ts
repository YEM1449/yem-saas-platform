import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Vente, VenteDocument } from '../../features/ventes/vente.service';

@Injectable({ providedIn: 'root' })
export class PortalVentesService {
  private http = inject(HttpClient);

  list(): Observable<Vente[]> {
    return this.http.get<Vente[]>('/api/portal/ventes');
  }

  get(id: string): Observable<Vente> {
    return this.http.get<Vente>(`/api/portal/ventes/${id}`);
  }

  downloadDocument(venteId: string, docId: string): Observable<Blob> {
    return this.http.get(
      `/api/portal/ventes/${venteId}/documents/${docId}/download`,
      { responseType: 'blob' }
    );
  }

  uploadDocument(venteId: string, file: File, documentType: string): Observable<VenteDocument> {
    const form = new FormData();
    form.append('file', file);
    form.append('documentType', documentType);
    return this.http.post<VenteDocument>(`/api/portal/ventes/${venteId}/documents`, form);
  }
}
