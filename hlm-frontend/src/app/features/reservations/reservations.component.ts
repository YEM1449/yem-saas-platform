import { Component, inject, OnInit } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { ReservationService, Reservation } from './reservation.service';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-reservations',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe, DecimalPipe, TranslateModule],
  templateUrl: './reservations.component.html',
  styleUrl: './reservations.component.css',
})
export class ReservationsComponent implements OnInit {
  private svc = inject(ReservationService);
  private auth = inject(AuthService);

  reservations: Reservation[] = [];
  loading = false;
  error = '';
  actionSuccess = '';
  actionError = '';
  filterStatus = '';

  statuses: string[] = ['ACTIVE', 'EXPIRED', 'CANCELLED', 'CONVERTED_TO_DEPOSIT'];

  get canWrite(): boolean {
    const r = this.auth.user?.role;
    return r === 'ROLE_ADMIN' || r === 'ROLE_MANAGER';
  }

  get filtered(): Reservation[] {
    if (!this.filterStatus) return this.reservations;
    return this.reservations.filter(r => r.status === this.filterStatus);
  }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.error = '';
    this.svc.list().subscribe({
      next: data => { this.reservations = data; this.loading = false; },
      error: err => {
        this.error = err?.error?.message ?? 'Failed to load reservations.';
        this.loading = false;
      },
    });
  }

  cancel(r: Reservation): void {
    if (!confirm(`Cancel reservation for property ${r.propertyId.substring(0, 8).toUpperCase()}?`)) return;
    this.actionSuccess = '';
    this.actionError = '';
    this.svc.cancel(r.id).subscribe({
      next: () => { this.actionSuccess = 'Reservation cancelled.'; this.load(); },
      error: err => { this.actionError = err?.error?.message ?? 'Cancel failed.'; },
    });
  }

  badgeClass(status: string): string {
    return 'badge badge-' + status.toLowerCase().replace(/_/g, '-');
  }

  isExpiringSoon(r: Reservation): boolean {
    if (r.status !== 'ACTIVE') return false;
    const diff = new Date(r.expiryDate).getTime() - Date.now();
    return diff > 0 && diff < 48 * 60 * 60 * 1000;
  }
}
