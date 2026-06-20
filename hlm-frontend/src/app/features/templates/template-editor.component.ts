import {
  AfterViewInit,
  Component,
  ElementRef,
  HostListener,
  inject,
  OnInit,
  ViewChild,
} from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TemplateService } from './template.service';
import { TemplateType } from './template.model';

const TYPE_LABELS: Record<string, string> = {
  CONTRACT: 'Contrat de vente',
  RESERVATION: 'Bon de réservation',
  CALL_FOR_FUNDS: 'Appel de fonds',
};

// Required variable names per template type (legal compliance)
const REQUIRED_VARS: Record<string, string[]> = {
  CONTRACT:       ['societeName', 'buyerName', 'propertyRef', 'agreedPrice', 'venteRef', 'dateCompromis'],
  RESERVATION:    ['buyerName', 'propertyRef', 'agreedPrice', 'depositReference', 'depositAmount', 'depositDate'],
  CALL_FOR_FUNDS: ['buyerDisplayName', 'contractRef', 'amountDue', 'trancheLabel', 'callNumber'],
};

interface TemplateVar  { var: string; desc: string; }
interface VarGroup     { id: string; label: string; icon: string; vars: TemplateVar[]; }
interface SectionPreset { id: string; icon: string; label: string; }
interface Clause       { id: string; icon: string; name: string; desc: string; html: string; }
interface ClauseSection { title: string; clauses: Clause[]; }
interface CheckItem    { varName: string; desc: string; present: boolean; }

@Component({
  selector: 'app-template-editor',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, TranslatePipe],
  templateUrl: './template-editor.component.html',
  styleUrl:    './template-editor.component.css',
})
export class TemplateEditorComponent implements OnInit, AfterViewInit {
  private i18n = inject(I18nService);
  private route  = inject(ActivatedRoute);
  private router = inject(Router);
  private svc    = inject(TemplateService);
  private http   = inject(HttpClient);

  @ViewChild('editor')      editorRef!: ElementRef<HTMLDivElement>;
  @ViewChild('editorScroll') editorScrollRef!: ElementRef<HTMLDivElement>;
  @ViewChild('rawEditor')   rawEditorRef!: ElementRef<HTMLTextAreaElement>;
  @ViewChild('imageInput')  imageInputRef!: ElementRef<HTMLInputElement>;
  @ViewChild('sectionWrap') sectionWrapRef!: ElementRef<HTMLDivElement>;

  type!: TemplateType;
  isCustom       = false;
  isDirty        = false;
  saving         = false;
  imageUploading = false;
  saveError      = '';
  saveSuccess    = false;
  toast          = '';
  searchTerm     = '';
  isDraggingOver = false;
  insertedCount  = 0;
  rawMode        = false;
  rawHtml        = '';
  lastSavedAt: Date | null = null;

  sidebarTab: 'vars' | 'clauses' | 'check' = 'vars';
  sectionMenuOpen = false;
  checkScore   = 0;
  checkItems: CheckItem[] = [];

  private savedRange: Range | null = null;
  private loadedHtml = '';
  private draggingGroupId = '';

  get typeLabel():      string { return this.i18n.instant('templates.docType.' + this.type); }
  get rawToggleTitle(): string { return this.i18n.instant(this.rawMode ? 'templates.editor.rawToggleToVisual' : 'templates.editor.rawToggleToHtml'); }
  get previewHref():    string { return this.svc.previewUrl(this.type); }
  private get editorEl(): HTMLDivElement { return this.editorRef?.nativeElement; }

  // ── Keyboard shortcut Ctrl+S ──────────────────────────────────────
  @HostListener('document:keydown', ['$event'])
  onKeydown(e: KeyboardEvent): void {
    if ((e.ctrlKey || e.metaKey) && e.key === 's') {
      e.preventDefault();
      this.save();
    }
  }

  @HostListener('document:click', ['$event'])
  onDocClick(e: MouseEvent): void {
    if (this.sectionMenuOpen && this.sectionWrapRef &&
        !this.sectionWrapRef.nativeElement.contains(e.target as Node)) {
      this.sectionMenuOpen = false;
    }
  }

