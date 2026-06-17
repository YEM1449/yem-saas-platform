import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ContactService, ConvertToProspectRequest, UpdateContactRequest,
         ContactLegal, SituationMatrimoniale, TypeAcquereur } from './contact.service';
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
import { TranslatePipe } from '@ngx-translate/core';
import { I18nService } from '../../core/i18n/i18n.service';

type TabId = 'details' | 'interests' | 'reservations' | 'deposits' | 'ventes' | 'timeline' | 'documents' | 'tasks';

@Component({
  selector: 'app-contact-detail',
  standalone: true,
  imports: [RouterLink, FormsModule, DocumentListComponent, ContactTasksComponent, DecimalPipe, DatePipe, TranslatePipe],
  templateUrl: './contact-detail.component.html',
  styleUrl: './contact-detail.component.css',
})
export class ContactDetailComponent implements OnInit {
  private svc          = inject(ContactService);
  private i18n         = inject(I18nService);
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

  // ── VEFA legal identity (Loi 44-00) ────────────────────────────────────────
  showLegal = false;
  legal: ContactLegal = {};
  legalSaving = false;
  legalError = '';
  legalSaved = false;
  readonly situationOptions: SituationMatrimoniale[] =
    ['CELIBATAIRE', 'MARIE_COMMUNAUTE', 'MARIE_SEPARATION', 'DIVORCE', 'VEUF'];
  readonly typeAcquereurOptions: TypeAcquereur[] = ['RESIDENT_MAROC', 'MRE', 'ETRANGER'];

  toggleLegal(): void {
    this.showLegal = !this.showLegal;
    if (this.showLegal && this.contact && !this.legal.contactId) {
      this.svc.getLegalDetails(this.contact.id).subscribe({ next: (l) => this.legal = l });
    }
  }

  saveLegal(): void {
    if (!this.contact) return;
    this.legalSaving = true;
    this.legalError = '';
    this.legalSaved = false;
    this.svc.updateLegalDetails(this.contact.id, this.legal).subscribe({
      next: (l) => { this.legal = l; this.legalSaving = false; this.legalSaved = true; },
      error: () => { this.legalSaving = false; this.legalError = this.i18n.instant('contacts.detail.errors.legalSaveFailed'); },
    });
  }
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
  // Status labels resolved from the i18n catalog (contacts.status.*).

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
        if (err.status === 404) this.error = this.i18n.instant('contacts.detail.errors.notFound');
        else if (err.status === 401) this.error = this.i18n.instant('contacts.detail.errors.sessionExpired');
        else this.error = body?.message ?? this.i18n.instant('ventes.create.genericError', { status: err.status });
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
        this.qualifyError = body?.message ?? this.i18n.instant('ventes.create.genericError', { status: err.status });
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
        this.interestError = body?.message ?? this.i18n.instant('ventes.create.genericError', { status: err.status });
      },
    });
  }

  private loadProperties(): void {
    this.propertiesLoading = true;
    this.propertiesError = '';
    this.propertySvc.list().subscribe({
      next: (data) => { this.properties = data; this.propertiesLoading = false; this.propertiesLoaded = true; },
      error: (err: HttpErrorResponse) => {
        this.propertiesLoading = false;
        const body = err.error as ErrorResponse | null;
        this.propertiesError = body?.message ?? `Impossible de charger les biens (${err.status})`;
      },
    });
  }

  get activeProperties(): Property[] {
    return this.properties.filter((p) => p.status === 'ACTIVE');
  }

  get availableForInterest(): Property[] {
    const taken = new Set(this.interests.map((i) => i.propertyId));
    return this.properties.filter((p) => !taken.has(p.id) && p.status === 'ACTIVE');
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
        this.interestError = body?.message ?? this.i18n.instant('ventes.create.genericError', { status: err.status });
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
        this.interestError = body?.message ?? this.i18n.instant('ventes.create.genericError', { status: err.status });
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
        this.reservationError = body?.message ?? this.i18n.instant('ventes.create.genericError', { status: err.status });
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
        this.reservationSuccess = this.i18n.instant('contacts.detail.errors.reservationCreated');
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
          this.reservationError = this.i18n.instant('contacts.detail.errors.alreadyReserved');
          this.propertiesLoaded = false;
          this.loadProperties();
        } else {
          this.reservationError = body?.message ?? this.i18n.instant('ventes.create.genericError', { status: err.status });
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
        this.reservationError = body?.message ?? this.i18n.instant('ventes.create.genericError', { status: err.status });
      },
    });
  }

  convertReservationToDeposit(r: Reservation): void {
    if (!r.reservationPrice) {
      this.reservationError = this.i18n.instant('contacts.detail.errors.amountBeforeConvert');
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
        this.reservationError = body?.message ?? this.i18n.instant('ventes.create.genericError', { status: err.status });
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
        this.convertReservationError = body?.message ?? this.i18n.instant('ventes.create.genericError', { status: err.status });
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
        this.depositError = body?.message ?? this.i18n.instant('ventes.create.genericError', { status: err.status });
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
        this.depositSuccess = this.i18n.instant('contacts.detail.errors.depositCreated');
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
          this.depositError = this.i18n.instant('contacts.detail.errors.alreadyReservedOrDeposit');
          this.propertiesLoaded = false;
          this.loadProperties();
        } else {
          this.depositError = body?.message ?? this.i18n.instant('ventes.create.genericError', { status: err.status });
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
        this.depositError = body?.message ?? this.i18n.instant('ventes.create.genericError', { status: err.status });
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
        this.depositError = body?.message ?? this.i18n.instant('ventes.create.genericError', { status: err.status });
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
      error: () => { this.depositError = this.i18n.instant('contacts.detail.errors.pdfDownloadFailed'); },
    });
  }

  convertDepositToVente(d: Deposit): void {
    this.convertingDepositId = d.id;
    this.depositError = '';
    this.venteSvc.create({ contactId: d.contactId, propertyId: d.propertyId }).subscribe({
      next: () => {
        this.convertingDepositId = null;
        this.depositSuccess = this.i18n.instant('contacts.detail.errors.venteCreated');
        this.ventesLoaded = false;
        this.svc.getById(this.contactId).subscribe({ next: (c) => { this.contact = c; } });
      },
      error: (err: HttpErrorResponse) => {
        this.convertingDepositId = null;
        const body = err.error as { message?: string } | null;
        this.depositError = body?.message ?? this.i18n.instant('ventes.create.genericError', { status: err.status });
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
        this.ventesError = body?.message ?? this.i18n.instant('ventes.create.genericError', { status: err.status });
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
    return this.i18n.instant('ventes.statut.' + s);
  }

  venteStatutClass(s: string): string {
    const classes: Record<string, string> = {
      COMPROMIS:    'badge-info',
      FINANCEMENT:  'badge-warning',
      ACTE: 'badge-primary',
      LIVRE_DEFINITIF:        'badge-success',
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
        this.timelineError = this.i18n.instant('ventes.create.genericError', { status: err.status });
      },
    });
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  statusLabel(s: string): string {
    return this.i18n.instant('contacts.status.' + s);
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
    return this.i18n.instant('contacts.detail.categories.' + c);
  }

  isTempClientExpired(): boolean {
    if (!this.contact?.tempClientUntil) return false;
    return new Date(this.contact.tempClientUntil) < new Date();
  }
}
