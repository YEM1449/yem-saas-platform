import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { take } from 'rxjs/operators';
import { TranslateModule } from '@ngx-translate/core';
import { VenteService, Vente, VenteStatut, ContractStatus, CreateVenteRequest } from './vente.service';
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
  private route       = inject(ActivatedRoute);

  ventes  = signal<Vente[]>([]);
  loading = signal(true);
  error   = signal('');
  showCreate = false;
  filterStatut  = '';
  filterAgentId = '';

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

  /** View mode: table (default) or kanban board */
  viewMode: 'table' | 'kanban' = 'table';

  /** Statuts whose accordion panel is expanded on mobile. */
  openStatuts = new Set<VenteStatut>(['COMPROMIS', 'FINANCEMENT', 'ACTE_NOTARIE', 'LIVRE', 'ANNULE']);

  /** Ventes grouped by statut for the mobile accordion view. */
  get groupedByStatut(): { statut: VenteStatut; items: Vente[] }[] {
    return this.statuts
      .map(s => ({ statut: s, items: this.filtered.filter(v => v.statut === s) }))
      .filter(g => g.items.length > 0);
  }

  toggleAccordion(statut: VenteStatut): void {
    const s = new Set(this.openStatuts);
    if (s.has(statut)) s.delete(statut); else s.add(statut);
    this.openStatuts = s;
  }

  get canWrite(): boolean {
    const r = this.auth.user?.role;
    return r === 'ROLE_ADMIN' || r === 'ROLE_MANAGER';
  }

  get filtered(): Vente[] {
    return this.ventes().filter(v => {
      if (this.filterStatut  && v.statut   !== this.filterStatut as VenteStatut) return false;
      if (this.filterAgentId && v.agentId  !== this.filterAgentId)               return false;
      return true;
    });
  }

  countStatut(s: VenteStatut): number {
    return this.ventes().filter(v => v.statut === s).length;
  }

  readonly KANBAN_COLUMNS: { key: VenteStatut; label: string; hint: string; color: string }[] = [
    { key: 'COMPROMIS',    label: 'Compromis',    hint: 'Avant-contrat signé',       color: '#c2410c' },
    { key: 'FINANCEMENT',  label: 'Financement',  hint: 'Dossier bancaire en cours', color: '#a16207' },
    { key: 'ACTE_NOTARIE', label: 'Acte notarié', hint: 'Acte authentique',          color: '#15803d' },
    { key: 'LIVRE',        label: 'Livré',         hint: 'Remise des clés',           color: '#1d4ed8' },
  ];

  get kanbanBoard(): { col: { key: VenteStatut; label: string; hint: string; color: string }; items: Vente[] }[] {
    return this.KANBAN_COLUMNS.map(col => ({
      col,
      items: this.filtered.filter(v => v.statut === col.key),
    }));
  }

  totalKanban(): number {
    return this.kanbanBoard.reduce((a, c) => a + c.items.length, 0);
  }

  totalCA(items: Vente[]): number {
    return items.reduce((a, v) => a + (v.prixVente ?? 0), 0);
  }

  formatCAShort(n: number): string {
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(1).replace('.', ',') + ' M';
    if (n >= 1_000)     return Math.round(n / 1_000) + ' K';
    return n.toLocaleString('fr-FR');
  }

  ageDays(v: Vente): number {
    return Math.floor((Date.now() - new Date(v.createdAt).getTime()) / 86_400_000);
  }

  initials(name: string | null | undefined): string {
    if (!name) return '?';
    return name.split(' ').map(p => p.charAt(0)).join('').substring(0, 2).toUpperCase();
  }

  readonly STATUT_DESC: Record<VenteStatut, string> = {
    COMPROMIS:    'Avant-contrat signé — financement et conditions suspensives en cours',
    FINANCEMENT:  'Dossier de financement déposé — en attente d\'accord bancaire',
    ACTE_NOTARIE: 'Acte authentique signé devant notaire — transfert de propriété effectué',
    LIVRE:        'Bien remis à l\'acquéreur — vente finalisée',
    ANNULE:       'Vente annulée — voir motif dans la fiche',
  };

  ngOnInit(): void {
    this.route.queryParamMap.pipe(take(1)).subscribe(params => {
      const statut  = params.get('statut');
      const agentId = params.get('agentId');
      if (statut)  this.filterStatut  = statut;
      if (agentId) this.filterAgentId = agentId;
    });
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

  contractStatusLabel(s: ContractStatus): string {
    return { PENDING: 'En attente', GENERATED: 'Généré', SIGNED: 'Signé' }[s] ?? s;
  }

  contractStatusClass(s: ContractStatus): string {
    return { PENDING: 'badge-secondary', GENERATED: 'badge-info', SIGNED: 'badge-success' }[s] ?? '';
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
