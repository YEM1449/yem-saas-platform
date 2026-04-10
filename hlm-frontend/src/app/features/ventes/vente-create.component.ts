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
  prixVente:          number | null = null;
  dateCompromis       = '';
  dateLivraisonPrevue = '';
  notes               = '';

  creating    = signal(false);
  createError = signal('');

  get reservationId(): string | null {
    return this.route.snapshot.queryParamMap.get('reservationId');
  }

  /** True when the form can be submitted. */
  get canSubmit(): boolean {
    return (this.prixVente ?? 0) >= 0 && this.prixVente !== null;
  }

  ngOnInit(): void {
    const resId = this.reservationId;
    if (!resId) return;

    this.loading.set(true);
    this.reservationSvc.getVentePrefill(resId).subscribe({
      next: (data) => {
        this.prefill.set(data);
        // Pre-fill with suggested price (propertyPrice − advance); user can override
        this.prixVente = data.suggestedPrixVente ?? data.propertyPrice ?? null;
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
      this.createError.set('Aucune réservation liée — utilisez le formulaire de la liste des ventes.');
      return;
    }

    if (this.prixVente === null) {
      this.createError.set('Le prix de vente est obligatoire.');
      return;
    }

    const req: CreateVenteRequest = {
      reservationId:      p.reservationId,
      prixVente:          this.prixVente,
      dateCompromis:      this.dateCompromis || null,
      dateLivraisonPrevue: this.dateLivraisonPrevue || null,
      notes:              this.notes || null,
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
