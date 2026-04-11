import { Component, ElementRef, inject, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { TemplateService } from './template.service';
import { TemplateType } from './template.model';

const TYPE_LABELS: Record<string, string> = {
  CONTRACT: 'Contrat de vente',
  RESERVATION: 'Bon de réservation',
  CALL_FOR_FUNDS: 'Appel de fonds',
};

interface TemplateVar {
  var: string;
  desc: string;
}

interface VarGroup {
  id: string;
  label: string;
  icon: string;
  vars: TemplateVar[];
}

@Component({
  selector: 'app-template-editor',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TranslateModule],
  template: `
    <!-- ── Page header ─────────────────────────────────────── -->
    <div class="ed-topbar">
      <div class="ed-topbar-left">
        <a routerLink="/app/templates" class="back-link">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M9 2L5 7l4 5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          Retour aux modèles
        </a>
        <div class="ed-title-row">
          <h1 class="ed-title">{{ typeLabel }}</h1>
          @if (isCustom) {
            <span class="badge-custom">Personnalisé</span>
          } @else {
            <span class="badge-default">Modèle intégré</span>
          }
        </div>
        <p class="ed-help">
          Glissez-déposez les variables depuis le panneau de droite vers votre modèle,
          ou cliquez pour les insérer à la position du curseur.
        </p>
      </div>

      <div class="ed-toolbar">
        <button (click)="save()" [disabled]="saving" class="btn btn-primary">
          <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
            <path d="M2 7l3 3 6-6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          {{ saving ? 'Enregistrement…' : 'Enregistrer' }}
        </button>
        <a [href]="previewHref" target="_blank" rel="noopener noreferrer" class="btn btn-outline">
          <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
            <path d="M1.5 6.5C1.5 6.5 3.5 2.5 6.5 2.5S11.5 6.5 11.5 6.5 9.5 10.5 6.5 10.5 1.5 6.5 1.5 6.5z" stroke="currentColor" stroke-width="1.3"/>
            <circle cx="6.5" cy="6.5" r="1.5" stroke="currentColor" stroke-width="1.3"/>
          </svg>
          Aperçu PDF
        </a>
        @if (isCustom) {
          <button (click)="revert()" class="btn btn-danger-soft">Réinitialiser</button>
        }
        @if (saveError) { <span class="msg-error">{{ saveError }}</span> }
        @if (saveSuccess) { <span class="msg-success">✓ Enregistré</span> }
      </div>
    </div>

    <!-- ── Two-pane layout ─────────────────────────────────── -->
    <div class="ed-layout">

      <!-- LEFT — text editor (drop target) -->
      <div class="ed-pane-editor">
        <div class="ed-pane-label">
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
            <path d="M3 4L1 6l2 2M9 4l2 2-2 2M7 2l-2 8" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          Modèle HTML — déposez les variables ici
          @if (insertedCount > 0) {
            <span class="ed-counter">{{ insertedCount }} variable{{ insertedCount > 1 ? 's' : '' }} insérée{{ insertedCount > 1 ? 's' : '' }}</span>
          }
        </div>
        <textarea
          #editor
          class="html-editor"
          [class.dragging]="isDraggingOver"
          [(ngModel)]="htmlContent"
          (input)="recountInserted()"
          (dragover)="onDragOver($event)"
          (dragleave)="onDragLeave($event)"
          (drop)="onDrop($event)"
          spellcheck="false"
          placeholder="Glissez les variables depuis la droite, ou écrivez votre HTML Thymeleaf ici…"
        ></textarea>
      </div>

      <!-- RIGHT — variable palette -->
      <div class="ed-pane-ref">
        <div class="ed-pane-label">
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
            <circle cx="6" cy="6" r="5" stroke="currentColor" stroke-width="1.3"/>
            <path d="M6 5.5v3M6 3.5v.5" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
          </svg>
          Bibliothèque de variables
        </div>

        <input
          type="text"
          class="ref-search"
          placeholder="Filtrer (ex. prix, acheteur)…"
          [(ngModel)]="searchTerm"
          (input)="filterVars()"
        />

        @for (g of filteredGroups; track g.id) {
          <div class="ref-group">
            <div class="ref-group-head">
              <span class="ref-group-icon">{{ g.icon }}</span>
              <span class="ref-group-label">{{ g.label }}</span>
              <span class="ref-group-count">{{ g.vars.length }}</span>
            </div>
            <div class="ref-group-body">
              @for (v of g.vars; track v.var) {
                <div
                  class="var-chip"
                  draggable="true"
                  (dragstart)="onDragStart($event, v.var)"
                  (dragend)="onDragEnd()"
                  (click)="insertAtCaret(v.var)"
                  [title]="'Glisser ou cliquer pour insérer ' + v.desc">
                  <span class="var-chip-token">{{ v.var }}</span>
                  <span class="var-chip-desc">{{ v.desc }}</span>
                </div>
              }
            </div>
          </div>
        }

        @if (filteredGroups.length === 0) {
          <div class="ref-empty">Aucune variable ne correspond à « {{ searchTerm }} »</div>
        }

        @if (toast) {
          <div class="toast">{{ toast }}</div>
        }
      </div>
    </div>
  `,
  styles: [`
    /* ── Topbar ─────────────────────────────────────────── */
    .ed-topbar { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
    .ed-topbar-left { display: flex; flex-direction: column; gap: 6px; max-width: 640px; }
    .back-link { display: inline-flex; align-items: center; gap: 5px; color: #2563eb; font-size: 12px; text-decoration: none; }
    .back-link:hover { text-decoration: underline; }
    .ed-title-row { display: flex; align-items: center; gap: 10px; }
    .ed-title { font-size: 18px; font-weight: 700; color: #1e293b; margin: 0; }
    .ed-help { font-size: 12px; color: #64748b; margin: 0; line-height: 1.45; }
    .badge-custom { background: #dbeafe; color: #1d4ed8; font-size: 11px; padding: 2px 8px; border-radius: 99px; font-weight: 600; }
    .badge-default { background: #f1f5f9; color: #64748b; font-size: 11px; padding: 2px 8px; border-radius: 99px; }
    .ed-toolbar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }

    /* ── Two-pane layout ─────────────────────────────────── */
    .ed-layout { display: grid; grid-template-columns: 1fr 300px; gap: 16px; height: calc(100vh - 200px); min-height: 500px; }
    .ed-pane-editor { display: flex; flex-direction: column; gap: 6px; }
    .ed-pane-ref { display: flex; flex-direction: column; gap: 8px; overflow-y: auto; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px; background: #f8fafc; }
    .ed-pane-label { display: flex; align-items: center; gap: 5px; font-size: 11px; font-weight: 600; color: #64748b; text-transform: uppercase; letter-spacing: 0.04em; margin-bottom: 2px; }
    .ed-counter { margin-left: auto; background: #dcfce7; color: #15803d; font-size: 10px; font-weight: 700; padding: 2px 8px; border-radius: 99px; text-transform: none; letter-spacing: 0; }

    /* ── Code editor ─────────────────────────────────────── */
    .html-editor { flex: 1; font-family: 'Courier New', Consolas, monospace; font-size: 12.5px; line-height: 1.65; border: 2px dashed transparent; border-color: #cbd5e1; border-radius: 8px; padding: 14px; resize: none; background: #0f172a; color: #e2e8f0; outline: none; box-sizing: border-box; tab-size: 2; transition: border-color .15s, box-shadow .15s; }
    .html-editor:focus { border-color: #2563eb; border-style: solid; box-shadow: 0 0 0 2px rgba(37,99,235,.15); }
    .html-editor.dragging { border-color: #10b981; border-style: dashed; background: #064e3b; }

    /* ── Search ──────────────────────────────────────────── */
    .ref-search { width: 100%; box-sizing: border-box; padding: 7px 10px; border: 1px solid #cbd5e1; border-radius: 6px; font-size: 12px; background: #fff; outline: none; }
    .ref-search:focus { border-color: #2563eb; box-shadow: 0 0 0 2px rgba(37,99,235,.15); }

    /* ── Variable palette ────────────────────────────────── */
    .ref-group { display: flex; flex-direction: column; gap: 4px; }
    .ref-group-head { display: flex; align-items: center; gap: 6px; padding: 4px 2px; }
    .ref-group-icon { font-size: 14px; }
    .ref-group-label { font-size: 11px; font-weight: 700; color: #475569; text-transform: uppercase; letter-spacing: 0.04em; }
    .ref-group-count { margin-left: auto; background: #e2e8f0; color: #64748b; font-size: 10px; padding: 1px 6px; border-radius: 99px; font-weight: 600; }
    .ref-group-body { display: flex; flex-direction: column; gap: 4px; }

    .var-chip { display: flex; flex-direction: column; gap: 2px; padding: 6px 9px; border-radius: 6px; cursor: grab; background: #fff; border: 1px solid #e2e8f0; transition: all .12s; }
    .var-chip:hover { background: #eff6ff; border-color: #93c5fd; transform: translateX(-2px); box-shadow: 0 1px 4px rgba(37,99,235,.12); }
    .var-chip:active { cursor: grabbing; }
    .var-chip-token { font-family: 'Courier New', monospace; font-size: 11px; color: #1d4ed8; font-weight: 600; }
    .var-chip-desc { font-size: 10px; color: #64748b; }

    .ref-empty { font-size: 11px; color: #94a3b8; text-align: center; padding: 16px 0; }
    .toast { margin-top: 8px; background: #dcfce7; color: #15803d; font-size: 11px; padding: 6px 10px; border-radius: 6px; text-align: center; font-weight: 500; }

    /* ── Buttons ─────────────────────────────────────────── */
    .btn { display: inline-flex; align-items: center; gap: 5px; padding: 6px 14px; border-radius: 6px; font-size: 13px; font-weight: 500; cursor: pointer; border: none; text-decoration: none; }
    .btn-primary { background: #2563eb; color: #fff; }
    .btn-primary:hover:not(:disabled) { background: #1d4ed8; }
    .btn-primary:disabled { opacity: .6; cursor: not-allowed; }
    .btn-outline { background: transparent; border: 1px solid #cbd5e1; color: #475569; }
    .btn-outline:hover { background: #f1f5f9; }
    .btn-danger-soft { background: #fef2f2; border: 1px solid #fecaca; color: #dc2626; padding: 6px 14px; border-radius: 6px; font-size: 13px; font-weight: 500; cursor: pointer; }
    .btn-danger-soft:hover { background: #fee2e2; }
    .msg-error { color: #dc2626; font-size: 12px; }
    .msg-success { color: #16a34a; font-size: 12px; }
  `]
})
export class TemplateEditorComponent implements OnInit {
  private route  = inject(ActivatedRoute);
  private router = inject(Router);
  private svc    = inject(TemplateService);