  // ── Variable catalog ──────────────────────────────────────────────
  readonly groups: VarGroup[] = [
    {
      id: 'societe', label: this.i18n.instant('templates.varGroup.societe'), icon: '🏢',
      vars: [
        { var: 'societeName',   desc: this.i18n.instant('templates.varDesc.societeName') },
        { var: 'projectName',   desc: this.i18n.instant('templates.varDesc.projectName') },
        { var: 'agentName',     desc: this.i18n.instant('templates.varDesc.agentName') },
        { var: 'agentEmail',    desc: this.i18n.instant('templates.varDesc.agentEmail') },
        { var: 'generatedAt',   desc: this.i18n.instant('templates.varDesc.generatedAt') },
        { var: 'createdAt',     desc: this.i18n.instant('templates.varDesc.createdAt') }],
    },
    {
      id: 'property', label: this.i18n.instant('templates.varGroup.property'), icon: '🏠',
      vars: [
        { var: 'propertyRef',   desc: this.i18n.instant('templates.varDesc.propertyRef') },
        { var: 'propertyTitle', desc: this.i18n.instant('templates.varDesc.propertyTitle') },
        { var: 'propertyType',  desc: this.i18n.instant('templates.varDesc.propertyType') },
        { var: 'agreedPrice',   desc: this.i18n.instant('templates.varDesc.agreedPrice') },
        { var: 'listPrice',     desc: this.i18n.instant('templates.varDesc.listPrice') },
        { var: 'prixVente',     desc: this.i18n.instant('templates.varDesc.prixVente') }],
    },
    {
      id: 'buyer', label: this.i18n.instant('templates.varGroup.buyer'), icon: '👤',
      vars: [
        { var: 'buyerName',        desc: this.i18n.instant('templates.varDesc.buyerName') },
        { var: 'buyerDisplayName', desc: this.i18n.instant('templates.varDesc.buyerDisplayName') },
        { var: 'buyerPhone',       desc: this.i18n.instant('templates.varDesc.buyerPhone') },
        { var: 'buyerEmail',       desc: this.i18n.instant('templates.varDesc.buyerEmail') },
        { var: 'buyerAddress',     desc: this.i18n.instant('templates.varDesc.buyerAddress') },
        { var: 'buyerNationalId',  desc: this.i18n.instant('templates.varDesc.buyerNationalId') },
        { var: 'buyerIce',         desc: this.i18n.instant('templates.varDesc.buyerIce') },
        { var: 'buyerTypeLabel',   desc: this.i18n.instant('templates.varDesc.buyerTypeLabel') }],
    },
    {
      id: 'contract', label: this.i18n.instant('templates.varGroup.contract'), icon: '📄',
      vars: [
        { var: 'venteRef',           desc: this.i18n.instant('templates.varDesc.venteRef') },
        { var: 'contractRef',        desc: this.i18n.instant('templates.varDesc.contractRef') },
        { var: 'contractStatus',     desc: this.i18n.instant('templates.varDesc.contractStatus') },
        { var: 'statut',             desc: this.i18n.instant('templates.varDesc.statut') },
        { var: 'dateCompromis',      desc: this.i18n.instant('templates.varDesc.dateCompromis') },
        { var: 'dateActeNotarie',    desc: this.i18n.instant('templates.varDesc.dateActeNotarie') },
        { var: 'dateLivraisonPrevue', desc: this.i18n.instant('templates.varDesc.dateLivraisonPrevue') },
        { var: 'signedAt',           desc: this.i18n.instant('templates.varDesc.signedAt') },
        { var: 'depositReference',   desc: this.i18n.instant('templates.varDesc.depositReference') },
        { var: 'depositAmount',      desc: this.i18n.instant('templates.varDesc.depositAmount') },
        { var: 'depositDate',        desc: this.i18n.instant('templates.varDesc.depositDate') },
        { var: 'dueDate',            desc: this.i18n.instant('templates.varDesc.dueDate') },
        { var: 'notes',              desc: this.i18n.instant('templates.varDesc.notes') }],
    }];

  filteredGroups: VarGroup[] = this.groups;

  // ── Section presets ────────────────────────────────────────────────
  readonly sectionPresets: SectionPreset[] = [
    { id: 'article',   icon: '§',  label: this.i18n.instant('templates.preset.article') },
    { id: 'signature', icon: '✍️', label: this.i18n.instant('templates.preset.signature') },
    { id: 'pagebreak', icon: '📄', label: this.i18n.instant('templates.preset.pagebreak') },
    { id: 'separator', icon: '—',  label: this.i18n.instant('templates.preset.separator') },
    { id: 'tableinfo', icon: '📊', label: this.i18n.instant('templates.preset.tableinfo') }];

