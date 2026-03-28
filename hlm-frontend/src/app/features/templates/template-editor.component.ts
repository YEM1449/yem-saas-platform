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
    <div class="editor-header">
      <a routerLink="/app/templates" class="back-link">{{ 'templates.backToList' | translate }}</a>
      <h1>{{ typeLabel }} <span class="badge-type">{{ type }}</span></h1>
      @if (isCustom) {
        <span class="badge-custom">Modèle personnalisé actif</span>
      } @else {
        <span class="badge-default">{{ 'templates.default' | translate }}</span>
      }
    </div>

    <div class="editor-toolbar">
      <button (click)="save()" [disabled]="saving" class="btn btn-primary">
        {{ saving ? ('templates.save' | translate) : ('templates.save' | translate) }}
      </button>
      <button (click)="previewPdf()" class="btn btn-outline">{{ 'templates.preview' | translate }}</button>
      @if (isCustom) {
        <button (click)="revert()" class="btn btn-danger">{{ 'templates.revert' | translate }}</button>
      }
      @if (saveError) {
        <span class="error-msg">{{ saveError }}</span>
      }
      @if (saveSuccess) {
        <span class="success-msg">{{ 'templates.saved' | translate }}</span>
      }
    </div>

    <div class="editor-hint">
      <strong>Syntaxe Thymeleaf.</strong> Utilisez <code>$&#x7B;model.societeName&#x7D;</code>,
      <code>$&#x7B;model.buyerDisplayName&#x7D;</code>, etc. Les mêmes variables que le modèle intégré sont disponibles.
    </div>

    <textarea
      class="html-editor"
      [(ngModel)]="htmlContent"
      spellcheck="false"
      placeholder="Collez ou écrivez votre HTML Thymeleaf ici…"
    ></textarea>
  `,
  styles: [`
    .editor-header { margin-bottom: 16px; }
    .back-link { color: #2563eb; font-size: 13px; text-decoration: none; display: inline-block; margin-bottom: 8px; }
    .back-link:hover { text-decoration: underline; }
    .editor-header h1 { font-size: 20px; font-weight: 700; color: #1e293b; margin: 0 0 6px; display: flex; align-items: center; gap: 10px; }
    .badge-type { background: #e2e8f0; color: #475569; font-size: 11px; padding: 2px 8px; border-radius: 99px; font-weight: 500; font-family: monospace; }
    .badge-custom { background: #dbeafe; color: #1d4ed8; font-size: 12px; padding: 3px 10px; border-radius: 99px; font-weight: 600; }
    .badge-default { background: #f1f5f9; color: #64748b; font-size: 12px; padding: 3px 10px; border-radius: 99px; }
    .editor-toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
    .editor-hint { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 10px 14px; font-size: 12px; color: #475569; margin-bottom: 12px; }
    .editor-hint code { background: #e2e8f0; padding: 1px 5px; border-radius: 4px; font-size: 12px; margin: 0 2px; }
    .html-editor { width: 100%; height: calc(100vh - 280px); min-height: 400px; font-family: 'Courier New', monospace; font-size: 13px; line-height: 1.6; border: 1px solid #cbd5e1; border-radius: 8px; padding: 14px; resize: vertical; background: #0f172a; color: #e2e8f0; outline: none; box-sizing: border-box; }
    .html-editor:focus { border-color: #2563eb; }
    .btn { padding: 6px 16px; border-radius: 6px; font-size: 13px; font-weight: 500; cursor: pointer; border: none; }
    .btn-primary { background: #2563eb; color: #fff; }
    .btn-primary:hover:not(:disabled) { background: #1d4ed8; }
    .btn-primary:disabled { opacity: .6; cursor: not-allowed; }
    .btn-outline { background: transparent; border: 1px solid #cbd5e1; color: #475569; }
    .btn-outline:hover { background: #f8fafc; }
    .btn-danger { background: #fef2f2; border: 1px solid #fecaca; color: #dc2626; }
    .btn-danger:hover { background: #fee2e2; }
    .error-msg { color: #dc2626; font-size: 13px; }
    .success-msg { color: #16a34a; font-size: 13px; }
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

  get typeLabel(): string {
    return TYPE_LABELS[this.type] ?? this.type;
  }

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

  previewPdf(): void {
    window.open(this.svc.previewUrl(this.type), '_blank');
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
}
