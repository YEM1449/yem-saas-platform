import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { ContactService, ConvertToProspectRequest, UpdateContactRequest } from './contact.service';
import { AuthService } from '../../core/auth/auth.service';
import { Contact, TimelineEvent } from '../../core/models/contact.model';
import { ContactInterest } from '../../core/models/contact-interest.model';
import { Deposit } from '../../core/models/deposit.model';
import { Property } from '../../core/models/property.model';
import { ErrorResponse } from '../../core/models/error-response.model';
import { DepositService, CreateDepositRequest } from '../prospects/deposit.service';
import { PropertyService } from '../properties/property.service';
import { Reservation, ReservationService, CreateReservationRequest } from '../reservations/reservation.service';
import { VenteService, Vente, VenteDocument } from '../ventes/vente.service';
import { DocumentListComponent } from '../documents/document-list.component';
import { ContactTasksComponent } from '../tasks/contact-tasks.component';

type TabId = 'details' | 'interests' | 'reservations' | 'deposits' | 'ventes' | 'timeline' | 'documents' | 'tasks';

@Component({
  selector: 'app-contact-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, TranslateModule, DocumentListComponent, ContactTasksComponent, DecimalPipe, DatePipe],
  templateUrl: './contact-detail.component.html',
  styleUrl: './contact-detail.component.css',
})
export class ContactDetailComponent implements OnInit {
  private svc          = inject(ContactService);
  private depositSvc   = inject(DepositService);
  private propertySvc  = inject(PropertyService);
  private reservSvc    = inject(ReservationService);
  private venteSvc     = inject(VenteService);
  private route        = inject(ActivatedRoute);
  private router       = inject(Router);
  private auth         = inject(AuthService);

  get canWrite(): boolean {
    const r = this.auth.user?.role;
    return r === 'ROLE_ADMIN' || r === 'ROLE_MANAGER';
  }

  contact: Contact | null = null;
  loading = true;
  error = '';
  contactId = '';

  activeTab: TabId = 'details';

  // ── Edit form ──────────────────────────────────────────────────────────────
  showEditForm = false;
  savingEdit = false;
  editError = '';
  editForm: UpdateContactRequest = {
    firstName: '', lastName: '', email: '', phone: '',
    nationalId: '', address: '', notes: '',
  };

  // ── Qualify form ───────────────────────────────────────────────────────────
  showQualifyForm = false;
  qualifySource = '';
  qualifyBudgetMin: number | null = null;
  qualifyBudgetMax: number | null = null;
  qualifyNotes = '';
  qualifying = false;
  qualifyError = '';

  // ── Status transition ──────────────────────────────────────────────────────
  updatingStatus = false;
  statusError = '';

  // ── Interests ──────────────────────────────────────────────────────────────
  interests: ContactInterest[] = [];
  interestsLoading = false;
  interestError = '';
  interestsLoaded = false;

  // ── Properties (shared across interests / reservation / deposit forms) ─────
  properties: Property[] = [];
  propertiesLoading = false;
  propertiesError = '';
  propertiesLoaded = false;

  // ── Add interest form ──────────────────────────────────────────────────────
  selectedPropertyId = '';
  addingInterest = false;
  removingPropertyId = '';

  // ── Reservations ───────────────────────────────────────────────────────────
  reservations: Reservation[] = [];
  reservationsLoading = false;
  reservationsLoaded = false;
  reservationError = '';
  reservationSuccess = '';
  reservationPropertyId = '';
  reservationPrice: number | null = null;
  reservationNotes = '';
  /** IDs of reservation rows whose document panel is expanded. */
  expandedReservationDocs = new Set<string>();
  creatingReservation = false;
  /** Reservation → Vente direct conversion. */
  convertingReservationId: string | null = null;
  convertReservationError = '';

  // ── Deposits ───────────────────────────────────────────────────────────────
  deposits: Deposit[] = [];
  depositsLoading = false;
  depositsLoaded = false;
  depositError = '';
  depositSuccess = '';
  depositPropertyId = '';
  depositAmount: number | null = null;
  depositNotes = '';
  creatingDeposit = false;
  actionDepositId: string | null = null;
  convertingDepositId: string | null = null;

