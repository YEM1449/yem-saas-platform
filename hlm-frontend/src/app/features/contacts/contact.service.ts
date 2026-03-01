import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Contact, ContactPage, TimelineEvent } from '../../core/models/contact.model';

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
}