  // ── Legal clause library ──────────────────────────────────────────
  readonly clauseSections: ClauseSection[] = [
    {
      title: this.i18n.instant('templates.clauseSection.conditions'),
      clauses: [
        {
          id: 'cs-financement',
          icon: '🏦', name: this.i18n.instant('templates.clause.cs-financement.name'),
          desc: this.i18n.instant('templates.clause.cs-financement.desc'),
          html: `<h3>CONDITION SUSPENSIVE D'OBTENTION DE FINANCEMENT</h3>
<p>La présente vente est conclue sous la condition suspensive de l'obtention par l'acquéreur d'un prêt immobilier d'un montant minimum de <strong>[MONTANT À COMPLÉTER]</strong> dirhams (MAD), auprès de tout établissement bancaire ou organisme de crédit de son choix, dans les conditions habituelles du marché.</p>
<p>L'acquéreur s'engage à accomplir toutes les démarches nécessaires auprès des organismes prêteurs dans un délai de <strong>soixante (60) jours</strong> à compter de la date de signature des présentes. Passé ce délai, si la condition ne s'est pas réalisée, toutes les sommes versées seront intégralement restituées à l'acquéreur, sans pénalité ni frais.</p>`,
        },
        {
          id: 'cs-permis',
          icon: '📜', name: this.i18n.instant('templates.clause.cs-permis.name'),
          desc: this.i18n.instant('templates.clause.cs-permis.desc'),
          html: `<h3>CONDITION SUSPENSIVE D'OBTENTION D'AUTORISATION</h3>
<p>La présente vente est conclue sous la condition suspensive de l'obtention par le vendeur des autorisations administratives nécessaires à la réalisation de l'opération immobilière objet du présent acte, dans un délai de <strong>[DÉLAI]</strong> à compter de la signature.</p>
<p>En cas de non-obtention de ladite autorisation dans le délai imparti, le présent contrat sera réputé nul et non avenu, et toutes les sommes versées par l'acquéreur lui seront remboursées sans retard.</p>`,
        }],
    },
    {
      title: this.i18n.instant('templates.clauseSection.garanties'),
      clauses: [
        {
          id: 'garanties-legales',
          icon: '🛡️', name: this.i18n.instant('templates.clause.garanties-legales.name'),
          desc: this.i18n.instant('templates.clause.garanties-legales.desc'),
          html: `<h3>GARANTIES LÉGALES</h3>
<p>Le vendeur garantit l'acquéreur contre tous vices cachés qui rendraient le bien immobilier impropre à l'usage auquel il est destiné, ou qui diminueraient tellement cet usage que l'acquéreur ne l'aurait pas acquis ou n'en aurait donné qu'un moindre prix s'il les avait connus, conformément aux dispositions du Dahir des Obligations et Contrats.</p>
<p>Le vendeur déclare et garantit que le bien est libre de toute servitude non apparente, hypothèque, inscription ou charge quelconque pouvant nuire à la libre jouissance de l'acquéreur, sauf celles expressément mentionnées aux présentes.</p>`,
        },
        {
          id: 'clause-penale',
          icon: '⚠️', name: this.i18n.instant('templates.clause.clause-penale.name'),
          desc: this.i18n.instant('templates.clause.clause-penale.desc'),
          html: `<h3>CLAUSE PÉNALE</h3>
<p>En cas d'inexécution par l'une des parties de ses obligations aux termes du présent contrat, la partie défaillante sera redevable envers l'autre partie d'une indemnité forfaitaire égale à <strong>dix pour cent (10 %)</strong> du prix de vente convenu, à titre de dommages et intérêts, sans préjudice du droit pour la partie lésée de demander l'exécution forcée du contrat ou sa résolution judiciaire.</p>
<p>Cette indemnité est de plein droit et ne nécessite ni mise en demeure préalable ni intervention judiciaire pour être acquise.</p>`,
        }],
    },
    {
      title: this.i18n.instant('templates.clauseSection.dispositions'),
      clauses: [
        {
          id: 'force-majeure',
          icon: '🌪️', name: this.i18n.instant('templates.clause.force-majeure.name'),
          desc: this.i18n.instant('templates.clause.force-majeure.desc'),
          html: `<h3>FORCE MAJEURE</h3>
<p>Aucune des parties ne pourra être tenue responsable d'un retard ou d'un manquement à ses obligations contractuelles résultant d'un événement constitutif de force majeure au sens de l'article 269 du Dahir formant Code des Obligations et des Contrats.</p>
<p>La partie invoquant la force majeure devra en informer l'autre partie par écrit dans les <strong>soixante-douze (72) heures</strong> suivant la survenance de l'événement. Les obligations des parties seront suspendues pendant la durée de l'événement de force majeure.</p>`,
        },
        {
          id: 'election-domicile',
          icon: '📍', name: this.i18n.instant('templates.clause.election-domicile.name'),
          desc: this.i18n.instant('templates.clause.election-domicile.desc'),
          html: `<h3>ÉLECTION DE DOMICILE</h3>
<p>Pour l'exécution des présentes et de leurs suites, les parties font élection de domicile à leurs adresses respectives telles qu'indiquées en tête du présent acte.</p>
<p>Tout changement d'adresse devra être notifié à l'autre partie par lettre recommandée avec accusé de réception dans un délai de <strong>quinze (15) jours</strong> ouvrables. À défaut de notification, toute correspondance adressée à la dernière adresse connue sera réputée valablement délivrée.</p>`,
        },
        {
          id: 'mediation',
          icon: '🤝', name: this.i18n.instant('templates.clause.mediation.name'),
          desc: this.i18n.instant('templates.clause.mediation.desc'),
          html: `<h3>MÉDIATION PRÉALABLE</h3>
<p>En cas de litige portant sur l'interprétation ou l'exécution du présent contrat, les parties s'engagent à rechercher, avant toute action judiciaire, une solution amiable par voie de médiation auprès d'un médiateur agréé, désigné d'un commun accord ou à défaut par le Tribunal compétent.</p>
<p>Cette tentative de médiation préalable est obligatoire. Sa durée ne saurait excéder <strong>trente (30) jours</strong> à compter de la saisine du médiateur, sauf accord des parties pour la prolonger.</p>`,
        }],
    },
    {
      title: this.i18n.instant('templates.clauseSection.paiement'),
      clauses: [
        {
          id: 'modalites-paiement',
          icon: '💰', name: this.i18n.instant('templates.clause.modalites-paiement.name'),
          desc: this.i18n.instant('templates.clause.modalites-paiement.desc'),
          html: `<h3>MODALITÉS DE PAIEMENT</h3>
<p>Le prix de vente convenu sera réglé selon les modalités suivantes :</p>
<ul>
  <li><strong>À la signature :</strong> [MONTANT] dirhams à titre d'acompte, représentant [%] % du prix total ;</li>
  <li><strong>À [JALON] :</strong> [MONTANT] dirhams, représentant [%] % du prix total ;</li>
  <li><strong>Solde à la livraison :</strong> [MONTANT] dirhams, représentant [%] % du prix total.</li>
</ul>
<p>Tout retard de paiement donnera lieu, de plein droit et sans mise en demeure préalable, à des pénalités de retard calculées au taux légal en vigueur, à compter du lendemain de la date d'échéance.</p>`,
        },
        {
          id: 'penalites-retard',
          icon: '⏰', name: this.i18n.instant('templates.clause.penalites-retard.name'),
          desc: this.i18n.instant('templates.clause.penalites-retard.desc'),
          html: `<h3>PÉNALITÉS DE RETARD</h3>
<p>Tout retard de paiement d'une échéance, quelle qu'en soit la cause, donnera lieu de plein droit et sans mise en demeure préalable à l'application de pénalités de retard calculées au taux de <strong>[TAUX] %</strong> par mois de retard, calculées sur le montant de l'échéance en souffrance.</p>
<p>Ces pénalités seront dues à compter du premier jour suivant la date d'échéance jusqu'au paiement intégral des sommes dues, en principal et intérêts.</p>`,
        }],
    }];

