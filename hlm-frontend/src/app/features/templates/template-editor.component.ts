import { Component, inject, OnInit } from '@angular/core';
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
          <button (click)="revert()" class="btn btn-danger-soft">
            Réinitialiser
          </button>
        }
        @if (saveError) {
          <span class="msg-error">{{ saveError }}</span>
        }
        @if (saveSuccess) {
          <span class="msg-success">✓ Enregistré</span>
        }
      </div>
    </div>

    <!-- ── Two-pane layout ─────────────────────────────────── -->
    <div class="ed-layout">

      <!-- LEFT — code editor -->
      <div class="ed-pane-editor">
        <div class="ed-pane-label">
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
            <path d="M3 4L1 6l2 2M9 4l2 2-2 2M7 2l-2 8" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          HTML Thymeleaf
        </div>
        <textarea
          class="html-editor"
          [(ngModel)]="htmlContent"
          spellcheck="false"
          placeholder="Collez ou écrivez votre HTML Thymeleaf ici…"
        ></textarea>
      </div>

      <!-- RIGHT — variable reference -->
      <div class="ed-pane-ref">
        <div class="ed-pane-label">
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
            <circle cx="6" cy="6" r="5" stroke="currentColor" stroke-width="1.3"/>
            <path d="M6 5.5v3M6 3.5v.5" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
          </svg>
          Variables disponibles
        </div>

        <div class="ref-section-title">Société &amp; contexte</div>
        <div class="ref-grid">
          @for (v of refSociete; track v.var) {
            <div class="ref-row" (click)="insertVar(v.var)" title="Cliquer pour copier">
              <code class="ref-var">&#36;&#123;model.{{ v.var }}&#125;</code>
              <span class="ref-desc">{{ v.desc }}</span>
            </div>
          }
        </div>

        <div class="ref-section-title">Bien immobilier</div>
        <div class="ref-grid">
          @for (v of refProperty; track v.var) {
            <div class="ref-row" (click)="insertVar(v.var)" title="Cliquer pour copier">
              <code class="ref-var">&#36;&#123;model.{{ v.var }}&#125;</code>
              <span class="ref-desc">{{ v.desc }}</span>
            </div>
          }
        </div>

        <div class="ref-section-title">Acheteur</div>
        <div class="ref-grid">
          @for (v of refBuyer; track v.var) {
            <div class="ref-row" (click)="insertVar(v.var)" title="Cliquer pour copier">
              <code class="ref-var">&#36;&#123;model.{{ v.var }}&#125;</code>
              <span class="ref-desc">{{ v.desc }}</span>
            </div>
          }
        </div>

        <div class="ref-section-title">Contrat / Document</div>
        <div class="ref-grid">
          @for (v of refContract; track v.var) {
            <div class="ref-row" (click)="insertVar(v.var)" title="Cliquer pour copier">
              <code class="ref-var">&#36;&#123;model.{{ v.var }}&#125;</code>
              <span class="ref-desc">{{ v.desc }}</span>
            </div>
          }
        </div>

        @if (copied) {
          <div class="copy-toast">✓ Copié dans le presse-papiers</div>
        }
      </div>
    </div>
  `,
  styles: [`
    /* ── Topbar ─────────────────────────────────────────── */
    .ed-topbar { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
    .ed-topbar-left { display: flex; flex-direction: column; gap: 6px; }
    .back-link { display: inline-flex; align-items: center; gap: 5px; color: #2563eb; font-size: 12px; text-decoration: none; }
    .back-link:hover { text-decoration: underline; }
    .ed-title-row { display: flex; align-items: center; gap: 10px; }
    .ed-title { font-size: 18px; font-weight: 700; color: #1e293b; margin: 0; }
    .badge-custom { background: #dbeafe; color: #1d4ed8; font-size: 11px; padding: 2px 8px; border-radius: 99px; font-weight: 600; }
    .badge-default { background: #f1f5f9; color: #64748b; font-size: 11px; padding: 2px 8px; border-radius: 99px; }
    .ed-toolbar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }

    /* ── Two-pane layout ─────────────────────────────────── */
    .ed-layout { display: grid; grid-template-columns: 1fr 260px; gap: 16px; height: calc(100vh - 160px); min-height: 500px; }
    .ed-pane-editor { display: flex; flex-direction: column; gap: 6px; }
    .ed-pane-ref { display: flex; flex-direction: column; gap: 4px; overflow-y: auto; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px; background: #f8fafc; }
    .ed-pane-label { display: flex; align-items: center; gap: 5px; font-size: 11px; font-weight: 600; color: #64748b; text-transform: uppercase; letter-spacing: 0.04em; margin-bottom: 2px; }

    /* ── Code editor ─────────────────────────────────────── */
    .html-editor { flex: 1; font-family: 'Courier New', Consolas, monospace; font-size: 12.5px; line-height: 1.65; border: 1px solid #cbd5e1; border-radius: 8px; padding: 14px; resize: none; background: #0f172a; color: #e2e8f0; outline: none; box-sizing: border-box; tab-size: 2; }
    .html-editor:focus { border-color: #2563eb; box-shadow: 0 0 0 2px rgba(37,99,235,.15); }

    /* ── Variable reference ──────────────────────────────── */
    .ref-section-title { font-size: 10px; font-weight: 700; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.05em; margin: 10px 0 4px; }
    .ref-section-title:first-of-type { margin-top: 4px; }
    .ref-grid { display: flex; flex-direction: column; gap: 2px; }
    .ref-row { display: flex; flex-direction: column; gap: 1px; padding: 5px 7px; border-radius: 5px; cursor: pointer; transition: background .12s; }
    .ref-row:hover { background: #e2e8f0; }
    .ref-var { font-family: 'Courier New', monospace; font-size: 10.5px; color: #1d4ed8; background: none; padding: 0; word-break: break-all; }
    .ref-desc { font-size: 10px; color: #64748b; }
    .copy-toast { margin-top: 8px; background: #dcfce7; color: #15803d; font-size: 11px; padding: 5px 10px; border-radius: 6px; text-align: center; }

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

  type!: TemplateType;
  htmlContent = '';
  isCustom = false;
  saving = false;
  saveError = '';
  saveSuccess = false;
  copied = false;

  get typeLabel(): string {
    return TYPE_LABELS[this.type] ?? this.type;
  }

  get previewHref(): string {
    return this.svc.previewUrl(this.type);
  }

  // ── Variable reference data ────────────────────────────────────

  readonly refSociete = [
    { var: 'societeName',   desc: 'Nom de la société' },
    { var: 'projectName',   desc: 'Nom du projet' },
    { var: 'agentEmail',    desc: 'Email de l\'agent' },
    { var: 'generatedAt',   desc: 'Date/heure de génération' },
    { var: 'createdAt',     desc: 'Date de création' },
  ];

  readonly refProperty = [
    { var: 'propertyRef',   desc: 'Référence du bien' },
    { var: 'propertyTitle', desc: 'Titre du bien' },
    { var: 'propertyType',  desc: 'Type (APPARTEMENT, VILLA…)' },
    { var: 'agreedPrice',   desc: 'Prix de vente convenu' },
    { var: 'listPrice',     desc: 'Prix catalogue' },
  ];

  readonly refBuyer = [
    { var: 'buyerDisplayName', desc: 'Nom complet de l\'acheteur' },
    { var: 'buyerPhone',       desc: 'Téléphone' },
    { var: 'buyerEmail',       desc: 'Email' },
    { var: 'buyerAddress',     desc: 'Adresse' },
    { var: 'buyerIce',         desc: 'ICE / numéro fiscal' },
    { var: 'buyerTypeLabel',   desc: 'Personne physique / morale' },
  ];

  readonly refContract = [
    { var: 'contractRef',    desc: 'Référence du contrat' },
    { var: 'contractStatus', desc: 'Statut du contrat' },
    { var: 'signedAt',       desc: 'Date de signature' },
    { var: 'depositReference', desc: 'Réf. acompte / réservation' },
    { var: 'depositAmount',  desc: 'Montant de l\'acompte' },
    { var: 'depositDate',    desc: 'Date de l\'acompte' },
    { var: 'dueDate',        desc: 'Date d\'échéance' },
    { var: 'notes',          desc: 'Notes libres' },
  ];

  // ── Lifecycle ─────────────────────────────────────────────────

  ngOnInit(): void {
    this.type = this.route.snapshot.paramMap.get('type') as TemplateType;
    this.svc.getSource(this.type).subscribe({
      next: res => {
        this.htmlContent = res.htmlContent;
        this.isCustom = res.custom;
      },
      error: () => { this.saveError = 'Impossible de charger le modèle.'; }
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
      }
    });
  }

  revert(): void {
    if (!confirm(`Réinitialiser "${this.typeLabel}" vers le modèle intégré ?`)) return;
    this.svc.delete(this.type).subscribe({
      next: () => {
        this.isCustom = false;
        this.router.navigateByUrl('/app/templates');
      },
      error: (err) => {
        this.saveError = err?.error?.message ?? 'Erreur lors de la réinitialisation.';
      }
    });
  }

  insertVar(varName: string): void {
    const snippet = `\${model.${varName}}`;
    navigator.clipboard.writeText(snippet).then(() => {
      this.copied = true;
      setTimeout(() => this.copied = false, 2000);
    });
  }
}
