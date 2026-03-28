import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { TemplateService } from './template.service';
import { TemplateSummary, TemplateType } from './template.model';

const ALL_TYPES: TemplateType[] = ['CONTRACT', 'RESERVATION', 'CALL_FOR_FUNDS'];

const TYPE_LABELS: Record<TemplateType, string> = {
  CONTRACT: 'Contrat de vente',
  RESERVATION: 'Bon de réservation',
  CALL_FOR_FUNDS: 'Appel de fonds',
};

@Component({
  selector: 'app-template-list',
  standalone: true,
  imports: [CommonModule, RouterLink, TranslateModule],
  template: `
    <div class="page-header">
      <h1>{{ 'templates.title' | translate }}</h1>
      <p class="subtitle">{{ 'templates.subtitle' | translate }}</p>
    </div>

    <div class="template-grid">
      @for (type of allTypes; track type) {
        <div class="template-card">
          <div class="template-card-header">
            <span class="template-type-badge">{{ typeLabel(type) }}</span>
            @if (isCustomized(type)) {
              <span class="badge-custom">Personnalisé</span>
            } @else {
              <span class="badge-default">{{ 'templates.default' | translate }}</span>
            }
          </div>
          <div class="template-card-body">
            <p class="template-desc">{{ typeDescription(type) }}</p>
            @if (isCustomized(type)) {
              <p class="template-updated">Modifié le {{ getUpdatedAt(type) }}</p>
            }
          </div>
          <div class="template-card-actions">
            <a [routerLink]="['/app/templates', type, 'edit']" class="btn btn-primary btn-sm">
              {{ 'templates.edit' | translate }}
            </a>
            <button (click)="previewPdf(type)" class="btn btn-outline btn-sm">
              {{ 'templates.preview' | translate }}
            </button>
            @if (isCustomized(type)) {
              <button (click)="revert(type)" class="btn btn-danger btn-sm">
                {{ 'templates.revert' | translate }}
              </button>
            }
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .page-header { margin-bottom: 24px; }
    .page-header h1 { font-size: 22px; font-weight: 700; color: #1e293b; margin: 0 0 6px; }
    .subtitle { color: #64748b; margin: 0; }
    .template-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 20px; }
    .template-card { background: #fff; border: 1px solid #e2e8f0; border-radius: 10px; padding: 20px; display: flex; flex-direction: column; gap: 12px; }
    .template-card-header { display: flex; align-items: center; gap: 8px; }
    .template-type-badge { font-weight: 600; color: #1e293b; font-size: 15px; }
    .badge-custom { background: #dbeafe; color: #1d4ed8; font-size: 11px; padding: 2px 8px; border-radius: 99px; font-weight: 600; margin-left: auto; }
    .badge-default { background: #f1f5f9; color: #64748b; font-size: 11px; padding: 2px 8px; border-radius: 99px; font-weight: 500; margin-left: auto; }
    .template-card-body { flex: 1; }
    .template-desc { color: #475569; font-size: 13px; margin: 0 0 4px; }
    .template-updated { color: #94a3b8; font-size: 12px; margin: 0; }
    .template-card-actions { display: flex; gap: 8px; flex-wrap: wrap; }
    .btn { padding: 6px 14px; border-radius: 6px; font-size: 13px; font-weight: 500; cursor: pointer; border: none; text-decoration: none; display: inline-flex; align-items: center; }
    .btn-sm { padding: 5px 12px; font-size: 12px; }
    .btn-primary { background: #2563eb; color: #fff; }
    .btn-primary:hover { background: #1d4ed8; }
    .btn-outline { background: transparent; border: 1px solid #cbd5e1; color: #475569; }
    .btn-outline:hover { background: #f8fafc; }
    .btn-danger { background: #fef2f2; border: 1px solid #fecaca; color: #dc2626; }
    .btn-danger:hover { background: #fee2e2; }
  `]
})
export class TemplateListComponent implements OnInit {
  private svc = inject(TemplateService);

  allTypes = ALL_TYPES;
  customized = new Map<TemplateType, TemplateSummary>();

  ngOnInit(): void {
    this.svc.list().subscribe(list => {
      list.forEach(t => this.customized.set(t.templateType, t));
    });
  }

  isCustomized(type: TemplateType): boolean {
    return this.customized.has(type);
  }

  getUpdatedAt(type: TemplateType): string {
    const t = this.customized.get(type);
    if (!t) return '';
    return new Date(t.updatedAt).toLocaleDateString('fr-FR');
  }

  typeLabel(type: TemplateType): string {
    return TYPE_LABELS[type];
  }

  typeDescription(type: TemplateType): string {
    const desc: Record<TemplateType, string> = {
      CONTRACT: 'Contrat de vente immobilière signé par l\'acheteur et l\'agent.',
      RESERVATION: 'Bon de réservation / attestation de dépôt de garantie.',
      CALL_FOR_FUNDS: 'Appel de fonds pour les tranches du calendrier de paiement.',
    };
    return desc[type];
  }

  previewPdf(type: TemplateType): void {
    window.open(this.svc.previewUrl(type), '_blank');
  }

  revert(type: TemplateType): void {
    if (!confirm(`Réinitialiser le modèle "${this.typeLabel(type)}" vers le modèle intégré ?`)) return;
    this.svc.delete(type).subscribe(() => {
      this.customized.delete(type);
    });
  }
}
