import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Prospect, ProspectPage } from '../../core/models/prospect.model';

@Injectable({ providedIn: 'root' })
export class ProspectService {
  private http = inject(HttpClient);

  list(): Observable<ProspectPage> {
    const params = new HttpParams()
      .append('contactType', 'PROSPECT')
      .append('contactType', 'TEMP_CLIENT');
    return this.http.get<ProspectPage>(`${environment.apiUrl}/api/contacts`, { params });
  }

  getById(id: string): Observable<Prospect> {
    return this.http.get<Prospect>(`${environment.apiUrl}/api/contacts/${id}`);
  }

  updateStatus(id: string, status: string): Observable<Prospect> {
    return this.http.patch<Prospect>(
      `${environment.apiUrl}/api/contacts/${id}/status`,
      { status }
    );
  }
}
