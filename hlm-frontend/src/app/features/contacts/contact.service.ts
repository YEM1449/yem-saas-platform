import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Contact, ContactPage, TimelineEvent } from '../../core/models/contact.model';

export interface CreateContactRequest {
  firstName: string;
  lastName: string;
  phone?: string | null;
  email?: string | null;
  nationalId?: string | null;
  address?: string | null;
  notes?: string | null;
  // GDPR / Law 09-08 consent fields
  consentGiven?: boolean | null;
  consentMethod?: string | null;
  processingBasis?: string | null;
}

@Injectable({ providedIn: 'root' })
export class ContactService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;

  list(): Observable<ContactPage> {
    return this.http.get<ContactPage>(`${this.apiUrl}/api/contacts`);
  }

  getById(id: string): Observable<Contact> {
    return this.http.get<Contact>(`${this.apiUrl}/api/contacts/${id}`);
  }

  getTimeline(id: string, limit = 50): Observable<TimelineEvent[]> {
    return this.http.get<TimelineEvent[]>(
      `${this.apiUrl}/api/contacts/${id}/timeline?limit=${limit}`
    );
  }

  /** Create a new contact (ADMIN / MANAGER only). */
  create(req: CreateContactRequest): Observable<Contact> {
    return this.http.post<Contact>(`${this.apiUrl}/api/contacts`, req);
  }
}
