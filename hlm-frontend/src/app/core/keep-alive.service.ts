import { Injectable, inject, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';

/**
 * Periodically pings the backend health endpoint to prevent the Render free-tier
 * dyno from sleeping during active user sessions. The ping fires every 8 minutes
 * (Render spins down after 15 minutes of inactivity) and silently ignores errors.
 */
@Injectable({ providedIn: 'root' })
export class KeepAliveService implements OnDestroy {
  private http = inject(HttpClient);
  private intervalId: ReturnType<typeof setInterval> | null = null;

  private static readonly INTERVAL_MS = 8 * 60 * 1000; // 8 minutes

  start(): void {
    if (this.intervalId) return;
    this.intervalId = setInterval(() => this.ping(), KeepAliveService.INTERVAL_MS);
  }

  stop(): void {
    if (this.intervalId) {
      clearInterval(this.intervalId);
      this.intervalId = null;
    }
  }

  ngOnDestroy(): void {
    this.stop();
  }

  private ping(): void {
    this.http.get(`${environment.apiUrl}/actuator/health`, { responseType: 'text' })
      .subscribe({ error: () => {} });
  }
}
