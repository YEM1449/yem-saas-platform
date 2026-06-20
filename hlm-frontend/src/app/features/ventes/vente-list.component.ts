import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { take } from 'rxjs/operators';
import { VenteService, Vente, VenteStatut, ContractStatus, CreateVenteRequest } from './vente.service';
import { AuthService } from '../../core/auth/auth.service';
import { ContactService } from '../contacts/contact.service';
import { Contact } from '../../core/models/contact.model';
import { PropertyService } from '../properties/property.service';
import { Property } from '../../core/models/property.model';
import { HttpErrorResponse } from '@angular/common/http';
import { UiButtonComponent, UiEmptyStateComponent } from '../../shared/ui';
import { TranslatePipe } from '@ngx-translate/core';
import { I18nService } from '../../core/i18n/i18n.service';

@Component({
  selector: 'app-vente-list',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, DecimalPipe,
            UiButtonComponent, UiEmptyStateComponent, TranslatePipe],
  templateUrl: './vente-list.component.html',
  styleUrl: './vente-list.component.css',
})
export class VenteListComponent implements OnInit {
  private svc         = inject(VenteService);
  private auth        = inject(AuthService);
  private contactSvc  = inject(ContactService);
  private propertySvc = inject(PropertyService);
  private route       = inject(ActivatedRoute);
  private i18n        = inject(I18nService);

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

  readonly statuts: VenteStatut[] = [
    'PROSPECT', 'OPTION', 'RESERVE', 'EN_RETRACTATION', 'ACOMPTE',
    'COMPROMIS', 'FINANCEMENT', 'ACTE',
    'LIVRE_AVEC_RESERVES', 'RESERVES_LEVEES', 'LIVRE_DEFINITIF', 'ANNULE'];

  /** View mode: table (default) or kanban board */
  viewMode: 'table' | 'kanban' = 'table';

  /** Statuts whose accordion panel is expanded on mobile. */
  openStatuts = new Set<VenteStatut>(['RESERVE', 'COMPROMIS', 'FINANCEMENT', 'ACTE', 'LIVRE_DEFINITIF', 'ANNULE']);

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

  // Column labels/hints are resolved from the i18n catalog by `key` in the template
  // ('ventes.statut.*' / 'ventes.kanbanHint.*'); only key + color live here.
  readonly KANBAN_COLUMNS: { key: VenteStatut; color: string }[] = [
    { key: 'RESERVE',         color: '#c2410c' },
    { key: 'COMPROMIS',       color: '#c2410c' },
    { key: 'FINANCEMENT',     color: '#a16207' },
    { key: 'ACTE',            color: '#15803d' },
    { key: 'LIVRE_DEFINITIF', color: '#15803d' }];

  get kanbanBoard(): { col: { key: VenteStatut; color: string }; items: Vente[] }[] {
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

  /** Tooltip description for a statut, resolved from the active language catalog. */
  statutDesc(s: VenteStatut): string {
    return this.i18n.instant('ventes.statutDesc.' + s);
  }

  ngOnInit(): void {
    this.route.queryParamMap.pipe(take(1)).subscribe(params => {
      const statut  = params.get('statut');
      const agentId = params.get('agentId');
      if (statut)  this.filterStatut  = statut;
      if (agentId) this.filterAgentId = agentId;
    });
    this.svc.list().subscribe({
      next:  (data) => { this.ventes.set(data); this.loading.set(false); },
      error: ()     => { this.error.set(this.i18n.instant('ventes.list.loadError')); this.loading.set(false); },
    });
  }

  statutLabel(s: VenteStatut): string {
    return this.i18n.instant('ventes.statut.' + s);
  }

  statutClass(s: VenteStatut): string {
    const classes: Record<VenteStatut, string> = {
      PROSPECT: 'badge-info', OPTION: 'badge-info', RESERVE: 'badge-info',
      EN_RETRACTATION: 'badge-warning', ACOMPTE: 'badge-info',
      COMPROMIS: 'badge-info', FINANCEMENT: 'badge-warning', ACTE: 'badge-primary',
      LIVRE_AVEC_RESERVES: 'badge-warning', RESERVES_LEVEES: 'badge-primary',
      LIVRE_DEFINITIF: 'badge-success', ANNULE: 'badge-error',
    };
    return classes[s] ?? '';
  }

  contractStatusLabel(s: ContractStatus): string {
    return this.i18n.instant('ventes.contractStatus.' + s);
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
      this.createError = this.i18n.instant('ventes.create.priceRequired');
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
        this.createError = (err.error as { message?: string })?.message
          ?? this.i18n.instant('ventes.create.genericError', { status: err.status });
      },
    });
  }
}
