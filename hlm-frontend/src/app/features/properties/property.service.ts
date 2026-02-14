import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Property } from '../../core/models/property.model';

@Injectable({ providedIn: 'root' })
export class PropertyService {
  private http = inject(HttpClient);

  list(): Observable<Property[]> {
    return this.http.get<Property[]>(`${environment.apiUrl}/api/properties`);
  }
}
