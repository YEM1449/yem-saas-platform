import { Injectable, NgZone } from '@angular/core';
import { Observable } from 'rxjs';

/**
 * Client-side SSE service for real-time dashboard refresh signals.
 *
 * Usage:
 *   this.dashboardEvents.connect().subscribe(event => this.reload());
 *
 * Note: EventSource does not support custom headers, so JWT auth cannot be
 * passed via Authorization header. The endpoint relies on the existing
 * server-side session. Token-in-query-param auth may be added in a future
 * iteration once backend supports it.
 */
@Injectable({ providedIn: 'root' })
export class DashboardEventsService {
  constructor(private ngZone: NgZone) {}

  connect(): Observable<MessageEvent> {
    return new Observable(observer => {
      const source = new EventSource(
        `/api/dashboard/commercial/events?sessionId=${Date.now()}`
      );

      source.addEventListener('dashboard-refresh', (event) => {
        this.ngZone.run(() => observer.next(event as MessageEvent));
      });

      source.onerror = () => {
        // Close on error; caller can retry by re-subscribing
        this.ngZone.run(() => observer.complete());
        source.close();
      };

      return () => source.close();
    });
  }
}
