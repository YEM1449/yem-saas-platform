import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import {
  VenteService, Vente, VenteStatut, EcheanceStatut, ContractStatus,
  TypeFinancement, MotifAnnulation, UpdateFinancingRequest,
  CreateEcheanceRequest, UpdateVenteStatutRequest, ReserveLivraison,
  CoAcquereur, RoleAcquereur, SituationMatrimoniale, TypeAcquereur,
  DossierFinancement, StatutDossierFinancement
} from './vente.service';
import { AuthService } from '../../core/auth/auth.service';
import { PipelineStepperComponent } from './pipeline-stepper.component';
import { AdvancePipelineDialogComponent } from './advance-pipeline-dialog.component';

/** Synthesised financial position of a sale — paid vs. remaining vs. overdue. */
interface DealFinancials {
  total: number;
  encaisse: number;
  reste: number;
  pct: number;
  overdueCount: number;
  overdueAmount: number;
  hasEcheances: boolean;
}

/** The single most pressing dated obligation on the sale right now. */
interface NextDeadline {
  label: string;
  date: string;
  days: number;
  urgency: string;
}

/** A concrete thing the agent must act on, ranked by severity. */
interface AttentionItem {
  text: string;
  severity: 'critical' | 'warning' | 'info';
}

@Component({
  selector: 'app-vente-detail',
  standalone: true,
  imports: [
    CommonModule, FormsModule, DatePipe, DecimalPipe, TranslateModule,
    RouterLink, PipelineStepperComponent, AdvancePipelineDialogComponent,
  ],
  templateUrl: './vente-detail.component.html',
  styleUrl: './vente-detail.component.css',
})
export class VenteDetailComponent implements OnInit {
  private svc   = inject(VenteService);
  private auth  = inject(AuthService);
  private route = inject(ActivatedRoute);

  vente    = signal<Vente | null>(null);
  error    = signal('');
  inviting = signal(false);
  inviteMsg   = signal('');
  inviteError = signal('');
  echError = signal('');

  showEcheanceForm  = false;
  showAdvanceDialog = false;
  savingStatut      = false;
  docUploading      = signal(false);
  docError          = signal('');

  // Contract actions
  contractGenerating = signal(false);
  contractSigning    = signal(false);
  contractError      = signal('');

  // Financing edit panel
  showFinancingForm = false;
  financing: UpdateFinancingRequest = {};
  financingError  = signal('');
  financingSaving = signal(false);

  // Post-livraison dates (PV réception + titre foncier)
  editingPostLivraison = false;
  postLivraisonForm: { datePvReception: string | null; dateTitreFoncier: string | null } =
    { datePvReception: null, dateTitreFoncier: null };

  readonly typeFinancementOptions: TypeFinancement[] = ['COMPTANT', 'CREDIT_IMMOBILIER', 'PTZ', 'MIXTE'];

  ech: CreateEcheanceRequest = { libelle: '', montant: 0, dateEcheance: '' };

  // ── VEFA Loi 44-00 lifecycle actions ──────────────────────────────────────
  vefaError = signal('');
  vefaBusy  = signal(false);
  reserves  = signal<ReserveLivraison[]>([]);
  showConfirmForm  = false;
  showDeliveryForm = false;
  depositAmount: number | null = null;
  deliveryDate: string | null = null;
  deliveryReserves = ''; // one reserve description per line

  get canWrite(): boolean {
    const r = this.auth.user?.role;
    return r === 'ROLE_ADMIN' || r === 'ROLE_MANAGER';
  }

  get canWriteFinancing(): boolean {
    const r = this.auth.user?.role;
    return r === 'ROLE_ADMIN' || r === 'ROLE_MANAGER' || r === 'ROLE_AGENT';
  }