  @ViewChild('editor') editorRef!: ElementRef<HTMLTextAreaElement>;

  type!: TemplateType;
  htmlContent = '';
  isCustom = false;
  saving = false;
  saveError = '';
  saveSuccess = false;
  toast = '';
  searchTerm = '';
  isDraggingOver = false;
  insertedCount = 0;

  get typeLabel(): string { return TYPE_LABELS[this.type] ?? this.type; }
  get previewHref(): string { return this.svc.previewUrl(this.type); }

  // ── Variable groups (catalog) ──────────────────────────────────

  readonly groups: VarGroup[] = [
    {
      id: 'societe',
      label: 'Société & contexte',
      icon: '🏢',
      vars: [
        { var: 'societeName',   desc: 'Nom de la société' },
        { var: 'projectName',   desc: 'Nom du projet' },
        { var: 'agentEmail',    desc: 'Email de l\'agent' },
        { var: 'generatedAt',   desc: 'Date/heure de génération' },
        { var: 'createdAt',     desc: 'Date de création' },
      ],
    },
    {
      id: 'property',
      label: 'Bien immobilier',
      icon: '🏠',
      vars: [
        { var: 'propertyRef',   desc: 'Référence du bien' },
        { var: 'propertyTitle', desc: 'Titre du bien' },
        { var: 'propertyType',  desc: 'Type (APPARTEMENT, VILLA…)' },
        { var: 'agreedPrice',   desc: 'Prix de vente convenu' },
        { var: 'listPrice',     desc: 'Prix catalogue' },
      ],
    },
    {
      id: 'buyer',
      label: 'Acheteur',
      icon: '👤',
      vars: [
        { var: 'buyerDisplayName', desc: 'Nom complet de l\'acheteur' },
        { var: 'buyerPhone',       desc: 'Téléphone' },
        { var: 'buyerEmail',       desc: 'Email' },
        { var: 'buyerAddress',     desc: 'Adresse' },
        { var: 'buyerIce',         desc: 'ICE / numéro fiscal' },
        { var: 'buyerTypeLabel',   desc: 'Personne physique / morale' },
      ],
    },
    {
      id: 'contract',
      label: 'Contrat / Document',
      icon: '📄',
      vars: [
        { var: 'contractRef',      desc: 'Référence du contrat' },
        { var: 'contractStatus',   desc: 'Statut du contrat' },
        { var: 'signedAt',         desc: 'Date de signature' },
        { var: 'depositReference', desc: 'Réf. acompte / réservation' },
        { var: 'depositAmount',    desc: 'Montant de l\'acompte' },
        { var: 'depositDate',      desc: 'Date de l\'acompte' },
        { var: 'dueDate',          desc: 'Date d\'échéance' },
        { var: 'notes',            desc: 'Notes libres' },
      ],
    },
  ];

