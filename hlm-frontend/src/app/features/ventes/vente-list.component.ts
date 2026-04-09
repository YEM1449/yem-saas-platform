import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { VenteService, Vente, VenteStatut, CreateVenteRequest } from './vente.service';
import { AuthService } from '../../core/auth/auth.service';
import { ContactService } from '../contacts/contact.service';
import { Contact } from '../../core/models/contact.model';
import { PropertyService } from '../properties/property.service';
import { Property } from '../../core/models/property.model';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
  selector: 'app-vente-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe, DecimalPipe, TranslateModule],
  templateUrl: './vente-list.component.html',
  styleUrl: './vente-list.component.css',
})
export class VenteListComponent implements OnInit {
  private svc         = inject(VenteService);
  private auth        = inject(AuthService);
  private contactSvc  = inject(ContactService);
  private propertySvc = inject(PropertyService);

  ventes  = signal<Vente[]>([]);
  loading = signal(true);
  error   = signal('');
  showCreate = false;
  filterStatut = '';

  // ── Create form state ──────────────────────────────────────────────────────
  contacts: Contact[] = [];
  properties: Property[] = [];
  createContactId   = '';
  createPropertyId  = '';
  createPrixVente: number | null = null;
  createReduction: number | null = null;
  /** Price suggested from the selected property (display only). */
  suggestedPrice: number | null = null;
  createDateCompromis = '';
  createNotes = '';
  creating = false;
  createError = '';

  readonly statuts: VenteStatut[] = ['COMPROMIS', 'FINANCEMENT', 'ACTE_NOTARIE', 'LIVRE', 'ANNULE'];

  get canWrite(): boolean {
    const r = this.auth.user?.role;
    return r === 'ROLE_ADMIN' || r === 'ROLE_MANAGER';
  }

  get filtered(): Vente[] {
    if (!this.filterStatut) return this.ventes();
    return this.ventes().filter(v => v.statut === this.filterStatut as VenteStatut);
  }

  ngOnInit(): void {
    this.svc.list().subscribe({
      next:  (data) => { this.ventes.set(data); this.loading.set(false); },
      error: ()     => { this.error.set('Erreur lors du chargement des ventes.'); this.loading.set(false); },
    });
  }

  statutLabel(s: VenteStatut): string {
    const labels: Record<VenteStatut, string> = {
      COMPROMIS:    'Compromis',
      FINANCEMENT:  'Financement',
      ACTE_NOTARIE: 'Acte notarié',
      LIVRE:        'Livré',
      ANNULE:       'Annulé',
    };
    return labels[s] ?? s;
  }

  statutClass(s: VenteStatut): string {
    const classes: Record<VenteStatut, string> = {
      COMPROMIS:    'badge-info',
      FINANCEMENT:  'badge-warning',
      ACTE_NOTARIE: 'badge-primary',
      LIVRE:        'badge-success',
      ANNULE:       'badge-error',
    };
    return classes[s] ?? '';
  }

  // ── Create dialog ──────────────────────────────────────────────────────────

  openCreate(): void {
    this.showCreate = true;
    this.createError      = '';
    this.createContactId  = '';
    this.createPropertyId = '';
    this.createPrixVente  = null;
    this.createReduction  = null;
    this.suggestedPrice   = null;
    this.createDateCompromis = '';
    this.createNotes = '';
    // P2: size=500 to avoid pagination truncating contacts beyond page 1 (default size=20)
    if (this.contacts.length === 0) {
      this.contactSvc.list({ size: 500 }).subscribe({
        next: (page) => { this.contacts = page.content; },
      });
    }
    if (this.properties.length === 0) {
      this.propertySvc.list({ status: 'ACTIVE' }).subscribe({
        next: (data) => { this.properties = data; },
      });
    }
  }

  cancelCreate(): void {
    this.showCreate = false;
    this.createError = '';
  }

  /** When the user picks a property, pre-fill the price from the property catalogue. */
  onPropertyChange(): void {
    const prop = this.properties.find(p => p.id === this.createPropertyId);
    if (prop?.price) {
      this.suggestedPrice  = prop.price;
      this.createPrixVente = prop.price;
    } else {
      this.suggestedPrice  = null;
    }
  }

  submitCreate(): void {
    if (!this.createContactId || !this.createPropertyId) return;
    if (!this.createPrixVente || this.createPrixVente <= 0) {
      this.createError = 'Le prix de vente est obligatoire et doit être positif.';
      return;
    }
    this.creating = true;
    this.createError = '';
    const req: CreateVenteRequest = {
      contactId:     this.createContactId,
      propertyId:    this.createPropertyId,
      prixVente:     this.createPrixVente,
      dateCompromis: this.createDateCompromis || null,
      notes:         this.createNotes || null,
    };
    this.svc.create(req).subscribe({
      next: (v) => {
        this.creating = false;
        this.showCreate = false;
        this.ventes.update(list => [v, ...list]);
        // P1: invalidate property cache — the sold property is no longer ACTIVE
        this.properties = [];
      },
      error: (err: HttpErrorResponse) => {
        this.creating = false;
        this.createError = (err.error as { message?: string })?.message ?? `Erreur (${err.status})`;
      },
    });
  }
}
