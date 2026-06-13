import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface GroupContactRef {
  contactId: string;
  societeId: string;
  societeNom: string;
  nomComplet: string;
  statut: string | null;
  dejaLie: boolean;
}

export interface LinkCandidate {
  cinMasque: string;
  contacts: GroupContactRef[];
}

export interface GroupClient {
  groupePersonneId: string;
  consentGiven: boolean;
  lieLe: string;
  contacts: GroupContactRef[];
}

@Injectable({ providedIn: 'root' })
export class GroupClientService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/groupe/clients`;

  candidates(): Observable<LinkCandidate[]> {
    return this.http.get<LinkCandidate[]>(`${this.base}/candidates`);
  }

  list(): Observable<GroupClient[]> {
    return this.http.get<GroupClient[]>(this.base);
  }

  link(contactIds: string[], consentGiven: boolean): Observable<GroupClient> {
    return this.http.post<GroupClient>(`${this.base}/link`, { contactIds, consentGiven });
  }

  unlink(contactId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${contactId}/link`);
  }
}
