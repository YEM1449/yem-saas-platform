import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { ContactService } from './contact.service';
import { Contact } from '../../core/models/contact.model';
import { ErrorResponse } from '../../core/models/error-response.model';
import { AuthService } from '../../core/auth/auth.service';

interface CreateContactForm {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  notes: string;
}

interface PrivacyNotice {
  version: string;
  lastUpdated: string;
  text: string;
}

interface PipelineColumn {
  key: string;
  label: string;
  color: string;
}

const PIPELINE_STATUSES = new Set([
  'PROSPECT', 'QUALIFIED_PROSPECT', 'CLIENT', 'ACTIVE_CLIENT', 'COMPLETED_CLIENT', 'REFERRAL', 'LOST',
]);

@Component({
  selector: 'app-contacts',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, TranslateModule],
  templateUrl: './contacts.component.html',
  styleUrl: './contacts.component.css',
})
export class ContactsComponent implements OnInit {
  private svc    = inject(ContactService);
  private auth   = inject(AuthService);
  private http   = inject(HttpClient);
  private router = inject(Router);

  contacts: Contact[] = [];
  loading = true;
  error   = '';

  searchQuery = '';
  showLost    = false;

  /** 'list' = table | 'cards' = contact card grid | 'pipeline' = kanban board */
  viewMode: 'list' | 'cards' | 'pipeline' = 'list';

  /** Modal state */
  showModal   = false;
  submitting  = false;
  submitError = '';

  form: CreateContactForm = {
    firstName: '', lastName: '', email: '', phone: '', notes: '',
  };

  privacyNotice: PrivacyNotice | null = null;
  privacyBannerVisible = false;

  readonly PIPELINE_COLUMNS: PipelineColumn[] = [
    { key: 'PROSPECT',           label: 'Prospects',      color: '#64748b' },
    { key: 'QUALIFIED_PROSPECT', label: 'Qualifiés',      color: '#3b82f6' },
    { key: 'CLIENT',             label: 'Clients',        color: '#8b5cf6' },
    { key: 'ACTIVE_CLIENT',      label: 'Clients Actifs', color: '#10b981' },
    { key: 'COMPLETED_CLIENT',   label: 'Complétés',      color: '#059669' },
    { key: 'REFERRAL',           label: 'Parrains',       color: '#f59e0b' },
  ];

  // ── Auth ────────────────────────────────────────────────────

  get canWrite(): boolean {
    const r = this.auth.user?.role;
    return r === 'ROLE_ADMIN' || r === 'ROLE_MANAGER';
  }

  // ── Filtered lists ──────────────────────────────────────────

  private get searched(): Contact[] {
    const q = this.searchQuery.toLowerCase().trim();
    if (!q) return this.contacts;
    return this.contacts.filter(c =>
      c.fullName.toLowerCase().includes(q) ||
      (c.email  ?? '').toLowerCase().includes(q) ||
      (c.phone  ?? '').includes(q)
    );
  }

  /** For list view — all contacts matching search */
  get filteredList(): Contact[] {
    return this.searched;
  }

  /** For pipeline view — only pipeline statuses, search applied */
  get filteredPipeline(): Contact[] {
    return this.searched.filter(c => {
      if (!PIPELINE_STATUSES.has(c.status)) return false;
      if (!this.showLost && c.status === 'LOST') return false;
      return true;
    });
  }

  columnContacts(key: string): Contact[] {
    return this.filteredPipeline.filter(c => c.status === key);
  }

  get lostCount(): number {
    return this.contacts.filter(c => c.status === 'LOST').length;
  }

  get pipelineTotal(): number {
    return this.contacts.filter(c => PIPELINE_STATUSES.has(c.status)).length;
  }

  get kpiProspects(): number {
    return this.contacts.filter(c => c.status === 'PROSPECT' || c.status === 'QUALIFIED_PROSPECT').length;
  }

  get kpiActive(): number {
    return this.contacts.filter(c => c.status === 'CLIENT' || c.status === 'ACTIVE_CLIENT').length;
  }

  get kpiCompleted(): number {
    return this.contacts.filter(c => c.status === 'COMPLETED_CLIENT').length;
  }