  // ── Lifecycle ──────────────────────────────────────────────────────
  ngOnInit(): void {
    this.type = this.route.snapshot.paramMap.get('type') as TemplateType;
    this.svc.getSource(this.type).subscribe({
      next: res => {
        this.loadedHtml = res.htmlContent;
        this.isCustom   = res.custom;
        if (this.editorRef?.nativeElement) {
          this.loadIntoEditor(res.htmlContent);
        }
      },
      error: () => { this.saveError = this.i18n.instant('templates.editor.loadError'); },
    });
  }

  ngAfterViewInit(): void {
    if (this.loadedHtml && this.editorRef?.nativeElement) {
      this.loadIntoEditor(this.loadedHtml);
    }
  }

  // ── Toggle raw / WYSIWYG ───────────────────────────────────────────
  toggleRaw(): void {
    if (!this.rawMode) {
      this.rawHtml = this.serializeContent();
      this.rawMode = true;
    } else {
      this.rawMode = false;
      setTimeout(() => {
        if (this.editorRef?.nativeElement) {
          this.loadIntoEditor(this.rawHtml);
        }
      });
    }
  }

  // ── Load / serialize ───────────────────────────────────────────────
  private loadIntoEditor(html: string): void {
    const doc = new DOMParser().parseFromString(html, 'text/html');
    this.replaceTokensInTextNodes(doc.body);
    this.editorEl.innerHTML = doc.body.innerHTML;
    this.recountInserted();
    this.isDirty = false;
  }

