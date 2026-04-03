import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Vente } from '../../features/ventes/vente.service';

@Injectable({ providedIn: 'root' })
export class PortalVentesService {
  private http = inject(HttpClient);

  list(): Observable<Vente[]> {
    return this.http.get<Vente[]>('/api/portal/ventes');
  }

  get(id: string): Observable<Vente> {
    return this.http.get<Vente>(`/api/portal/ventes/${id}`);
  }
}
