import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TemplateService } from './template.service';
import { TemplateSummary, TemplateType } from './template.model';

const ALL_TYPES: TemplateType[] = ['CONTRACT', 'RESERVATION', 'CALL_FOR_FUNDS'];

const TYPE_META: Record<TemplateType, { label: string; icon: string; desc: string; color: string; }> = {
  CONTRACT: {
    label: 'Contrat de vente',
    icon: '📑',
    desc: 'Acte de vente immobilière — signé par l\'acquéreur et l\'agent commercial. Inclut les conditions financières, les garanties légales et les clauses suspensives.',
    color: '#15803d',
  },
  RESERVATION: {
    label: 'Bon de réservation',
    icon: '🔖',
    desc: 'Attestation de réservation et de dépôt de garantie. Document précontractuel officialisant l\'engagement de l\'acquéreur sur un bien identifié.',
    color: '#059669',
  },
  CALL_FOR_FUNDS: {
    label: 'Appel de fonds',
    icon: '💶',
    desc: 'Avis de règlement des tranches du calendrier de paiement. Émis à chaque jalon de construction ou de livraison défini dans le contrat.',
    color: '#7c3aed',
  },
};

@Component({
  selector: 'app-template-list',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="page-header">
      <div class="page-header-inner">
        <h1 class="page-title">Modèles de documents</h1>
        <p class="page-subtitle">
          Personnalisez les modèles PDF générés par le CRM pour chaque type de document légal.
          Les modifications sont appliquées à toutes les générations futures.
        </p>
      </div>
    </div>

    <div class="template-grid">
      @for (type of allTypes; track type) {
        <div class="template-card" [style.--accent]="meta(type).color">
          <div class="card-accent-bar"></div>
          <div class="card-body">
            <div class="card-header">
              <span class="type-icon">{{ meta(type).icon }}</span>
              <div class="type-info">
                <h2 class="type-label">{{ meta(type).label }}</h2>
                @if (isCustomized(type)) {
                  <span class="badge badge-custom">Personnalisé</span>
                } @else {
                  <span class="badge badge-default">Modèle intégré</span>
                }
              </div>
            </div>
            <p class="type-desc">{{ meta(type).desc }}</p>
            @if (isCustomized(type)) {
              <p class="updated-label">
                <svg width="11" height="11" viewBox="0 0 11 11" fill="none">
                  <circle cx="5.5" cy="5.5" r="4.5" stroke="currentColor" stroke-width="1.2"/>
                  <path d="M5.5 3v2.5l1.5 1.5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                </svg>
                Modifié le {{ getUpdatedAt(type) }}
              </p>
            }
          </div>
          <div class="card-actions">
            <a [routerLink]="['/app/templates', type, 'edit']" class="btn btn-primary">
              <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
                <path d="M9 2l2 2L4 11H2V9L9 2z" stroke="currentColor" stroke-width="1.3"
                      stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
              Modifier
            </a>
            <button (click)="previewPdf(type)" class="btn btn-outline">
              <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
                <path d="M1.5 6.5C1.5 6.5 3.5 2.5 6.5 2.5S11.5 6.5 11.5 6.5 9.5 10.5 6.5 10.5 1.5 6.5 1.5 6.5z"
                      stroke="currentColor" stroke-width="1.3"/>
                <circle cx="6.5" cy="6.5" r="1.5" stroke="currentColor" stroke-width="1.3"/>
              </svg>
              Aperçu
            </button>
            @if (isCustomized(type)) {
              <button (click)="revert(type)" class="btn btn-ghost-danger">
                Réinitialiser
              </button>
            }
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .page-header {
      background: linear-gradient(135deg, #1e293b 0%, #334155 100%);
      border-radius: 12px; padding: 28px 32px; margin-bottom: 24px; color: #fff;
    }
    .page-title   { font-size: 22px; font-weight: 700; margin: 0 0 8px; }
    .page-subtitle { font-size: 13px; color: #94a3b8; margin: 0; max-width: 600px; line-height: 1.6; }

    .template-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 20px; }

    .template-card {
      background: #fff; border: 1px solid #e2e8f0; border-radius: 12px;
      overflow: hidden; display: flex; flex-direction: column;
      transition: box-shadow .15s, transform .15s;
    }
    .template-card:hover { box-shadow: 0 8px 24px rgba(0,0,0,.1); transform: translateY(-2px); }

    .card-accent-bar { height: 4px; background: var(--accent, #16a34a); }
    .card-body { flex: 1; padding: 20px; display: flex; flex-direction: column; gap: 10px; }
    .card-header { display: flex; align-items: flex-start; gap: 12px; }
    .type-icon { font-size: 28px; line-height: 1; flex-shrink: 0; margin-top: 2px; }
    .type-info { flex: 1; }
    .type-label { font-size: 16px; font-weight: 700; color: #1e293b; margin: 0 0 5px; }

    .badge { font-size: 11px; padding: 2px 8px; border-radius: 99px; font-weight: 600; }
    .badge-custom  { background: #dbeafe; color: #15803d; }
    .badge-default { background: #f1f5f9; color: #64748b; }

    .type-desc   { font-size: 12.5px; color: #64748b; line-height: 1.6; margin: 0; flex: 1; }
    .updated-label {
      display: flex; align-items: center; gap: 5px;
      font-size: 11px; color: #94a3b8; margin: 0;
    }

    .card-actions { display: flex; gap: 8px; padding: 14px 20px; border-top: 1px solid #f1f5f9; flex-wrap: wrap; }

    .btn {
      display: inline-flex; align-items: center; gap: 5px;
      padding: 7px 14px; border-radius: 6px; font-size: 12.5px;
      font-weight: 500; cursor: pointer; border: none; text-decoration: none;
    }
    .btn-primary { background: #16a34a; color: #fff; }
    .btn-primary:hover { background: #15803d; }
    .btn-outline { background: transparent; border: 1px solid #cbd5e1; color: #475569; }
    .btn-outline:hover { background: #f8fafc; }
    .btn-ghost-danger { background: transparent; color: #dc2626; border: 1px solid #fecaca; font-size: 12.5px; }
    .btn-ghost-danger:hover { background: #fef2f2; }
  `],
})
export class TemplateListComponent implements OnInit {
  private svc = inject(TemplateService);

  allTypes   = ALL_TYPES;
  customized = new Map<TemplateType, TemplateSummary>();

  ngOnInit(): void {
    this.svc.list().subscribe(list => list.forEach(t => this.customized.set(t.templateType, t)));
  }

  meta(type: TemplateType) { return TYPE_META[type]; }
  isCustomized(type: TemplateType): boolean { return this.customized.has(type); }

  getUpdatedAt(type: TemplateType): string {
    const t = this.customized.get(type);
    return t ? new Date(t.updatedAt).toLocaleDateString('fr-FR') : '';
  }

  previewPdf(type: TemplateType): void {
    window.open(this.svc.previewUrl(type), '_blank', 'noopener,noreferrer');
  }

  revert(type: TemplateType): void {
    if (!confirm(`Réinitialiser le modèle "${TYPE_META[type].label}" vers la version intégrée ?`)) return;
    this.svc.delete(type).subscribe(() => this.customized.delete(type));
  }
}
