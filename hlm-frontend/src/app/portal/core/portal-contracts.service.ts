import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PortalContract, PortalProperty } from '../../core/models/portal.model';

@Injectable({ providedIn: 'root' })
export class PortalContractsService {
  private http = inject(HttpClient);

  listContracts(): Observable<PortalContract[]> {
    return this.http.get<PortalContract[]>('/api/portal/contracts');
  }

  downloadContractPdf(venteId: string, docId: string): Observable<Blob> {
    return this.http.get(`/api/portal/ventes/${venteId}/documents/${docId}/download`, { responseType: 'blob' });
  }

  getProperty(propertyId: string): Observable<PortalProperty> {
    return this.http.get<PortalProperty>(`/api/portal/properties/${propertyId}`);
  }
}
