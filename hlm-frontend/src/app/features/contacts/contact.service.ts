import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Contact, ContactPage } from '../../core/models/contact.model';

@Injectable({ providedIn: 'root' })
export class ContactService {
  private http = inject(HttpClient);

  list(): Observable<ContactPage> {
    return this.http.get<ContactPage>(`${environment.apiUrl}/api/contacts`);
  }

  getById(id: string): Observable<Contact> {
    return this.http.get<Contact>(`${environment.apiUrl}/api/contacts/${id}`);
  }
}