  // ── Ventes ─────────────────────────────────────────────────────────────────
  ventes: Vente[] = [];
  ventesLoading = false;
  ventesLoaded = false;
  ventesError = '';

  // ── Portal documents (from buyer portal uploads) ───────────────────────────
  portalDocs = signal<Array<{venteName: string; doc: VenteDocument}>>([]);
  portalDocsLoaded = false;

  // ── Timeline ───────────────────────────────────────────────────────────────
  timeline: TimelineEvent[] = [];
  timelineLoading = false;
  timelineError = '';
  timelineLoaded = false;

  // ── Status machine helper ──────────────────────────────────────────────────
  readonly STATUS_LABELS: Record<string, string> = {
    PROSPECT:           'Prospect',
    QUALIFIED_PROSPECT: 'Prospect qualifié',
    CLIENT:             'Client',
    ACTIVE_CLIENT:      'Client actif',
    COMPLETED_CLIENT:   'Client finalisé',
    REFERRAL:           'Référent',
    LOST:               'Perdu',
  };

  readonly ALLOWED_TRANSITIONS: Record<string, string[]> = {
    PROSPECT:           ['QUALIFIED_PROSPECT', 'LOST'],
    QUALIFIED_PROSPECT: ['PROSPECT', 'CLIENT', 'LOST'],
    CLIENT:             ['ACTIVE_CLIENT', 'COMPLETED_CLIENT', 'LOST'],
    ACTIVE_CLIENT:      ['COMPLETED_CLIENT', 'LOST'],
    COMPLETED_CLIENT:   ['REFERRAL'],
    REFERRAL:           [],
    LOST:               ['PROSPECT'],
  };

  ngOnInit(): void {
    this.contactId = this.route.snapshot.paramMap.get('id')!;
    this.loadContact();
  }