  private replaceTokensInTextNodes(parent: Node): void {
    for (const node of Array.from(parent.childNodes)) {
      if (node.nodeType === Node.TEXT_NODE) {
        this.replaceTokensInTextNode(node as Text);
      } else if (node.nodeType === Node.ELEMENT_NODE) {
        const tag = (node as Element).tagName;
        if (tag !== 'SCRIPT' && tag !== 'STYLE') {
          this.replaceTokensInTextNodes(node);
        }
      }
    }
  }

  private replaceTokensInTextNode(textNode: Text): void {
    const text = textNode.textContent ?? '';
    if (!text.includes('${model.')) return;
    const re = /\$\{model\.([a-zA-Z0-9_]+)\}/g;
    const fragment = document.createDocumentFragment();
    let lastIndex = 0, match: RegExpExecArray | null;
    while ((match = re.exec(text)) !== null) {
      if (match.index > lastIndex) {
        fragment.appendChild(document.createTextNode(text.slice(lastIndex, match.index)));
      }
      fragment.appendChild(this.makeChip(match[1]));
      lastIndex = re.lastIndex;
    }
    if (lastIndex < text.length) {
      fragment.appendChild(document.createTextNode(text.slice(lastIndex)));
    }
    textNode.parentNode?.replaceChild(fragment, textNode);
  }

  private serializeContent(): string {
    if (this.rawMode) return this.rawHtml;
    if (!this.editorEl) return '';
    const clone = this.editorEl.cloneNode(true) as HTMLElement;
    clone.querySelectorAll('span[data-var]').forEach(span => {
      const varName = span.getAttribute('data-var')!;
      span.replaceWith(`\${model.${varName}}`);
    });
    return clone.innerHTML;
  }

  private buildVarMap(): Record<string, string> {
    const map: Record<string, string> = {};
    for (const g of this.groups) for (const v of g.vars) { map[v.var] = v.desc; }
    return map;
  }

  // ── Dirty tracking ─────────────────────────────────────────────────
  onEditorInput(): void {
    this.markDirty();
    this.recountInserted();
  }

  markDirty(): void {
    this.isDirty = true;
  }

  // ── Actions ────────────────────────────────────────────────────────
  save(): void {
    if (this.saving) return;
    this.saving     = true;
    this.saveError  = '';
    this.saveSuccess = false;
    const content = this.serializeContent();
    this.svc.upsert(this.type, content).subscribe({
      next: () => {
        this.saving      = false;
        this.isCustom    = true;
        this.isDirty     = false;
        this.lastSavedAt = new Date();
        this.saveSuccess = true;
        setTimeout(() => this.saveSuccess = false, 3000);
      },
      error: (err) => {
        this.saving    = false;
        this.saveError = err?.error?.message ?? this.i18n.instant('templates.editor.saveError');
      },
    });
  }

  revert(): void {
    if (!confirm(`Réinitialiser "${this.typeLabel}" vers le modèle intégré ? Cette action est irréversible.`)) return;
    this.svc.delete(this.type).subscribe({
      next: () => { this.isCustom = false; this.router.navigateByUrl('/app/templates'); },
      error: (err) => { this.saveError = err?.error?.message ?? this.i18n.instant('templates.editor.revertError'); },
    });
  }

  // ── Formatting commands ────────────────────────────────────────────
  exec(cmd: string, val?: string): void {
    this.editorEl?.focus();
    document.execCommand(cmd, false, val ?? '');
  }

  execFormatBlock(tag: string): void {
    if (!tag) return;
    this.editorEl?.focus();
    document.execCommand('formatBlock', false, tag);
  }

