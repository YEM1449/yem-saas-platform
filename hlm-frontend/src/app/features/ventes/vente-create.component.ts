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
import { VisiteApiService } from '../../modules/visites/services/visite-api.service';
import { ContactService } from '../contacts/contact.service';
import { PropertyService } from '../properties/property.service';

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
  private visiteApi      = inject(VisiteApiService);
  private contactSvc     = inject(ContactService);
  private propertySvc    = inject(PropertyService);
  private route          = inject(ActivatedRoute);
  private router         = inject(Router);
  private i18n           = inject(I18nService);

  prefill      = signal<VentePrefillData | null>(null);
  prefillError = signal('');
  loading      = signal(false);

  // Visite-origin mode (Wave 16 P5-T2): create a vente straight from a visite's
  // OPPORTUNITE_CREEE outcome, with no reservation. The created vente is linked back.
  visiteOrigin       = signal(false);
  visiteId           = signal<string | null>(null);
  originContactId    = signal<string | null>(null);
  originPropertyId   = signal<string | null>(null);
  originContactName  = signal<string>('');
  originPropertyLabel = signal<string>('');

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
    if (this.visiteOrigin() && this.visiteId()) return ['/app/visites', this.visiteId()!];
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
    if (!resId) {
      this.initVisiteOrigin();
      return;
    }

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

  /** Read visite-origin query params and resolve contact/property names for display (no UUIDs). */
  private initVisiteOrigin(): void {
    const qp = this.route.snapshot.queryParamMap;
    const visiteId = qp.get('visiteId');
    const contactId = qp.get('contactId');
    if (!visiteId || !contactId) return;

    this.visiteOrigin.set(true);
    this.visiteId.set(visiteId);
    this.originContactId.set(contactId);
    this.originPropertyId.set(qp.get('propertyId'));

    this.contactSvc.getById(contactId).subscribe({
      next: (c) => this.originContactName.set(c.fullName ?? `${c.firstName} ${c.lastName}`.trim()),
      error: () => {},
    });
    const propId = qp.get('propertyId');
    if (propId) {
      this.propertySvc.getById(propId).subscribe({
        next: (p) => this.originPropertyLabel.set(`${p.referenceCode} — ${p.title}`),
        error: () => {},
      });
    }
  }

  submit(): void {
    this.createError.set('');
    if (this.prixVente === null || this.prixVente <= 0) {
      this.createError.set(this.i18n.instant('ventes.createPage.priceError'));
      return;
    }

    // Visite-origin path: no reservation; create from contact/property then link the visite.
    if (this.visiteOrigin()) {
      const req: CreateVenteRequest = {
        contactId:           this.originContactId(),
        propertyId:          this.originPropertyId(),
        prixVente:           this.prixVente,
        dateCompromis:       this.dateCompromis || null,
        dateLivraisonPrevue: this.dateLivraisonPrevue || null,
        notes:               this.notes || null,
      };
      this.creating.set(true);
      this.venteSvc.create(req).subscribe({
        next: (v) => this.linkVisiteThenNavigate(v.id),
        error: (err: HttpErrorResponse) => this.onCreateError(err),
      });
      return;
    }

    const p = this.prefill();
    if (!p) {
      this.createError.set(this.i18n.instant('ventes.createPage.noReservation'));
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
      error: (err: HttpErrorResponse) => this.onCreateError(err),
    });
  }

  /** Link the new vente back to the originating visite (best-effort), then go to the vente. */
  private linkVisiteThenNavigate(venteId: string): void {
    const visiteId = this.visiteId();
    if (!visiteId) { this.router.navigate(['/app/ventes', venteId]); return; }
    this.visiteApi.lierVente(visiteId, venteId).subscribe({
      next: () => this.router.navigate(['/app/ventes', venteId]),
      error: () => this.router.navigate(['/app/ventes', venteId]), // link failure shouldn't block
    });
  }

  private onCreateError(err: HttpErrorResponse): void {
    this.creating.set(false);
    this.createError.set(
      (err.error as { message?: string })?.message
      ?? this.i18n.instant('ventes.create.genericError', { status: err.status })
    );
  }
}
