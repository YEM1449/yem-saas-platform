import { Component, inject, OnInit } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { ReservationService, Reservation } from './reservation.service';
import { DocumentListComponent } from '../documents/document-list.component';
import { AuthService } from '../../core/auth/auth.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-reservations',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe, DecimalPipe, TranslateModule, DocumentListComponent],
  templateUrl: './reservations.component.html',
  styleUrl: './reservations.component.css',
})
export class ReservationsComponent implements OnInit {
  private svc    = inject(ReservationService);
  private auth   = inject(AuthService);
  private router = inject(Router);
  private http   = inject(HttpClient);

  reservations: Reservation[] = [];
  loading = false;
  error = '';
  actionSuccess = '';
  actionError = '';
  filterStatus = '';

  /** Expanded row tracking: set of reservation IDs whose doc panel is open. */
  expandedDocs = new Set<string>();

  statuses: string[] = ['ACTIVE', 'EXPIRED', 'CANCELLED', 'CONVERTED_TO_DEPOSIT'];

  /** Name lookup maps to avoid UUID display. */
  contactNames  = new Map<string, string>();
  propertyNames = new Map<string, string>();
  namesLoading = false;

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
      next: data => {
        this.reservations = data;
        this.loading = false;
        this.resolveNames(data);
      },
      error: err => {
        this.error = err?.error?.message ?? 'Failed to load reservations.';
        this.loading = false;
      },
    });
  }

  private resolveNames(reservations: Reservation[]): void {
    if (!reservations.length) return;
    this.namesLoading = true;

    // Load contacts
    this.http.get<{ content: Array<{ id: string; fullName: string }> }>(
      `${environment.apiUrl}/api/contacts`
    ).subscribe({
      next: page => {
        (page.content ?? []).forEach(c => this.contactNames.set(c.id, c.fullName));
        this.namesLoading = false;
      },
      error: () => { this.namesLoading = false; },
    });

    // Load properties
    this.http.get<Array<{ id: string; title: string; referenceCode: string }>>(
      `${environment.apiUrl}/api/properties`
    ).subscribe({
      next: ps => ps.forEach(p => {
        this.propertyNames.set(p.id, `${p.title} · ${p.referenceCode}`);
      }),
      error: () => {},
    });
  }

  openConvertToVente(r: Reservation): void {
    this.router.navigate(['/app/ventes/new'], { queryParams: { reservationId: r.id } });
  }

  contactName(id: string): string {
    return this.contactNames.get(id) ?? id.substring(0, 8).toUpperCase();
  }

  propertyName(id: string): string {
    return this.propertyNames.get(id) ?? id.substring(0, 8).toUpperCase();
  }

  toggleDocs(id: string): void {
    if (this.expandedDocs.has(id)) this.expandedDocs.delete(id);
    else this.expandedDocs.add(id);
  }

  isDocExpanded(id: string): boolean {
    return this.expandedDocs.has(id);
  }

  cancel(r: Reservation): void {
    if (!confirm(`Annuler la réservation pour ${this.propertyName(r.propertyId)} ?`)) return;
    this.actionSuccess = '';
    this.actionError = '';
    this.svc.cancel(r.id).subscribe({
      next: () => { this.actionSuccess = 'Réservation annulée.'; this.load(); },
      error: err => { this.actionError = err?.error?.message ?? 'Annulation échouée.'; },
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

  copiedRef: string | null = null;

  copyRef(ref: string): void {
    navigator.clipboard.writeText(ref).then(() => {
      this.copiedRef = ref;
      setTimeout(() => { this.copiedRef = null; }, 2000);
    });
  }
}
