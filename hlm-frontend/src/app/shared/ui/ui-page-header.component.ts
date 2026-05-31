import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

export interface UiBreadcrumb {
  label: string;
  /** Router link target; omit for the current (non-clickable) crumb. */
  link?: string | unknown[];
}

/**
 * Consistent page header: breadcrumb trail + title + optional subtitle + a right-aligned
 * action slot. Gives every screen the same top structure and is the anchor for the future
 * project-scoped workspace header.
 *
 * Usage:
 *   <ui-page-header
 *      [breadcrumbs]="[{label:'Projets', link:'/app/projets'}, {label: projet.nom}]"
 *      title="Résidence Al Manar"
 *      subtitle="Casablanca · 3 tranches · 84 lots">
 *     <ui-button variant="primary" (click)="newTranche()">Nouvelle tranche</ui-button>
 *   </ui-page-header>
 */
@Component({
  selector: 'ui-page-header',
  standalone: true,
  imports: [CommonModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="ui-ph">
      @if (breadcrumbs?.length) {
        <nav class="ui-ph-crumbs" aria-label="Fil d'Ariane">
          @for (crumb of breadcrumbs; track $index; let last = $last) {
            @if (crumb.link && !last) {
              <a [routerLink]="crumb.link" class="ui-ph-crumb">{{ crumb.label }}</a>
              <span class="ui-ph-sep" aria-hidden="true">/</span>
            } @else {
              <span class="ui-ph-crumb is-current" [attr.aria-current]="last ? 'page' : null">{{ crumb.label }}</span>
              @if (!last) { <span class="ui-ph-sep" aria-hidden="true">/</span> }
            }
          }
        </nav>
      }
      <div class="ui-ph-row">
        <div class="ui-ph-titles">
          <h1 class="ui-ph-title">{{ title }}</h1>
          @if (subtitle) { <p class="ui-ph-sub">{{ subtitle }}</p> }
        </div>
        <div class="ui-ph-actions"><ng-content></ng-content></div>
      </div>
    </header>
  `,
  styles: [`
    .ui-ph { display: flex; flex-direction: column; gap: var(--sp-2, 8px); margin-bottom: var(--sp-5, 20px); }
    .ui-ph-crumbs { display: flex; align-items: center; gap: var(--sp-1, 4px); flex-wrap: wrap; font-size: var(--text-xs, .75rem); }
    .ui-ph-crumb { color: var(--c-text-secondary); text-decoration: none; }
    .ui-ph-crumb:hover { color: var(--c-primary); text-decoration: underline; }
    .ui-ph-crumb.is-current { color: var(--c-text-muted); }
    .ui-ph-sep { color: var(--c-text-muted); }
    .ui-ph-row { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--sp-4, 16px); flex-wrap: wrap; }
    .ui-ph-title { margin: 0; font-size: var(--text-2xl, 1.5rem); font-weight: 700; color: var(--c-text); }
    .ui-ph-sub { margin: 2px 0 0; font-size: var(--text-sm, .875rem); color: var(--c-text-secondary); }
    .ui-ph-actions { display: flex; align-items: center; gap: var(--sp-2, 8px); }
    .ui-ph-actions:empty { display: none; }
  `],
})
export class UiPageHeaderComponent {
  @Input() breadcrumbs?: UiBreadcrumb[];
  @Input() title = '';
  @Input() subtitle?: string;
}