  private loadContact(): void {
    this.loading = true;
    this.error = '';
    this.svc.getById(this.contactId).subscribe({
      next: (data) => { this.contact = data; this.loading = false; },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        const body = err.error as ErrorResponse | null;
        if (err.status === 404) this.error = 'Contact introuvable.';
        else if (err.status === 401) this.error = 'Session expirée.';
        else this.error = body?.message ?? `Erreur (${err.status})`;
      },
    });
  }

  selectTab(tab: TabId): void {
    this.activeTab = tab;
    switch (tab) {
      case 'interests':
        if (!this.interestsLoaded) this.loadInterests();
        if (!this.propertiesLoaded) this.loadProperties();
        break;
      case 'reservations':
        if (!this.reservationsLoaded) this.loadReservations();
        if (!this.propertiesLoaded) this.loadProperties();
        break;
      case 'deposits':
        if (!this.depositsLoaded) this.loadDeposits();
        if (!this.propertiesLoaded) this.loadProperties();
        break;
      case 'ventes':
        if (!this.ventesLoaded) this.loadVentes();
        break;
      case 'documents':
        if (!this.portalDocsLoaded) this.loadPortalDocs();
        break;
      case 'timeline':
        if (!this.timelineLoaded) this.loadTimeline();
        break;
    }
  }

  // ── Edit contact details ──────────────────────────────────────────────────

  toggleEditForm(): void {
    this.showEditForm = !this.showEditForm;
    this.editError = '';
    if (this.showEditForm && this.contact) {
      this.editForm = {
        firstName:  this.contact.firstName  ?? '',
        lastName:   this.contact.lastName   ?? '',
        email:      this.contact.email      ?? '',
        phone:      this.contact.phone      ?? '',
        nationalId: this.contact.nationalId ?? '',
        address:    this.contact.address    ?? '',
        notes:      this.contact.notes      ?? '',
      };
    }
  }

  saveEdit(): void {
    if (!this.contact || this.savingEdit) return;
    this.savingEdit = true;
    this.editError = '';
    this.svc.update(this.contactId, this.editForm).subscribe({
      next: (updated) => {
        this.contact = updated;
        this.savingEdit = false;
        this.showEditForm = false;
      },
      error: (err: HttpErrorResponse) => {
        this.savingEdit = false;
        const body = err.error as ErrorResponse | null;
        this.editError = body?.message ?? `Impossible d'enregistrer (${err.status})`;
      },
    });
  }

  // ── Qualify / convert to prospect ─────────────────────────────────────────

  toggleQualifyForm(): void {
    this.showQualifyForm = !this.showQualifyForm;
    this.qualifyError = '';
  }

  qualify(): void {
    if (!this.contact) return;
    this.qualifying = true;
    this.qualifyError = '';
    const req: ConvertToProspectRequest = {
      source:    this.qualifySource   || null,
      notes:     this.qualifyNotes    || null,
      budgetMin: this.qualifyBudgetMin,
      budgetMax: this.qualifyBudgetMax,
    };
    this.svc.convertToProspect(this.contactId, req).subscribe({
      next: (updated) => {
        this.contact = updated;
        this.qualifying = false;
        this.showQualifyForm = false;
        this.qualifySource = '';
        this.qualifyNotes = '';
        this.qualifyBudgetMin = null;
        this.qualifyBudgetMax = null;
      },
      error: (err: HttpErrorResponse) => {
        this.qualifying = false;
        const body = err.error as ErrorResponse | null;
        this.qualifyError = body?.message ?? `Erreur (${err.status})`;
      },
    });
  }

  // ── Status transition ──────────────────────────────────────────────────────

  transitionTo(status: string): void {
    if (!this.contact) return;
    this.updatingStatus = true;
    this.statusError = '';
    this.svc.updateStatus(this.contactId, status).subscribe({
      next: (updated) => { this.contact = updated; this.updatingStatus = false; },
      error: (err: HttpErrorResponse) => {
        this.updatingStatus = false;
        const body = err.error as ErrorResponse | null;
        this.statusError = body?.message ?? `Impossible de changer le statut (${err.status})`;
      },
    });
  }

  get allowedTransitions(): string[] {
    return this.contact ? (this.ALLOWED_TRANSITIONS[this.contact.status] ?? []) : [];
  }

  // ── Interests ──────────────────────────────────────────────────────────────

  private loadInterests(): void {
    this.interestsLoading = true;
    this.interestError = '';
    this.svc.listInterests(this.contactId).subscribe({
      next: (data) => { this.interests = data; this.interestsLoading = false; this.interestsLoaded = true; },
      error: (err: HttpErrorResponse) => {
        this.interestsLoading = false;
        const body = err.error as ErrorResponse | null;
        this.interestError = body?.message ?? `Erreur (${err.status})`;
      },
    });
  }

  private loadProperties(): void {
    this.propertiesLoading = true;
    this.propertiesError = '';
    this.propertySvc.list({ status: 'ACTIVE' }).subscribe({
      next: (data) => { this.properties = data; this.propertiesLoading = false; this.propertiesLoaded = true; },
      error: (err: HttpErrorResponse) => {
        this.propertiesLoading = false;
        const body = err.error as ErrorResponse | null;
        this.propertiesError = body?.message ?? `Impossible de charger les biens (${err.status})`;
      },
    });
  }

  get availableForInterest(): Property[] {
    const taken = new Set(this.interests.map((i) => i.propertyId));
    return this.properties.filter((p) => !taken.has(p.id));
  }

  propertyLabel(id: string): string {
    const p = this.properties.find((pr) => pr.id === id);
    return p ? `${p.title} — ${p.referenceCode}` : id;
  }

  addInterest(): void {
    if (!this.selectedPropertyId) return;
    this.addingInterest = true;
    this.interestError = '';
    this.svc.addInterest(this.contactId, this.selectedPropertyId).subscribe({
      next: () => {
        this.addingInterest = false;
        this.selectedPropertyId = '';
        this.loadInterests();
        // Refresh contact — auto-promotion may have changed status
        this.svc.getById(this.contactId).subscribe({ next: (c) => { this.contact = c; } });
      },
      error: (err: HttpErrorResponse) => {
        this.addingInterest = false;
        const body = err.error as ErrorResponse | null;
        this.interestError = body?.message ?? `Erreur (${err.status})`;
      },
    });
  }

  removeInterest(propertyId: string): void {
    this.removingPropertyId = propertyId;
    this.svc.removeInterest(this.contactId, propertyId).subscribe({
      next: () => { this.removingPropertyId = ''; this.loadInterests(); },
      error: (err: HttpErrorResponse) => {
        this.removingPropertyId = '';
        const body = err.error as ErrorResponse | null;
        this.interestError = body?.message ?? `Erreur (${err.status})`;
      },
    });
  }

  // ── Reservations ───────────────────────────────────────────────────────────

  private loadReservations(): void {
    this.reservationsLoading = true;
    this.reservationError = '';
    this.reservSvc.listByContact(this.contactId).subscribe({
      next: (data) => { this.reservations = data; this.reservationsLoading = false; this.reservationsLoaded = true; },
      error: (err: HttpErrorResponse) => {
        this.reservationsLoading = false;
        const body = err.error as ErrorResponse | null;
        this.reservationError = body?.message ?? `Erreur (${err.status})`;
      },
    });
  }

  createReservation(): void {
    if (!this.reservationPropertyId) return;
    this.creatingReservation = true;
    this.reservationError = '';
    this.reservationSuccess = '';
    const req: CreateReservationRequest = {
      contactId:  this.contactId,
      propertyId: this.reservationPropertyId,
      reservationPrice: this.reservationPrice ?? undefined,
      notes: this.reservationNotes || undefined,
    };
    this.reservSvc.create(req).subscribe({
      next: () => {
        this.creatingReservation = false;
        this.reservationSuccess = 'Réservation créée. Le bien est en attente.';
        this.reservationPropertyId = '';
        this.reservationPrice = null;
        this.reservationNotes = '';
        this.loadReservations();
        this.propertiesLoaded = false;
        this.loadProperties();
        this.svc.getById(this.contactId).subscribe({ next: (c) => { this.contact = c; } });
      },
      error: (err: HttpErrorResponse) => {
        this.creatingReservation = false;
        const body = err.error as ErrorResponse | null;
        if (err.status === 409) {
          this.reservationError = 'Ce bien est déjà réservé.';
          this.propertiesLoaded = false;
          this.loadProperties();
        } else {
          this.reservationError = body?.message ?? `Erreur (${err.status})`;
        }
      },
    });
  }

  cancelReservation(id: string): void {
    this.reservSvc.cancel(id).subscribe({
      next: () => {
        this.loadReservations();
        this.propertiesLoaded = false;
        this.loadProperties();
      },
      error: (err: HttpErrorResponse) => {
        const body = err.error as ErrorResponse | null;
        this.reservationError = body?.message ?? `Erreur (${err.status})`;
      },
    });
  }

  convertReservationToDeposit(r: Reservation): void {
    if (!r.reservationPrice) {
      this.reservationError = 'Veuillez saisir un montant avant de convertir.';
      return;
    }
    this.reservSvc.convertToDeposit(r.id, {
      amount: r.reservationPrice,
      currency: 'MAD',
    }).subscribe({
      next: () => {
        this.loadReservations();
        this.depositsLoaded = false;
        this.loadDeposits();
        this.svc.getById(this.contactId).subscribe({ next: (c) => { this.contact = c; } });
      },
      error: (err: HttpErrorResponse) => {
        const body = err.error as ErrorResponse | null;
        this.reservationError = body?.message ?? `Erreur (${err.status})`;
      },
    });
  }

  convertReservationToVente(r: Reservation): void {
    this.convertingReservationId = r.id;
    this.convertReservationError = '';
    this.venteSvc.create({ reservationId: r.id }).subscribe({
      next: vente => {
        this.convertingReservationId = null;
        this.router.navigate(['/app/ventes', vente.id]);
      },
      error: (err: HttpErrorResponse) => {
        this.convertingReservationId = null;
        const body = err.error as ErrorResponse | null;
        this.convertReservationError = body?.message ?? `Erreur (${err.status})`;
      },
    });
  }

  // ── Deposits ───────────────────────────────────────────────────────────────

  private loadDeposits(): void {
    this.depositsLoading = true;
    this.depositError = '';
    this.depositSvc.listByContact(this.contactId).subscribe({
      next: (data) => { this.deposits = data; this.depositsLoading = false; this.depositsLoaded = true; },
      error: (err: HttpErrorResponse) => {
        this.depositsLoading = false;
        const body = err.error as ErrorResponse | null;
        this.depositError = body?.message ?? `Erreur (${err.status})`;
      },
    });
  }

  createDeposit(): void {
    if (!this.depositPropertyId || !this.depositAmount) return;
    this.creatingDeposit = true;
    this.depositError = '';
    this.depositSuccess = '';
    const req: CreateDepositRequest = {
      contactId:  this.contactId,
      propertyId: this.depositPropertyId,
      amount:     this.depositAmount,
      notes:      this.depositNotes || undefined,
    };
    this.depositSvc.create(req).subscribe({
      next: () => {
        this.creatingDeposit = false;
        this.depositSuccess = 'Acompte créé avec succès.';
        this.depositPropertyId = '';
        this.depositAmount = null;
        this.depositNotes = '';
        this.loadDeposits();
        this.propertiesLoaded = false;
        this.loadProperties();
        this.svc.getById(this.contactId).subscribe({ next: (c) => { this.contact = c; } });
      },
      error: (err: HttpErrorResponse) => {
        this.creatingDeposit = false;
        const body = err.error as ErrorResponse | null;
        if (err.status === 409) {
          this.depositError = 'Ce bien est déjà réservé ou un acompte existe.';
          this.propertiesLoaded = false;
          this.loadProperties();
        } else {
          this.depositError = body?.message ?? `Erreur (${err.status})`;
        }
      },
    });
  }

  confirmDeposit(d: Deposit): void {
    this.actionDepositId = d.id;
    this.depositError = '';
    this.depositSvc.confirm(d.id).subscribe({
      next: () => {
        this.actionDepositId = null;
        this.loadDeposits();
        this.svc.getById(this.contactId).subscribe({ next: (c) => { this.contact = c; } });
      },
      error: (err: HttpErrorResponse) => {
        this.actionDepositId = null;
        const body = err.error as ErrorResponse | null;
        this.depositError = body?.message ?? `Erreur (${err.status})`;
      },
    });
  }

  cancelDeposit(d: Deposit): void {
    this.actionDepositId = d.id;
    this.depositError = '';
    this.depositSvc.cancel(d.id).subscribe({
      next: () => {
        this.actionDepositId = null;
        this.loadDeposits();
        this.propertiesLoaded = false;
        this.loadProperties();
        this.svc.getById(this.contactId).subscribe({ next: (c) => { this.contact = c; } });
      },
      error: (err: HttpErrorResponse) => {
        this.actionDepositId = null;
        const body = err.error as ErrorResponse | null;
        this.depositError = body?.message ?? `Erreur (${err.status})`;
      },
    });
  }

  downloadPdf(d: Deposit): void {
    this.depositSvc.downloadReservationPdf(d.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `reservation_${d.reference}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => { this.depositError = 'Échec du téléchargement PDF.'; },
    });
  }

  convertDepositToVente(d: Deposit): void {
    this.convertingDepositId = d.id;
    this.depositError = '';
    this.venteSvc.create({ contactId: d.contactId, propertyId: d.propertyId }).subscribe({
      next: () => {
        this.convertingDepositId = null;
        this.depositSuccess = 'Vente créée avec succès. Voir l\'onglet Ventes.';
        this.ventesLoaded = false;
        this.svc.getById(this.contactId).subscribe({ next: (c) => { this.contact = c; } });
      },
      error: (err: HttpErrorResponse) => {
        this.convertingDepositId = null;
        const body = err.error as { message?: string } | null;
        this.depositError = body?.message ?? `Erreur (${err.status})`;
      },
    });
  }

  // ── Ventes ─────────────────────────────────────────────────────────────────

  private loadVentes(): void {
    this.ventesLoading = true;
    this.ventesError = '';
    this.venteSvc.listByContact(this.contactId).subscribe({
      next: (data) => { this.ventes = data; this.ventesLoading = false; this.ventesLoaded = true; },
      error: (err: HttpErrorResponse) => {
        this.ventesLoading = false;
        const body = err.error as ErrorResponse | null;
        this.ventesError = body?.message ?? `Erreur (${err.status})`;
      },
    });
  }

  private loadPortalDocs(): void {
    this.venteSvc.listByContact(this.contactId).subscribe({
      next: (ventes) => {
        const docs: Array<{venteName: string; doc: VenteDocument}> = [];
        for (const v of ventes) {
          for (const d of v.documents) {
            if (d.uploadedByPortal) {
              docs.push({ venteName: v.venteRef || v.id, doc: d });
            }
          }
        }
        this.portalDocs.set(docs);
        this.portalDocsLoaded = true;
      },
      error: () => { this.portalDocsLoaded = true; },
    });
  }

  venteStatutLabel(s: string): string {
    const labels: Record<string, string> = {
      COMPROMIS:    'Compromis',
      FINANCEMENT:  'Financement',
      ACTE_NOTARIE: 'Acte notarié',
      LIVRE:        'Livré',
      ANNULE:       'Annulé',
    };
    return labels[s] ?? s;
  }

  venteStatutClass(s: string): string {
    const classes: Record<string, string> = {
      COMPROMIS:    'badge-info',
      FINANCEMENT:  'badge-warning',
      ACTE_NOTARIE: 'badge-primary',
      LIVRE:        'badge-success',
      ANNULE:       'badge-error',
    };
    return classes[s] ?? '';
  }

  // ── Timeline ───────────────────────────────────────────────────────────────

  private loadTimeline(): void {
    this.timelineLoading = true;
    this.timelineError = '';
    this.svc.getTimeline(this.contactId).subscribe({
      next: (events) => {
        this.timeline = events;
        this.timelineLoading = false;
        this.timelineLoaded = true;
      },
      error: (err: HttpErrorResponse) => {
        this.timelineLoading = false;
        this.timelineError = `Erreur (${err.status})`;
      },
    });
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  statusLabel(s: string): string {
    return this.STATUS_LABELS[s] ?? s;
  }

  statusClass(s: string): string {
    const map: Record<string, string> = {
      PROSPECT:           'badge-prospect',
      QUALIFIED_PROSPECT: 'badge-qualified',
      CLIENT:             'badge-client',
      ACTIVE_CLIENT:      'badge-active-client',
      COMPLETED_CLIENT:   'badge-completed',
      REFERRAL:           'badge-referral',
      LOST:               'badge-lost',
    };
    return map[s] ?? '';
  }

  typeClass(t: string): string {
    const map: Record<string, string> = {
      PROSPECT:    'type-prospect',
      TEMP_CLIENT: 'type-temp',
      CLIENT:      'type-client',
    };
    return map[t] ?? '';
  }

  depositStatusClass(s: string): string {
    const map: Record<string, string> = {
      PENDING:   'ds-pending',
      CONFIRMED: 'ds-confirmed',
      CANCELLED: 'ds-cancelled',
      EXPIRED:   'ds-expired',
    };
    return map[s] ?? '';
  }

  toggleReservationDocs(id: string): void {
    if (this.expandedReservationDocs.has(id)) this.expandedReservationDocs.delete(id);
    else this.expandedReservationDocs.add(id);
  }

  isReservationDocExpanded(id: string): boolean {
    return this.expandedReservationDocs.has(id);
  }

  reservationStatusClass(s: string): string {
    const map: Record<string, string> = {
      ACTIVE:               'rs-active',
      EXPIRED:              'rs-expired',
      CANCELLED:            'rs-cancelled',
      CONVERTED_TO_DEPOSIT: 'rs-converted',
    };
    return map[s] ?? '';
  }

  categoryLabel(c: string): string {
    const map: Record<string, string> = {
      AUDIT:         'Audit',
      MESSAGE:       'Message',
      NOTIFICATION:  'Notif.',
      STATUS_CHANGE: 'Statut',
    };
    return map[c] ?? c;
  }

  isTempClientExpired(): boolean {
    if (!this.contact?.tempClientUntil) return false;
    return new Date(this.contact.tempClientUntil) < new Date();
  }
}