  get kpiConversionRate(): number {
    const eligible = this.contacts.filter(
      c => PIPELINE_STATUSES.has(c.status) && c.status !== 'REFERRAL'
    ).length;
    if (eligible === 0) return 0;
    return Math.round(this.kpiCompleted / eligible * 100);
  }

  daysSince(dateStr: string): number {
    return Math.floor((Date.now() - new Date(dateStr).getTime()) / 86_400_000);
  }

  // ── Avatar helpers ──────────────────────────────────────────

  initials(c: Contact): string {
    return ((c.firstName?.charAt(0) ?? '') + (c.lastName?.charAt(0) ?? '')).toUpperCase();
  }

  avatarColor(c: Contact): string {
    const colors = ['#3b82f6','#8b5cf6','#10b981','#f59e0b','#ef4444','#06b6d4','#ec4899'];
    const code = (c.firstName?.charCodeAt(0) ?? 0) + (c.lastName?.charCodeAt(0) ?? 0);
    return colors[code % colors.length];
  }

  // ── View toggle ─────────────────────────────────────────────

  setView(mode: 'list' | 'cards' | 'pipeline'): void {
    this.viewMode = mode;
    localStorage.setItem('contacts_view', mode);
  }

  // ── Badge ───────────────────────────────────────────────────

  badgeClass(value: string): string {
    return 'badge badge-' + value.toLowerCase().replace(/_/g, '-');
  }

  // ── Lifecycle ───────────────────────────────────────────────

  ngOnInit(): void {
    const saved = localStorage.getItem('contacts_view');
    if (saved === 'pipeline' || saved === 'list' || saved === 'cards') this.viewMode = saved;
    this.load();
    this.loadPrivacyNotice();
  }

  load(): void {
    this.loading = true;
    this.error   = '';
    this.svc.list().subscribe({
      next: (page) => { this.contacts = page.content; this.loading = false; },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        const body = err.error as ErrorResponse | null;
        if      (err.status === 401) this.error = 'Session expirée. Veuillez vous reconnecter.';
        else if (err.status === 403) this.error = 'Accès refusé.';
        else                          this.error = body?.message ?? `Erreur de chargement (${err.status})`;
      },
    });
  }

  private loadPrivacyNotice(): void {
    const dismissed = sessionStorage.getItem('gdpr_notice_dismissed') === 'true';
    if (dismissed) return;
    this.http.get<PrivacyNotice>('/api/gdpr/privacy-notice').subscribe({
      next: (notice) => { this.privacyNotice = notice; this.privacyBannerVisible = true; },
      error: () => {},
    });
  }

  dismissPrivacyBanner(): void {
    sessionStorage.setItem('gdpr_notice_dismissed', 'true');
    this.privacyBannerVisible = false;
  }

  // ── Modal ───────────────────────────────────────────────────

  openModal(): void {
    this.form = { firstName: '', lastName: '', email: '', phone: '', notes: '' };
    this.submitError = '';
    this.showModal   = true;
  }

  closeModal(): void {
    if (this.submitting) return;
    this.showModal = false;
  }

  submitCreate(): void {
    if (!this.form.firstName.trim() || !this.form.lastName.trim()) {
      this.submitError = 'Prénom et nom obligatoires.';
      return;
    }
    if (!this.form.email.trim() && !this.form.phone.trim()) {
      this.submitError = 'Téléphone ou email obligatoire.';
      return;
    }
    this.submitting  = true;
    this.submitError = '';

    this.svc.create({
      firstName:       this.form.firstName.trim(),
      lastName:        this.form.lastName.trim(),
      email:           this.form.email.trim()  || null,
      phone:           this.form.phone.trim()  || null,
      notes:           this.form.notes.trim()  || null,
      processingBasis: 'LEGITIMATE_INTEREST',
    }).subscribe({
      next: (created) => {
        this.submitting = false;
        this.contacts   = [created, ...this.contacts];
        this.showModal  = false;
        // Navigate to detail to complete the prospect workflow
        this.router.navigate(['/app/contacts', created.id]);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting = false;
        const body = err.error as ErrorResponse | null;
        this.submitError = body?.message ?? `Erreur lors de la création (${err.status})`;
      },
    });
  }
}
