import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  MessageChannel,
  MessageStatus,
  OutboundMessagePage,
  SendMessageRequest,
  SendMessageResponse,
} from '../../core/models/outbox.model';

export interface ListParams {
  channel?: MessageChannel;
  status?: MessageStatus;
  contactId?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class OutboxService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/messages`;

  send(req: SendMessageRequest): Observable<SendMessageResponse> {
    return this.http.post<SendMessageResponse>(this.base, req);
  }

  list(params: ListParams = {}): Observable<OutboundMessagePage> {
    let p = new HttpParams();
    for (const [k, v] of Object.entries(params)) {
      if (v != null && v !== '') p = p.set(k, String(v));
    }
    return this.http.get<OutboundMessagePage>(this.base, { params: p });
  }
}
