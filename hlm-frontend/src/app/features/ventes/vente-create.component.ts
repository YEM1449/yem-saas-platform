import { Component, inject, OnInit, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { VenteService, CreateVenteRequest } from './vente.service';
import { ReservationService, VentePrefillData } from '../reservations/reservation.service';
import { MadInputComponent } from '../../core/components/mad-input.component';
import { TranslatePipe } from '@ngx-translate/core';
import { I18nService } from '../../core/i18n/i18n.service';

@Component({
  selector: 'app-vente-create',
  standalone: true,
  imports: [FormsModule, DecimalPipe, RouterLink, MadInputComponent, TranslatePipe],
  templateUrl: './vente-create.component.html',
  styleUrl: './vente-create.component.css',
})
export class VenteCreateComponent implements OnInit {
  private venteSvc       = inject(VenteService);
  private reservationSvc = inject(ReservationService);
  private route          = inject(ActivatedRoute);
  private router         = inject(Router);
  private i18n           = inject(I18nService);

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

  // Stage labels resolved in the template via 'ventes.createPage.stages.<key>'.
  readonly PIPELINE_STAGES = [
    { key: 'COMPROMIS',       color: '#6366f1' },
    { key: 'FINANCEMENT',     color: '#f59e0b' },
    { key: 'ACTE',            color: '#3b82f6' },
    { key: 'LIVRE_DEFINITIF', color: '#10b981' }];

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
          this.i18n.instant('ventes.createPage.loadError')
        );
        this.loading.set(false);
      },
    });
  }

  submit(): void {
    this.createError.set('');
    const p = this.prefill();

    if (!p) {
      this.createError.set(this.i18n.instant('ventes.createPage.noReservation'));
      return;
    }
    if (this.prixVente === null || this.prixVente <= 0) {
      this.createError.set(this.i18n.instant('ventes.createPage.priceError'));
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
          (err.error as { message?: string })?.message
          ?? this.i18n.instant('ventes.create.genericError', { status: err.status })
        );
      },
    });
  }
}