  filteredGroups: VarGroup[] = this.groups;

  // ── Lifecycle ─────────────────────────────────────────────────

  ngOnInit(): void {
    this.type = this.route.snapshot.paramMap.get('type') as TemplateType;
    this.svc.getSource(this.type).subscribe({
      next: res => {
        this.htmlContent = res.htmlContent;
        this.isCustom = res.custom;
        this.recountInserted();
      },
      error: () => { this.saveError = 'Impossible de charger le modèle.'; },
    });
  }

  // ── Actions ───────────────────────────────────────────────────

  save(): void {
    this.saving = true;
    this.saveError = '';
    this.saveSuccess = false;
    this.svc.upsert(this.type, this.htmlContent).subscribe({
      next: () => {
        this.saving = false;
        this.isCustom = true;
        this.saveSuccess = true;
        setTimeout(() => this.saveSuccess = false, 3000);
      },
      error: (err) => {
        this.saving = false;
        this.saveError = err?.error?.message ?? 'Erreur lors de la sauvegarde.';
      },
    });
  }

  revert(): void {
    if (!confirm(`Réinitialiser "${this.typeLabel}" vers le modèle intégré ?`)) return;
    this.svc.delete(this.type).subscribe({
      next: () => { this.isCustom = false; this.router.navigateByUrl('/app/templates'); },
      error: (err) => { this.saveError = err?.error?.message ?? 'Erreur lors de la réinitialisation.'; },
    });
  }