  // ── Co-acquéreur (VEFA co-acquisition) ─────────────────────────────────────
  coAcquereur = signal<CoAcquereur | null>(null);
  showCoAcqForm = false;
  coAcqForm: CoAcquereur = { nom: '', prenom: '' };
  coAcqError = signal('');
  coAcqBusy = signal(false);
  readonly coAcqSituationOptions: SituationMatrimoniale[] =
    ['CELIBATAIRE', 'MARIE_COMMUNAUTE', 'MARIE_SEPARATION', 'DIVORCE', 'VEUF'];
  readonly coAcqTypeOptions: TypeAcquereur[] = ['RESIDENT_MAROC', 'MRE', 'ETRANGER'];
  readonly coAcqRoleOptions: RoleAcquereur[] = ['CO_ACQUEREUR', 'CONJOINT', 'CO_INVESTISSEUR', 'REPRESENTANT_SCI'];

  // ── Dossier de financement ─────────────────────────────────────────────────
  dossier = signal<DossierFinancement | null>(null);
  showDossierForm = false;
  dossierForm: DossierFinancement = {};
  dossierError = signal('');
  dossierBusy = signal(false);
  readonly statutDossierOptions: StatutDossierFinancement[] =
    ['EN_COURS', 'ACCORD_PRINCIPE', 'ACCORD_DEFINITIF', 'REFUSE'];

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.svc.get(id).subscribe({
      next:  (v) => {
        this.vente.set(v);
        this.maybeLoadReserves(v);
        this.loadCoAcquereur(v.id);
        this.loadDossier(v.id);
      },
      error: ()  => this.error.set('Vente introuvable.'),
    });
  }

  // ── Legal document generation (Loi 44-00) ──────────────────────────────────
  docGenBusy = signal(false);
  docGenError = signal('');

  generateContratReservation(venteId: string): void {
    this.docGenBusy.set(true);
    this.docGenError.set('');
    this.svc.generateContratReservation(venteId).subscribe({
      next: () => { this.docGenBusy.set(false); this.reload(venteId); },
      error: (e) => { this.docGenBusy.set(false);
        this.docGenError.set(e?.error?.message ?? 'Échec de la génération du contrat.'); },
    });
  }

  generatePvLivraison(venteId: string): void {
    this.docGenBusy.set(true);
    this.docGenError.set('');
    this.svc.generatePvLivraison(venteId).subscribe({
      next: () => { this.docGenBusy.set(false); this.reload(venteId); },
      error: (e) => { this.docGenBusy.set(false);
        this.docGenError.set(e?.error?.message ?? 'Échec de la génération du PV.'); },
    });
  }

  private loadDossier(venteId: string): void {
    this.svc.getDossierFinancement(venteId).subscribe({
      next:  (d) => this.dossier.set(d),
      error: ()  => this.dossier.set(null), // 404 = no dossier yet
    });
  }

  openDossierForm(): void {
    const existing = this.dossier();
    this.dossierForm = existing ? { ...existing } : { statut: 'EN_COURS' };
    this.showDossierForm = true;
    this.dossierError.set('');
  }

  saveDossier(venteId: string): void {
    this.dossierBusy.set(true);
    this.dossierError.set('');
    this.svc.upsertDossierFinancement(venteId, this.dossierForm).subscribe({
      next: (d) => { this.dossier.set(d); this.showDossierForm = false; this.dossierBusy.set(false); },
      error: (e) => { this.dossierBusy.set(false);
        this.dossierError.set(e?.error?.message ?? 'Échec de l\'enregistrement du dossier.'); },
    });
  }

  private loadCoAcquereur(venteId: string): void {
    this.svc.listCoAcquereurs(venteId).subscribe({
      next: (list) => this.coAcquereur.set(list.length > 0 ? list[0] : null),
    });
  }

  openCoAcqForm(): void {
    const existing = this.coAcquereur();
    this.coAcqForm = existing ? { ...existing } : { nom: '', prenom: '', roleAcquereur: 'CO_ACQUEREUR' };
    this.showCoAcqForm = true;
    this.coAcqError.set('');
  }

  saveCoAcquereur(venteId: string): void {
    if (!this.coAcqForm.nom?.trim() || !this.coAcqForm.prenom?.trim()) {
      this.coAcqError.set('Nom et prénom sont requis.');
      return;
    }
    this.coAcqBusy.set(true);
    this.coAcqError.set('');
    const existing = this.coAcquereur();
    const obs = existing?.id
      ? this.svc.updateCoAcquereur(venteId, existing.id, this.coAcqForm)
      : this.svc.addCoAcquereur(venteId, this.coAcqForm);
    obs.subscribe({
      next: (c) => { this.coAcquereur.set(c); this.showCoAcqForm = false; this.coAcqBusy.set(false); },
      error: (e) => { this.coAcqBusy.set(false);
        this.coAcqError.set(e?.error?.message ?? 'Échec de l\'enregistrement du co-acquéreur.'); },
    });
  }

  deleteCoAcquereur(venteId: string): void {
    const existing = this.coAcquereur();
    if (!existing?.id || !confirm('Supprimer le co-acquéreur ?')) return;
    this.coAcqBusy.set(true);
    this.svc.deleteCoAcquereur(venteId, existing.id).subscribe({
      next: () => { this.coAcquereur.set(null); this.showCoAcqForm = false; this.coAcqBusy.set(false); },
      error: () => { this.coAcqBusy.set(false); this.coAcqError.set('La suppression a échoué.'); },
    });
  }

  // ── VEFA actions ───────────────────────────────────────────────────────────

  /** True for statuts that can still confirm a reservation (PROSPECT/OPTION). */
  get canConfirmReservation(): boolean {
    const s = this.vente()?.statut;
    return this.canWrite && (s === 'PROSPECT' || s === 'OPTION');
  }
  get canRetract(): boolean {
    return this.canWrite && this.vente()?.statut === 'EN_RETRACTATION';
  }
  get canRecordDelivery(): boolean {
    return this.canWrite && this.vente()?.statut === 'ACTE';
  }
  get showReservesPanel(): boolean {
    const s = this.vente()?.statut;
    return s === 'LIVRE_AVEC_RESERVES' || s === 'RESERVES_LEVEES';
  }

  private maybeLoadReserves(v: Vente): void {
    if (v.statut === 'LIVRE_AVEC_RESERVES' || v.statut === 'RESERVES_LEVEES') {
      this.svc.listReserves(v.id).subscribe({ next: (r) => this.reserves.set(r) });
    }
  }

  private applyVente(v: Vente): void {
    this.vente.set(v);
    this.vefaBusy.set(false);
    this.maybeLoadReserves(v);
  }

  confirmReservation(venteId: string): void {
    this.vefaError.set('');
    this.vefaBusy.set(true);
    this.svc.confirmReservation(venteId, this.depositAmount ?? 0).subscribe({
      next: (v) => { this.showConfirmForm = false; this.depositAmount = null; this.applyVente(v); },
      error: (e) => { this.vefaBusy.set(false);
        this.vefaError.set(e?.error?.message ?? 'La confirmation de réservation a échoué (dépôt > 5% ?).'); },
    });
  }

  exerciseRetractation(venteId: string): void {
    if (!confirm('Confirmer la rétractation ? La vente sera annulée et le bien libéré.')) return;
    this.vefaError.set('');
    this.vefaBusy.set(true);
    this.svc.exerciseRetractation(venteId).subscribe({
      next: (v) => this.applyVente(v),
      error: (e) => { this.vefaBusy.set(false);
        this.vefaError.set(e?.error?.message ?? 'La rétractation est impossible (délai expiré ?).'); },
    });
  }

  recordDelivery(venteId: string): void {
    this.vefaError.set('');
    this.vefaBusy.set(true);
    const reserves = this.deliveryReserves
      .split('\n').map(s => s.trim()).filter(s => s.length > 0);
    this.svc.recordDelivery(venteId, { dateLivraison: this.deliveryDate, reserves }).subscribe({
      next: (v) => { this.showDeliveryForm = false; this.deliveryReserves = ''; this.deliveryDate = null; this.applyVente(v); },
      error: (e) => { this.vefaBusy.set(false);
        this.vefaError.set(e?.error?.message ?? 'L\'enregistrement de la livraison a échoué.'); },
    });
  }

  liftReserve(venteId: string, reserveId: string): void {
    this.vefaBusy.set(true);
    this.svc.liftReserve(venteId, reserveId).subscribe({
      next: (v) => this.applyVente(v),
      error: () => { this.vefaBusy.set(false); this.vefaError.set('La levée de réserve a échoué.'); },
    });
  }

  /** True once the legal échéancier (Art. 618-17) has been generated. */
  hasLegalEcheancier(v: Vente): boolean {
    return v.echeances.some(e => !!e.etape);
  }

  generateEcheancierLegal(venteId: string): void {
    this.vefaError.set('');
    this.vefaBusy.set(true);
    this.svc.generateEcheancierLegal(venteId).subscribe({
      next: () => { this.vefaBusy.set(false); this.reload(venteId); },
      error: (e) => { this.vefaBusy.set(false);
        this.vefaError.set(e?.error?.message ?? 'La génération de l\'échéancier légal a échoué.'); },
    });
  }

  openAdvanceDialog(): void  { this.showAdvanceDialog = true; }
  closeAdvanceDialog(): void { this.showAdvanceDialog = false; }

  saveStatut(id: string, req: UpdateVenteStatutRequest): void {
    this.savingStatut = true;
    this.svc.updateStatut(id, req).subscribe({
      next: (v) => {
        this.vente.set(v);
        this.savingStatut = false;
        this.showAdvanceDialog = false;
      },
      error: () => { this.savingStatut = false; },
    });
  }

  addEcheance(venteId: string): void {
    this.echError.set('');
    if (!this.ech.libelle || !this.ech.montant || !this.ech.dateEcheance) {
      this.echError.set('Tous les champs sont requis.');
      return;
    }
    this.svc.addEcheance(venteId, this.ech).subscribe({
      next: () => {
        this.showEcheanceForm = false;
        this.ech = { libelle: '', montant: 0, dateEcheance: '' };
        this.reload(venteId);
      },
      error: () => this.echError.set('Erreur lors de l\'ajout.'),
    });
  }

  generateQuittance(venteId: string, echeanceId: string): void {
    this.docGenBusy.set(true);
    this.svc.generateQuittance(venteId, echeanceId).subscribe({
      next: () => { this.docGenBusy.set(false); this.reload(venteId); },
      error: () => { this.docGenBusy.set(false); this.docError.set('La génération de la quittance a échoué.'); },
    });
  }

  markPaid(venteId: string, echeanceId: string): void {
    const today = new Date().toISOString().slice(0, 10);
    this.svc.updateEcheanceStatut(venteId, echeanceId, {
      statut: 'PAYEE',
      datePaiement: today,
    }).subscribe({ next: () => this.reload(venteId) });
  }

  inviteBuyer(venteId: string): void {
    this.inviting.set(true);
    this.inviteMsg.set('');
    this.inviteError.set('');
    this.svc.inviteBuyer(venteId).subscribe({
      next:  () => { this.inviting.set(false); this.inviteMsg.set('Invitation envoyée avec succès.'); },
      error: () => { this.inviting.set(false); this.inviteError.set('Erreur lors de l\'envoi.'); },
    });
  }

  onDocFileSelected(event: Event, venteId: string): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.docUploading.set(true);
    this.docError.set('');
    this.svc.uploadDocument(venteId, file).subscribe({
      next: () => { this.docUploading.set(false); this.reload(venteId); },
      error: () => { this.docUploading.set(false); this.docError.set('Échec du téléversement.'); },
    });
    input.value = '';
  }

  downloadDoc(venteId: string, docId: string, fileName: string): void {
    this.svc.downloadDocument(venteId, docId).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = fileName;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.docError.set('Échec du téléchargement.'),
    });
  }

  openPostLivraisonEdit(v: Vente): void {
    this.postLivraisonForm = {
      datePvReception:  v.datePvReception  || null,
      dateTitreFoncier: v.dateTitreFoncier || null,
    };
    this.editingPostLivraison = true;
  }

  savePostLivraison(venteId: string): void {
    this.svc.updateFinancement(venteId, {
      datePvReception:  this.postLivraisonForm.datePvReception  || null,
      dateTitreFoncier: this.postLivraisonForm.dateTitreFoncier || null,
    }).subscribe({
      next: (v) => { this.vente.set(v); this.editingPostLivraison = false; },
      error: () => {},
    });
  }

  generateContract(venteId: string): void {
    this.contractGenerating.set(true);
    this.contractError.set('');
    this.svc.generateContract(venteId).subscribe({
      next:  (v) => { this.vente.set(v); this.contractGenerating.set(false); },
      error: ()  => { this.contractGenerating.set(false); this.contractError.set('Erreur lors de la génération du contrat.'); },
    });
  }

  signContract(venteId: string): void {
    this.contractSigning.set(true);
    this.contractError.set('');
    this.svc.signContract(venteId).subscribe({
      next:  (v) => { this.vente.set(v); this.contractSigning.set(false); },
      error: ()  => { this.contractSigning.set(false); this.contractError.set('Erreur lors de la signature du contrat.'); },
    });
  }

  openFinancingForm(v: Vente): void {
    this.financing = {
      typeFinancement:           v.typeFinancement,
      montantCredit:             v.montantCredit,
      banqueCredit:              v.banqueCredit,
      creditObtenu:              v.creditObtenu,
      dateLimiteFinancement: v.dateLimiteFinancement,
      notaireAcquereurNom:       v.notaireAcquereurNom,
      notaireAcquereurEmail:     v.notaireAcquereurEmail,
    };
    this.showFinancingForm = true;
  }

  saveFinancement(venteId: string): void {
    this.financingSaving.set(true);
    this.financingError.set('');
    this.svc.updateFinancement(venteId, this.financing).subscribe({
      next: (v) => {
        this.vente.set(v);
        this.financingSaving.set(false);
        this.showFinancingForm = false;
      },
      error: () => { this.financingSaving.set(false); this.financingError.set('Erreur lors de la mise à jour.'); },
    });
  }

  isTerminal(s: VenteStatut): boolean {
    return s === 'LIVRE_DEFINITIF' || s === 'ANNULE';
  }

  hasContratGenereDoc(v: Vente): boolean {
    return v.documents.some(d => d.documentType === 'CONTRAT_GENERE');
  }

  private reload(id: string): void {
    this.svc.get(id).subscribe({ next: (v) => this.vente.set(v) });
  }

  statutLabel(s: VenteStatut): string {
    const labels: Record<VenteStatut, string> = {
      PROSPECT: 'Prospect', OPTION: 'Option', RESERVE: 'Réservé',
      EN_RETRACTATION: 'Délai de rétractation', ACOMPTE: 'Acompte',
      COMPROMIS: 'Compromis', FINANCEMENT: 'Financement', ACTE: 'Acte notarié',
      LIVRE_AVEC_RESERVES: 'Livré (réserves)', RESERVES_LEVEES: 'Réserves levées',
      LIVRE_DEFINITIF: 'Livré', ANNULE: 'Annulé',
    };
    return labels[s] ?? s;
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

  echLabel(s: EcheanceStatut): string {
    return { EN_ATTENTE: 'En attente', PAYEE: 'Payée', EN_RETARD: 'En retard' }[s] ?? s;
  }

  echClass(s: EcheanceStatut): string {
    return { EN_ATTENTE: 'badge-info', PAYEE: 'badge-success', EN_RETARD: 'badge-error' }[s] ?? '';
  }

  contractStatusLabel(s: ContractStatus): string {
    return { PENDING: 'En attente', GENERATED: 'Généré', SIGNED: 'Signé' }[s] ?? s;
  }

  contractStatusClass(s: ContractStatus): string {
    return { PENDING: 'badge-secondary', GENERATED: 'badge-info', SIGNED: 'badge-success' }[s] ?? '';
  }

  /** Returns number of days until given ISO date string (negative = past). */
  daysUntil(dateStr: string | null): number | null {
    if (!dateStr) return null;
    const diff = new Date(dateStr).getTime() - Date.now();
    return Math.ceil(diff / 86_400_000);
  }

  deadlineUrgencyClass(dateStr: string | null): string {
    const d = this.daysUntil(dateStr);
    if (d === null) return '';
    if (d < 0)  return 'deadline-overdue';
    if (d <= 3) return 'deadline-critical';
    if (d <= 7) return 'deadline-warning';
    return '';
  }

  financingLabel(t: TypeFinancement | null): string {
    if (!t) return '—';
    const labels: Record<TypeFinancement, string> = {
      COMPTANT:          'Comptant',
      CREDIT_IMMOBILIER: 'Crédit immobilier',
      PTZ:               'Prêt à taux zéro (PTZ)',
      MIXTE:             'Mixte',
    };
    return labels[t];
  }

  motifLabel(m: MotifAnnulation | null): string {
    if (!m) return '—';
    const labels: Record<MotifAnnulation, string> = {
      CREDIT_REFUSE:    'Crédit refusé',
      DESISTEMENT_ACHETEUR: 'Désistement acheteur',
      CSP_NON_REALISEE: 'Condition suspensive non réalisée',
      ACCORD_PARTIES:   'Accord entre parties',
      LITIGE:           'Litige',
      AUTRE:            'Autre',
    };
    return labels[m];
  }

  // ── Deal cockpit synthesis (workflow-first) ─────────────────────
  // These three derived views answer the agent's first three questions on
  // opening a sale: where's the money, what's my next deadline, what's blocking it.

  formatMad(n: number): string {
    return new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 }).format(n) + ' MAD';
  }

  /** Paid / remaining / overdue position, derived from the échéancier and sale price. */
  dealFinancials(v: Vente): DealFinancials {
    const today = new Date().toISOString().slice(0, 10);
    const encaisse = v.echeances
      .filter(e => e.statut === 'PAYEE')
      .reduce((s, e) => s + (e.montant || 0), 0);
    const total = v.prixVente ?? v.echeances.reduce((s, e) => s + (e.montant || 0), 0);
    const overdue = v.echeances.filter(e =>
      e.statut === 'EN_RETARD' || (e.statut === 'EN_ATTENTE' && e.dateEcheance < today));
    return {
      total,
      encaisse,
      reste: total > 0 ? Math.max(0, total - encaisse) : 0,
      pct: total > 0 ? Math.round((encaisse / total) * 100) : 0,
      overdueCount: overdue.length,
      overdueAmount: overdue.reduce((s, e) => s + (e.montant || 0), 0),
      hasEcheances: v.echeances.length > 0,
    };
  }

  /** The soonest pending legal/closing deadline for the current stage. */
  nextDeadline(v: Vente): NextDeadline | null {
    if (this.isTerminal(v.statut)) return null;
    const candidates: Array<{ label: string; date: string | null }> = [];
    if (v.statut === 'COMPROMIS' && v.dateFinDelaiReflexion) {
      candidates.push({ label: 'Fin du délai de rétractation', date: v.dateFinDelaiReflexion });
    }
    if ((v.statut === 'COMPROMIS' || v.statut === 'FINANCEMENT') && v.dateLimiteFinancement) {
      candidates.push({ label: 'Limite d’obtention du financement', date: v.dateLimiteFinancement });
    }
    if (v.statut === 'ACTE' && v.dateLivraisonPrevue) {
      candidates.push({ label: 'Livraison prévue', date: v.dateLivraisonPrevue });
    }
    if (v.expectedClosingDate) {
      candidates.push({ label: 'Clôture prévue', date: v.expectedClosingDate });
    }
    const valid = candidates.filter((c): c is { label: string; date: string } => !!c.date);
    if (valid.length === 0) return null;
    // Prefer the soonest still-pending deadline: an expired informational date
    // (e.g. a reflection period that already ended) must never out-rank an
    // actionable future obligation like the financing deadline. Rank future
    // dates ahead of past ones, then by date ascending within each group.
    const today = new Date().toISOString().slice(0, 10);
    valid.sort((a, b) => {
      const aPast = a.date < today;
      const bPast = b.date < today;
      if (aPast !== bPast) return aPast ? 1 : -1;
      return a.date.localeCompare(b.date);
    });
    const chosen = valid[0];
    return {
      label: chosen.label,
      date: chosen.date,
      days: this.daysUntil(chosen.date) ?? 0,
      urgency: this.deadlineUrgencyClass(chosen.date),
    };
  }

  /** Ordered list of concrete blockers/next steps for the current sale state. */
  attentionItems(v: Vente): AttentionItem[] {
    const items: AttentionItem[] = [];
    const fin = this.dealFinancials(v);

    if (v.statut === 'ANNULE') {
      items.push({
        text: 'Vente annulée' + (v.motifAnnulation ? ' — ' + this.motifLabel(v.motifAnnulation) : ''),
        severity: 'info',
      });
      return items;
    }

    if (v.statut === 'LIVRE_DEFINITIF') {
      if (fin.reste > 0) items.push({ text: 'Solde restant : ' + this.formatMad(fin.reste), severity: 'warning' });
      if (!v.datePvReception) items.push({ text: 'PV de réception non saisi', severity: 'warning' });
      if (!v.dateTitreFoncier) items.push({ text: 'Titre foncier non enregistré', severity: 'info' });
      if (items.length === 0) items.push({ text: 'Dossier complet', severity: 'info' });
      return items;
    }

    const finDays = this.daysUntil(v.dateLimiteFinancement);
    const needsCredit = v.typeFinancement !== 'COMPTANT';

    if (v.statut === 'COMPROMIS' && v.dateFinDelaiReflexion) {
      const d = this.daysUntil(v.dateFinDelaiReflexion);
      if (d !== null && d >= 0) {
        items.push({ text: `Rétractation acheteur possible ${d} j — vente non sécurisée`, severity: 'info' });
      }
    }
    if (finDays !== null && finDays < 0 && !v.creditObtenu && needsCredit) {
      items.push({ text: 'Délai de financement dépassé sans accord — risque de caducité', severity: 'critical' });
    } else if ((v.statut === 'COMPROMIS' || v.statut === 'FINANCEMENT') && !v.creditObtenu && needsCredit) {
      const crit = finDays !== null && finDays <= 7;
      items.push({
        text: v.banqueCredit ? `Accord de crédit en attente (${v.banqueCredit})` : 'Financement à confirmer',
        severity: crit ? 'critical' : 'warning',
      });
    }
    if (v.contractStatus === 'PENDING' && (v.statut === 'FINANCEMENT' || v.statut === 'ACTE')) {
      items.push({ text: 'Contrat de vente non généré', severity: 'warning' });
    }
    if (v.contractStatus === 'GENERATED') {
      items.push({ text: 'Contrat généré — à faire signer', severity: 'info' });
    }
    if (fin.overdueCount > 0) {
      items.push({
        text: `${fin.overdueCount} échéance${fin.overdueCount > 1 ? 's' : ''} en retard (${this.formatMad(fin.overdueAmount)})`,
        severity: 'critical',
      });
    }
    if (!fin.hasEcheances && v.statut !== 'COMPROMIS') {
      items.push({ text: 'Aucun échéancier de paiement défini', severity: 'info' });
    }

    if (items.length === 0) {
      items.push({ text: 'Aucune action en attente — prêt pour l’étape suivante', severity: 'info' });
    }
    // Surface the most severe items first.
    const rank = { critical: 0, warning: 1, info: 2 };
    return items.sort((a, b) => rank[a.severity] - rank[b.severity]);
  }
}
