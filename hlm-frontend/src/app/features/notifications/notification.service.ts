import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Notification } from '../../core/models/notification.model';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private http = inject(HttpClient);

  list(read?: boolean, size: number = 50): Observable<Notification[]> {
    let params = new HttpParams().set('size', size);
    if (read !== undefined) {
      params = params.set('read', read);
    }
    return this.http.get<Notification[]>(
      `${environment.apiUrl}/api/notifications`,
      { params }
    );
  }

  markRead(id: string): Observable<Notification> {
    return this.http.post<Notification>(
      `${environment.apiUrl}/api/notifications/${id}/read`,
      {}
    );
  }
}