  // ── Filter ────────────────────────────────────────────────────

  filterVars(): void {
    const q = this.searchTerm.trim().toLowerCase();
    if (!q) { this.filteredGroups = this.groups; return; }
    this.filteredGroups = this.groups
      .map(g => ({
        ...g,
        vars: g.vars.filter(v =>
          v.var.toLowerCase().includes(q) || v.desc.toLowerCase().includes(q)),
      }))
      .filter(g => g.vars.length > 0);
  }

  // ── Insertion ─────────────────────────────────────────────────

  /** Builds the Thymeleaf token for a given variable name. */
  private snippet(varName: string): string {
    return '${model.' + varName + '}';
  }

  /** Inserts a snippet at the current caret/selection position of the textarea. */
  insertAtCaret(varName: string): void {
    const ta = this.editorRef?.nativeElement;
    if (!ta) return;
    const snippet = this.snippet(varName);
    const start = ta.selectionStart ?? ta.value.length;
    const end   = ta.selectionEnd   ?? ta.value.length;
    ta.setRangeText(snippet, start, end, 'end');
    this.htmlContent = ta.value;
    ta.focus();
    this.recountInserted();
    this.flashToast(`Inséré: ${snippet}`);
  }

  // ── Drag & drop ───────────────────────────────────────────────

  onDragStart(ev: DragEvent, varName: string): void {
    if (!ev.dataTransfer) return;
    ev.dataTransfer.setData('text/plain', this.snippet(varName));
    ev.dataTransfer.effectAllowed = 'copy';
  }

  onDragOver(ev: DragEvent): void {
    ev.preventDefault();
    if (ev.dataTransfer) ev.dataTransfer.dropEffect = 'copy';
    this.isDraggingOver = true;
  }

  onDragLeave(_ev: DragEvent): void {
    this.isDraggingOver = false;
  }

  onDragEnd(): void {
    this.isDraggingOver = false;
  }

  onDrop(ev: DragEvent): void {
    ev.preventDefault();
    this.isDraggingOver = false;
    const ta = ev.target as HTMLTextAreaElement;
    const snippet = ev.dataTransfer?.getData('text/plain');
    if (!snippet || !ta) return;

    // Try to insert at the drop point. document.caretPositionFromPoint and
    // caretRangeFromPoint give us a DOM position; for a textarea we fall back
    // to the current selection.
    let pos = ta.selectionStart ?? ta.value.length;
    const docAny = document as Document & {
      caretPositionFromPoint?: (x: number, y: number) => { offsetNode: Node; offset: number } | null;
      caretRangeFromPoint?: (x: number, y: number) => Range | null;
    };
    if (docAny.caretPositionFromPoint) {
      const cp = docAny.caretPositionFromPoint(ev.clientX, ev.clientY);
      if (cp) pos = cp.offset;
    } else if (docAny.caretRangeFromPoint) {
      const cr = docAny.caretRangeFromPoint(ev.clientX, ev.clientY);
      if (cr) pos = cr.startOffset;
    }

    ta.setRangeText(snippet, pos, pos, 'end');
    this.htmlContent = ta.value;
    ta.focus();
    this.recountInserted();
    this.flashToast(`Inséré: ${snippet}`);
  }

  // ── Helpers ───────────────────────────────────────────────────

  recountInserted(): void {
    const re = /\$\{model\.[a-zA-Z0-9_]+\}/g;
    this.insertedCount = (this.htmlContent.match(re) || []).length;
  }

  private flashToast(msg: string): void {
    this.toast = msg;
    setTimeout(() => this.toast = '', 1800);
  }
}
