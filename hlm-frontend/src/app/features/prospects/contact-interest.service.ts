import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ContactInterest } from '../../core/models/contact-interest.model';

@Injectable({ providedIn: 'root' })
export class ContactInterestService {
  private http = inject(HttpClient);

  list(contactId: string): Observable<ContactInterest[]> {
    return this.http.get<ContactInterest[]>(
      `${environment.apiUrl}/api/contacts/${contactId}/interests`
    );
  }

  add(contactId: string, propertyId: string): Observable<void> {
    return this.http.post<void>(
      `${environment.apiUrl}/api/contacts/${contactId}/interests`,
      { propertyId }
    );
  }

  remove(contactId: string, propertyId: string): Observable<void> {
    return this.http.delete<void>(
      `${environment.apiUrl}/api/contacts/${contactId}/interests/${propertyId}`
    );
  }
}