  // ── Section / Clause insertion ────────────────────────────────────
  insertSection(id: string): void {
    this.editorEl?.focus();
    this.restoreSelection();
    let html = '';
    switch (id) {
      case 'article':
        html = `<h2>ARTICLE [N°] — [TITRE DE L'ARTICLE]</h2><p>Texte de l'article…</p>`;
        break;
      case 'signature':
        html = `<hr/>
<table style="width:100%;border:none;margin-top:40px">
  <tr>
    <td style="border:none;width:50%;padding:0 20px 0 0;vertical-align:top">
      <p style="font-weight:bold">Pour le VENDEUR / L'AGENT</p>
      <p style="color:#94a3b8;font-size:12px">Nom, qualité, signature et cachet</p>
      <div style="border:1px solid #e2e8f0;min-height:70px;margin-top:12px;padding:8px"></div>
      <p style="font-size:11px;color:#64748b;margin-top:8px">Fait à _____________, le _____________</p>
    </td>
    <td style="border:none;width:50%;padding:0 0 0 20px;vertical-align:top">
      <p style="font-weight:bold">Pour l'ACHETEUR / L'ACQUÉREUR</p>
      <p style="color:#94a3b8;font-size:12px">Nom, qualité, signature et mention manuscrite «&nbsp;Lu et approuvé&nbsp;»</p>
      <div style="border:1px solid #e2e8f0;min-height:70px;margin-top:12px;padding:8px"></div>
      <p style="font-size:11px;color:#64748b;margin-top:8px">Fait à _____________, le _____________</p>
    </td>
  </tr>
</table>`;
        break;
      case 'pagebreak':
        html = `<div style="page-break-after:always;border-top:1px dashed #e2e8f0;margin:20px 0;padding-top:4px"><span style="font-size:10px;color:#cbd5e1">— Saut de page —</span></div>`;
        break;
      case 'separator':
        html = `<hr/>`;
        break;
      case 'tableinfo':
        html = `<table style="width:100%;border-collapse:collapse;margin:12px 0">
  <tr style="background:#f8fafc"><th style="border:1px solid #e2e8f0;padding:7px 10px;text-align:left;font-size:12px">Élément</th><th style="border:1px solid #e2e8f0;padding:7px 10px;text-align:left;font-size:12px">Valeur</th></tr>
  <tr><td style="border:1px solid #e2e8f0;padding:7px 10px;font-size:12px;color:#64748b">Référence du bien</td><td style="border:1px solid #e2e8f0;padding:7px 10px;font-size:12px"></td></tr>
  <tr style="background:#f8fafc"><td style="border:1px solid #e2e8f0;padding:7px 10px;font-size:12px;color:#64748b">Prix convenu</td><td style="border:1px solid #e2e8f0;padding:7px 10px;font-size:12px"></td></tr>
  <tr><td style="border:1px solid #e2e8f0;padding:7px 10px;font-size:12px;color:#64748b">Nom de l'acquéreur</td><td style="border:1px solid #e2e8f0;padding:7px 10px;font-size:12px"></td></tr>
</table>`;
        break;
    }
    if (!html) return;

    const tempDiv = document.createElement('div');
    tempDiv.innerHTML = html;
    const sel = window.getSelection();
    if (sel && sel.rangeCount > 0) {
      const range = sel.getRangeAt(0);
      if (this.editorEl.contains(range.commonAncestorContainer)) {
        range.deleteContents();
        const frag = document.createDocumentFragment();
        while (tempDiv.firstChild) frag.appendChild(tempDiv.firstChild);
        range.insertNode(frag);
        range.collapse(false);
        sel.removeAllRanges();
        sel.addRange(range);
        this.savedRange = range.cloneRange();
        this.markDirty();
        return;
      }
    }
    while (tempDiv.firstChild) this.editorEl.appendChild(tempDiv.firstChild);
    this.markDirty();
  }

