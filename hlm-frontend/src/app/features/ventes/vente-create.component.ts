import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { VenteService, CreateVenteRequest } from './vente.service';
import { ReservationService, VentePrefillData } from '../reservations/reservation.service';

@Component({
  selector: 'app-vente-create',
  standalone: true,
  imports: [CommonModule, FormsModule, DecimalPipe, RouterLink],
  templateUrl: './vente-create.component.html',
  styleUrl: './vente-create.component.css',
})
export class VenteCreateComponent implements OnInit {
  private venteSvc       = inject(VenteService);
  private reservationSvc = inject(ReservationService);
  private route          = inject(ActivatedRoute);
  private router         = inject(Router);

  prefill      = signal<VentePrefillData | null>(null);
  prefillError = signal('');
  loading      = signal(false);

  // Form fields
  prixVente:           number | null = null;
  dateCompromis        = '';
  dateLivraisonPrevue  = '';
  notes                = '';

  creating    = signal(false);
  createError = signal('');

  readonly PIPELINE_STAGES = [
    { key: 'COMPROMIS',    label: 'Compromis',     color: '#6366f1' },
    { key: 'FINANCEMENT',  label: 'Financement',   color: '#f59e0b' },
    { key: 'ACTE_NOTARIE', label: 'Acte notarié',  color: '#3b82f6' },
    { key: 'LIVRE',        label: 'Livraison',      color: '#10b981' },
  ];

  get reservationId(): string | null {
    return this.route.snapshot.queryParamMap.get('reservationId');
  }

  get backUrl(): string[] {
    const id = this.reservationId;
    return id ? ['/app/reservations', id] : ['/app/reservations'];
  }

  get canSubmit(): boolean {
    return (this.prixVente ?? 0) > 0 && this.prixVente !== null;
  }

  /** Remaining balance = prix de vente − avance déjà versée. */
  get remainingBalance(): number | null {
    const p = this.prefill();
    if (this.prixVente === null || this.prixVente <= 0) return null;
    const advance = p?.reservationPrice ?? 0;
    return this.prixVente - Number(advance);
  }

  ngOnInit(): void {
    const resId = this.reservationId;
    if (!resId) return;

    this.loading.set(true);
    this.reservationSvc.getVentePrefill(resId).subscribe({
      next: (data) => {
        this.prefill.set(data);
        this.prixVente = Number(data.suggestedPrixVente ?? data.propertyPrice ?? null) || null;
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.prefillError.set(
          (err.error as { message?: string })?.message ??
          'Impossible de charger la réservation.'
        );
        this.loading.set(false);
      },
    });
  }

  submit(): void {
    this.createError.set('');
    const p = this.prefill();

    if (!p) {
      this.createError.set('Aucune réservation liée.');
      return;
    }
    if (this.prixVente === null || this.prixVente <= 0) {
      this.createError.set('Le prix de vente doit être supérieur à 0.');
      return;
    }

    const req: CreateVenteRequest = {
      reservationId:       p.reservationId,
      prixVente:           this.prixVente,
      dateCompromis:       this.dateCompromis || null,
      dateLivraisonPrevue: this.dateLivraisonPrevue || null,
      notes:               this.notes || null,
    };

    this.creating.set(true);
    this.venteSvc.create(req).subscribe({
      next: (v) => this.router.navigate(['/app/ventes', v.id]),
      error: (err: HttpErrorResponse) => {
        this.creating.set(false);
        this.createError.set(
          (err.error as { message?: string })?.message ?? `Erreur (${err.status})`
        );
      },
    });
  }
}
