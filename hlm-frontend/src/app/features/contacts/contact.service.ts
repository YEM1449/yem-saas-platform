import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Contact, ContactPage, TimelineEvent } from '../../core/models/contact.model';
import { ContactInterest } from '../../core/models/contact-interest.model';

export interface CreateContactRequest {
  firstName: string;
  lastName: string;
  phone?: string | null;
  email?: string | null;
  nationalId?: string | null;
  address?: string | null;
  notes?: string | null;
  // GDPR / Law 09-08 consent fields
  consentGiven?: boolean | null;
  consentMethod?: string | null;
  processingBasis?: string | null;
}

export interface ContactImportRowIssue {
  line: number;
  identity: string;
  reason: string;
}

export interface ContactImportReport {
  dryRun: boolean;
  totalRows: number;
  created: number;
  duplicateCount: number;
  errorCount: number;
  truncated: boolean;
  ignoredColumns: string[];
  duplicates: ContactImportRowIssue[];
  errors: ContactImportRowIssue[];
}

export interface UpdateContactRequest {
  firstName?: string | null;
  lastName?: string | null;
  phone?: string | null;
  email?: string | null;
  nationalId?: string | null;
  address?: string | null;
  notes?: string | null;
  consentGiven?: boolean | null;
  consentMethod?: string | null;
  processingBasis?: string | null;
}

export interface ConvertToProspectRequest {
  source?: string | null;
  notes?: string | null;
  budgetMin?: number | null;
  budgetMax?: number | null;
}

export type SituationMatrimoniale =
  'CELIBATAIRE' | 'MARIE_COMMUNAUTE' | 'MARIE_SEPARATION' | 'DIVORCE' | 'VEUF';
export type TypeAcquereur = 'RESIDENT_MAROC' | 'MRE' | 'ETRANGER';

/** VEFA legal identity (Loi 44-00). CIN is the contact's nationalId. */
export interface ContactLegal {
  contactId?: string;
  nationalId?: string | null;
  cinDateDelivrance?: string | null;
  cinAutorite?: string | null;
  passeportNumero?: string | null;
  passeportExpire?: string | null;
  dateNaissance?: string | null;
  lieuNaissance?: string | null;
  situationMatrimoniale?: SituationMatrimoniale | null;
  nationalite?: string | null;
  paysResidence?: string | null;
  typeAcquereur?: TypeAcquereur | null;
  apportPersonnel?: number | null;
}

@Injectable({ providedIn: 'root' })
export class ContactService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;

  list(params?: { q?: string; size?: number }): Observable<ContactPage> {
    const p: Record<string, string> = {};
    if (params?.q)    p['q']    = params.q;
    if (params?.size) p['size'] = String(params.size);
    return this.http.get<ContactPage>(`${this.apiUrl}/api/contacts`, { params: p });
  }

  getById(id: string): Observable<Contact> {
    return this.http.get<Contact>(`${this.apiUrl}/api/contacts/${id}`);
  }

  getTimeline(id: string, limit = 50): Observable<TimelineEvent[]> {
    return this.http.get<TimelineEvent[]>(
      `${this.apiUrl}/api/contacts/${id}/timeline?limit=${limit}`
    );
  }

  /** Create a new contact (ADMIN / MANAGER only). */
  create(req: CreateContactRequest): Observable<Contact> {
    return this.http.post<Contact>(`${this.apiUrl}/api/contacts`, req);
  }

  /**
   * Import contacts from a CSV file (ADMIN / MANAGER only).
   * `dryRun=true` previews created/duplicates/errors without persisting anything.
   * Exactly one of `consentGiven` / `processingBasis` is required (Loi 09-08).
   */
  importCsv(file: File, opts: { dryRun: boolean; consentGiven: boolean; processingBasis?: string }):
      Observable<ContactImportReport> {
    const form = new FormData();
    form.append('file', file);
    form.append('dryRun', String(opts.dryRun));
    form.append('consentGiven', String(opts.consentGiven));
    if (opts.processingBasis) form.append('processingBasis', opts.processingBasis);
    return this.http.post<ContactImportReport>(`${this.apiUrl}/api/contacts/import`, form);
  }

  /** Partially update a contact's fields (ADMIN / MANAGER only). */
  update(id: string, req: UpdateContactRequest): Observable<Contact> {
    return this.http.patch<Contact>(`${this.apiUrl}/api/contacts/${id}`, req);
  }

  // ── VEFA legal identity (Loi 44-00) ─────────────────────────────────────
  getLegalDetails(id: string): Observable<ContactLegal> {
    return this.http.get<ContactLegal>(`${this.apiUrl}/api/contacts/${id}/legal`);
  }

  updateLegalDetails(id: string, req: ContactLegal): Observable<ContactLegal> {
    return this.http.patch<ContactLegal>(`${this.apiUrl}/api/contacts/${id}/legal`, req);
  }

  /** Transition the contact's status via the server-enforced state machine. */
  updateStatus(id: string, status: string): Observable<Contact> {
    return this.http.patch<Contact>(`${this.apiUrl}/api/contacts/${id}/status`, { status });
  }

  /** Qualify as prospect and optionally enrich budget / source / notes. */
  convertToProspect(id: string, req: ConvertToProspectRequest = {}): Observable<Contact> {
    return this.http.post<Contact>(
      `${this.apiUrl}/api/contacts/${id}/convert-to-prospect`, req
    );
  }

  listInterests(contactId: string): Observable<ContactInterest[]> {
    return this.http.get<ContactInterest[]>(
      `${this.apiUrl}/api/contacts/${contactId}/interests`
    );
  }

  addInterest(contactId: string, propertyId: string): Observable<void> {
    return this.http.post<void>(
      `${this.apiUrl}/api/contacts/${contactId}/interests`, { propertyId }
    );
  }

  removeInterest(contactId: string, propertyId: string): Observable<void> {
    return this.http.delete<void>(
      `${this.apiUrl}/api/contacts/${contactId}/interests/${propertyId}`
    );
  }
}