  insertClause(clauseId: string): void {
    if (this.rawMode) return;
    let clause: Clause | undefined;
    for (const s of this.clauseSections) {
      clause = s.clauses.find(c => c.id === clauseId);
      if (clause) break;
    }
    if (!clause) return;
    this.editorEl?.focus();
    this.restoreSelection();
    const tempDiv = document.createElement('div');
    tempDiv.innerHTML = clause.html;
    const sel = window.getSelection();
    if (sel && sel.rangeCount > 0) {
      const range = sel.getRangeAt(0);
      if (this.editorEl.contains(range.commonAncestorContainer)) {
        range.deleteContents();
        const frag = document.createDocumentFragment();
        while (tempDiv.firstChild) frag.appendChild(tempDiv.firstChild);
        range.insertNode(frag);
        range.collapse(false);
        sel.removeAllRanges();
        sel.addRange(range);
        this.savedRange = range.cloneRange();
        this.markDirty();
        this.flashToast(this.i18n.instant('templates.editor.clauseInserted', { name: clause.name }));
        return;
      }
    }
    while (tempDiv.firstChild) this.editorEl.appendChild(tempDiv.firstChild);
    this.markDirty();
    this.flashToast(this.i18n.instant('templates.editor.clauseInserted', { name: clause.name }));
  }

  // ── Selection management ───────────────────────────────────────────
  saveSelection(): void {
    const sel = window.getSelection();
    if (sel && sel.rangeCount > 0) {
      this.savedRange = sel.getRangeAt(0).cloneRange();
    }
  }

  private restoreSelection(): boolean {
    if (!this.savedRange) return false;
    const sel = window.getSelection();
    if (!sel) return false;
    sel.removeAllRanges();
    sel.addRange(this.savedRange);
    return true;
  }

  // ── Variable insertion ─────────────────────────────────────────────
  onVarClick(varName: string): void {
    if (this.rawMode) {
      const token = `\${model.${varName}}`;
      navigator.clipboard?.writeText(token).catch(() => {});
      this.flashToast(`Copié : \${model.${varName}}`);
      return;
    }
    this.insertVar(varName);
  }

  insertVar(varName: string): void {
    const chip = this.makeChip(varName);
    this.editorEl.focus();
    this.restoreSelection();
    const sel = window.getSelection();
    if (sel && sel.rangeCount > 0) {
      const range = sel.getRangeAt(0);
      if (this.editorEl.contains(range.commonAncestorContainer)) {
        range.deleteContents();
        range.insertNode(chip);
        range.setStartAfter(chip);
        range.collapse(true);
        sel.removeAllRanges();
        sel.addRange(range);
        this.savedRange = range.cloneRange();
        this.recountInserted();
        this.markDirty();
        this.flashToast(this.i18n.instant('templates.editor.inserted', { text: chip.textContent }));
        return;
      }
    }
    this.editorEl.appendChild(chip);
    this.recountInserted();
    this.markDirty();
    this.flashToast(this.i18n.instant('templates.editor.inserted', { text: chip.textContent }));
  }

  private makeChip(varName: string): HTMLSpanElement {
    const groupId = this.groups.find(g => g.vars.some(v => v.var === varName))?.id ?? '';
    const desc    = this.buildVarMap()[varName] ?? varName;
    const span    = document.createElement('span');
    span.className = 'var-pill';
    span.setAttribute('data-var', varName);
    span.setAttribute('data-group', groupId);
    span.setAttribute('contenteditable', 'false');
    span.textContent = desc;
    return span;
  }

  // ── Drag & drop ────────────────────────────────────────────────────
  onDragStart(ev: DragEvent, varName: string, groupId: string): void {
    ev.dataTransfer?.setData('application/x-var', varName);
    ev.dataTransfer?.setData('application/x-group', groupId);
    if (ev.dataTransfer) ev.dataTransfer.effectAllowed = 'copy';
    this.draggingGroupId = groupId;
  }

  onDragOver(ev: DragEvent): void {
    ev.preventDefault();
    if (ev.dataTransfer) ev.dataTransfer.dropEffect = 'copy';
    this.isDraggingOver = true;
  }

  onDragLeave(_ev: DragEvent): void { this.isDraggingOver = false; }
  onDragEnd():  void                { this.isDraggingOver = false; this.draggingGroupId = ''; }

