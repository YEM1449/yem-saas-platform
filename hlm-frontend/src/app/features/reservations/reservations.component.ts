import { Component, inject, OnInit } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { ReservationService, Reservation } from './reservation.service';
import { DocumentListComponent } from '../documents/document-list.component';
import { AuthService } from '../../core/auth/auth.service';
import { VenteService } from '../ventes/vente.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-reservations',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe, DecimalPipe, TranslateModule, DocumentListComponent],
  templateUrl: './reservations.component.html',
  styleUrl: './reservations.component.css',
})
export class ReservationsComponent implements OnInit {
  private svc      = inject(ReservationService);
  private venteSvc = inject(VenteService);
  private auth     = inject(AuthService);
  private router   = inject(Router);
  private http     = inject(HttpClient);

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
  /** Price lookup by propertyId — used in conversion price preview. */
  propertyPrices = new Map<string, number>();
  namesLoading = false;

  // ── Convert to Vente modal ────────────────────────────────────────────────
  showConvertModal    = false;
  convertingResv: Reservation | null = null;
  convertReduction: number | null = null;
  convertDateCompromis = '';
  convertNotes        = '';
  converting          = false;
  convertError        = '';

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

    // Load properties (also capture price for conversion preview)
    this.http.get<Array<{ id: string; title: string; referenceCode: string; price: number | null }>>(
      `${environment.apiUrl}/api/properties`
    ).subscribe({
      next: ps => ps.forEach(p => {
        this.propertyNames.set(p.id, `${p.title} · ${p.referenceCode}`);
        if (p.price != null) this.propertyPrices.set(p.id, p.price);
      }),
      error: () => {},
    });
  }

  /** Estimated conversion price = property price − advance − reduction. */
  estimatedConversionPrice(): number | null {
    if (!this.convertingResv) return null;
    const base    = this.propertyPrices.get(this.convertingResv.propertyId) ?? null;
    if (base == null) return null;
    const advance = this.convertingResv.reservationPrice ?? 0;
    const reduc   = this.convertReduction ?? 0;
    return base - advance - reduc;
  }

  openConvertToVente(r: Reservation): void {
    this.convertingResv     = r;
    this.convertReduction   = null;
    this.convertDateCompromis = new Date().toISOString().substring(0, 10);
    this.convertNotes       = '';
    this.convertError       = '';
    this.showConvertModal   = true;
  }

  closeConvertModal(): void {
    this.showConvertModal = false;
    this.convertingResv   = null;
    this.convertError     = '';
  }

  submitConvertToVente(): void {
    if (!this.convertingResv) return;
    const estimated = this.estimatedConversionPrice();
    if (estimated !== null && estimated <= 0) {
      this.convertError = 'Le prix calculé est nul ou négatif. Veuillez ajuster la réduction.';
      return;
    }
    this.converting   = true;
    this.convertError = '';
    this.venteSvc.create({
      reservationId:   this.convertingResv.id,
      reduction:       this.convertReduction ?? undefined,
      dateCompromis:   this.convertDateCompromis || undefined,
      notes:           this.convertNotes || undefined,
    }).subscribe({
      next: vente => {
        this.converting       = false;
        this.showConvertModal  = false;
        this.actionSuccess     = `Vente créée avec succès. Prix : ${vente.prixVente?.toLocaleString('fr-FR')} MAD`;
        this.load(); // refresh list — reservation status now CONVERTED_TO_DEPOSIT
        this.router.navigate(['/app/ventes', vente.id]);
      },
      error: err => {
        this.converting   = false;
        this.convertError = err?.error?.message ?? 'Erreur lors de la conversion.';
      },
    });
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
}