  onDrop(ev: DragEvent): void {
    ev.preventDefault();
    this.isDraggingOver = false;
    const varName = ev.dataTransfer?.getData('application/x-var');
    if (!varName) return;
    const chip = this.makeChip(varName);
    const docAny = document as Document & {
      caretPositionFromPoint?: (x: number, y: number) => { offsetNode: Node; offset: number } | null;
      caretRangeFromPoint?:    (x: number, y: number) => Range | null;
    };
    let range: Range | null = null;
    if (docAny.caretRangeFromPoint) {
      range = docAny.caretRangeFromPoint(ev.clientX, ev.clientY);
    } else if (docAny.caretPositionFromPoint) {
      const cp = docAny.caretPositionFromPoint(ev.clientX, ev.clientY);
      if (cp) { range = document.createRange(); range.setStart(cp.offsetNode, cp.offset); range.collapse(true); }
    }
    if (range && this.editorEl.contains(range.commonAncestorContainer)) {
      range.insertNode(chip);
      range.setStartAfter(chip);
      range.collapse(true);
      const sel = window.getSelection();
      sel?.removeAllRanges();
      sel?.addRange(range);
      this.savedRange = range.cloneRange();
    } else {
      this.editorEl.appendChild(chip);
    }
    this.editorEl.focus();
    this.recountInserted();
    this.markDirty();
    this.flashToast(this.i18n.instant('templates.editor.inserted', { text: chip.textContent }));
  }

  // ── Search / filter ────────────────────────────────────────────────
  filterVars(): void {
    const q = this.searchTerm.trim().toLowerCase();
    if (!q) { this.filteredGroups = this.groups; return; }
    this.filteredGroups = this.groups
      .map(g => ({ ...g, vars: g.vars.filter(v =>
        v.var.toLowerCase().includes(q) || v.desc.toLowerCase().includes(q)) }))
      .filter(g => g.vars.length > 0);
  }

  // ── Required variable helpers ──────────────────────────────────────
  isRequired(varName: string): boolean {
    return (REQUIRED_VARS[this.type] ?? []).includes(varName);
  }

  // ── Legal check ────────────────────────────────────────────────────
  computeCheck(): void {
    const required = REQUIRED_VARS[this.type] ?? [];
    const html     = this.serializeContent();
    const present  = new Set<string>();
    for (const g of this.groups) {
      for (const v of g.vars) {
        if (html.includes(`data-var="${v.var}"`) || html.includes(`\${model.${v.var}}`)) {
          present.add(v.var);
        }
      }
    }
    const varMap = this.buildVarMap();
    this.checkItems = required.map(vn => ({
      varName: vn, desc: varMap[vn] ?? vn, present: present.has(vn),
    }));
    const presentCount = this.checkItems.filter(i => i.present).length;
    this.checkScore = required.length > 0 ? Math.round((presentCount / required.length) * 100) : 100;
  }

  // ── Image upload ───────────────────────────────────────────────────
  triggerImagePicker(): void {
    this.imageInputRef.nativeElement.value = '';
    this.imageInputRef.nativeElement.click();
  }

  onImageFileSelected(ev: Event): void {
    const file = (ev.target as HTMLInputElement).files?.[0];
    if (!file) return;
    if (file.size > 3 * 1024 * 1024) { this.flashToast(this.i18n.instant('templates.editor.imageTooLarge')); return; }
    const form = new FormData();
    form.append('file', file);
    this.imageUploading = true;
    this.saveSelection();
    this.http.post<{ dataUri: string }>('/api/templates/images', form).subscribe({
      next: ({ dataUri }) => { this.imageUploading = false; this.insertImage(dataUri, file.name); },
      error: () => { this.imageUploading = false; this.flashToast(this.i18n.instant('templates.editor.imageError')); },
    });
  }

  private insertImage(dataUri: string, name: string): void {
    const img = document.createElement('img');
    img.src = dataUri;
    img.alt = name.replace(/\.[^.]+$/, '');
    img.style.cssText = 'max-width:200px;height:auto;display:block;margin:8px 0;';
    this.editorEl.focus();
    this.restoreSelection();
    const sel = window.getSelection();
    if (sel && sel.rangeCount > 0) {
      const range = sel.getRangeAt(0);
      if (this.editorEl.contains(range.commonAncestorContainer)) {
        range.deleteContents();
        range.insertNode(img);
        range.setStartAfter(img);
        range.collapse(true);
        sel.removeAllRanges();
        sel.addRange(range);
        this.savedRange = range.cloneRange();
        this.markDirty();
        return;
      }
    }
    this.editorEl.insertBefore(img, this.editorEl.firstChild);
    this.markDirty();
  }

  // ── Helpers ────────────────────────────────────────────────────────
  recountInserted(): void {
    this.insertedCount = this.editorEl?.querySelectorAll('span[data-var]').length ?? 0;
  }

  varToken(varName: string): string { return '${model.' + varName + '}'; }

  private flashToast(msg: string): void {
    this.toast = msg;
    setTimeout(() => this.toast = '', 2000);
  }
}
