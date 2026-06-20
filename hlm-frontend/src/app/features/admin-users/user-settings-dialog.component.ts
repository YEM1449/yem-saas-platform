import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { I18nService } from '../../core/i18n/i18n.service';

import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { AdminUserService, UserQuotaRequest, UserQuotaResponse, ProjectAccessResponse } from './admin-user.service';
import { MembreDto } from './admin-user.model';
import { ProjectService } from '../projects/project.service';
import { Project } from '../../core/models/project.model';

type Tab = 'objectifs' | 'projets';

@Component({
  selector: 'app-user-settings-dialog',
  standalone: true,
  imports: [FormsModule, TranslatePipe],
  styles: [`
    .overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.45); z-index: 200; display: flex; align-items: center; justify-content: center; }
    .modal { background: #fff; border-radius: 10px; padding: 1.5rem; width: min(540px, 95vw); max-height: 90vh; overflow-y: auto;
             box-shadow: 0 8px 32px rgba(0,0,0,0.18); display: flex; flex-direction: column; gap: 1rem; }
    .modal-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 0.5rem; }
    .modal-title { font-size: 1.05rem; font-weight: 600; color: #111827; }
    .modal-sub { font-size: 0.8rem; color: #6b7280; margin-top: 2px; }
    .close-btn { background: none; border: none; font-size: 1.2rem; cursor: pointer; color: #6b7280; padding: 0; line-height: 1; }
    .tabs { display: flex; gap: 0; border-bottom: 1px solid #e5e7eb; }
    .tab { padding: 0.45rem 1rem; font-size: 0.875rem; font-weight: 500; cursor: pointer; border: none; background: none;
           color: #6b7280; border-bottom: 2px solid transparent; margin-bottom: -1px; }
    .tab.active { color: #6366f1; border-bottom-color: #6366f1; }
    .tab-body { padding-top: 0.25rem; }
    label { display: flex; flex-direction: column; gap: 4px; font-size: 0.875rem; font-weight: 500; color: #374151; margin-bottom: 0.75rem; }
    input[type="text"], input[type="number"], input[type="month"] { padding: 0.4rem 0.6rem; border: 1px solid #d1d5db; border-radius: 6px; font-size: 0.875rem; width: 100%; box-sizing: border-box; }
    input:focus { outline: none; border-color: #6366f1; }
    .row2 { display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem; }
    .hint { font-size: 0.75rem; color: #6b7280; font-weight: 400; margin-top: 1px; }
    .project-list { display: flex; flex-direction: column; gap: 0.5rem; max-height: 260px; overflow-y: auto; border: 1px solid #e5e7eb; border-radius: 6px; padding: 0.5rem; }
    .project-row { display: flex; align-items: center; gap: 0.5rem; font-size: 0.875rem; cursor: pointer; padding: 0.2rem 0.25rem; border-radius: 4px; }
    .project-row:hover { background: #f9fafb; }
    .project-row input[type="checkbox"] { width: 15px; height: 15px; accent-color: #6366f1; cursor: pointer; flex-shrink: 0; }
    .info-box { background: #eff6ff; border: 1px solid #bfdbfe; border-radius: 6px; padding: 0.5rem 0.75rem; font-size: 0.8rem; color: #15803d; }
    .actions { display: flex; justify-content: flex-end; gap: 0.5rem; padding-top: 0.5rem; border-top: 1px solid #f3f4f6; }
    .btn-primary { background: #6366f1; color: #fff; border: none; padding: 0.45rem 1.1rem; border-radius: 6px; cursor: pointer; font-size: 0.875rem; font-weight: 500; }
    .btn-primary:disabled { opacity: 0.6; cursor: default; }
    .btn-secondary { background: #f3f4f6; color: #374151; border: 1px solid #d1d5db; padding: 0.45rem 1.1rem; border-radius: 6px; cursor: pointer; font-size: 0.875rem; }
    .alert { padding: 0.4rem 0.75rem; border-radius: 6px; font-size: 0.8rem; margin-bottom: 0.25rem; }
    .alert-error { background: #fee2e2; color: #b91c1c; }
    .alert-success { background: #dcfce7; color: #15803d; }
    .loading { color: #6b7280; font-size: 0.85rem; padding: 1rem 0; text-align: center; }
  `],
  template: `
    <div class="overlay" (click)="$event.target === $event.currentTarget && close.emit()">
      <div class="modal">
        <div class="modal-header">
          <div>
            <div class="modal-title">{{ 'adminUsers.settings.title' | translate:{ name: membre.nomComplet } }}</div>
            <div class="modal-sub">{{ membre.email }}</div>
          </div>
          <button class="close-btn" (click)="close.emit()" [title]="'adminUsers.settings.close' | translate">✕</button>
        </div>

        <div class="tabs">
          <button class="tab" [class.active]="tab === 'objectifs'" (click)="tab = 'objectifs'">{{ 'adminUsers.settings.tabObjectifs' | translate }}</button>
          <button class="tab" [class.active]="tab === 'projets'" (click)="tab = 'projets'">{{ 'adminUsers.settings.tabProjets' | translate }}</button>
        </div>

        @if (loading) {
          <div class="loading">{{ 'adminUsers.settings.loading' | translate }}</div>
        } @else {

          <!-- ── Objectifs tab ─────────────────────────────────────────────── -->
          @if (tab === 'objectifs') {
            <div class="tab-body">
              @if (quotaError) { <div class="alert alert-error">{{ quotaError }}</div> }
              @if (quotaSuccess) { <div class="alert alert-success">{{ quotaSuccess }}</div> }

              <label>
                {{ 'adminUsers.settings.mois' | translate }}
                <input type="month" [(ngModel)]="quotaMonth" (change)="onMonthChange()" />
              </label>

              <div class="row2">
                <label>
                  {{ 'adminUsers.settings.objectifCa' | translate }}
                  <input type="number" [(ngModel)]="caCible" min="0" step="1000" [placeholder]="'adminUsers.settings.caPlaceholder' | translate" />
                  <span class="hint">{{ 'adminUsers.settings.caHint' | translate }}</span>
                </label>
                <label>
                  {{ 'adminUsers.settings.objectifVentes' | translate }}
                  <input type="number" [(ngModel)]="ventesCountCible" min="0" step="1" [placeholder]="'adminUsers.settings.ventesPlaceholder' | translate" />
                  <span class="hint">{{ 'adminUsers.settings.ventesHint' | translate }}</span>
                </label>
              </div>

              <div class="info-box">
                {{ 'adminUsers.settings.infoObjectifs' | translate }}
              </div>

              <div class="actions">
                <button class="btn-secondary" (click)="close.emit()">{{ 'adminUsers.settings.cancel' | translate }}</button>
                <button class="btn-primary" [disabled]="savingQuota" (click)="saveQuota()">
                  {{ (savingQuota ? 'adminUsers.settings.saving' : 'adminUsers.settings.save') | translate }}
                </button>
              </div>
            </div>
          }

          <!-- ── Accès projets tab ──────────────────────────────────────────── -->
          @if (tab === 'projets') {
            <div class="tab-body">
              @if (accessError) { <div class="alert alert-error">{{ accessError }}</div> }
              @if (accessSuccess) { <div class="alert alert-success">{{ accessSuccess }}</div> }

              <div class="info-box" style="margin-bottom: 0.75rem">
                <strong>{{ 'adminUsers.settings.infoProjetsStrong' | translate }}</strong>{{ 'adminUsers.settings.infoProjetsRest' | translate }}
              </div>

              @if (projects.length === 0) {
                <div style="color:#6b7280; font-size:0.85rem; padding:0.5rem 0">{{ 'adminUsers.settings.noProjects' | translate }}</div>
              } @else {
                <div class="project-list">
                  @for (p of projects; track p.id) {
                    <label class="project-row" style="flex-direction:row;margin:0">
                      <input type="checkbox"
                             [checked]="selectedProjectIds.has(p.id)"
                             (change)="toggleProject(p.id)" />
                      {{ p.name }}
                      @if (p.status === 'ARCHIVED') {
                        <span style="font-size:0.7rem;color:#9ca3af;margin-left:auto">{{ 'adminUsers.settings.archived' | translate }}</span>
                      }
                    </label>
                  }
                </div>
              }

              <div style="font-size:0.78rem;color:#6b7280;margin-top:0.5rem">
                {{ selectedProjectIds.size === 0 ? ('adminUsers.settings.accessTotal' | translate) : ('adminUsers.settings.projectsSelected' | translate:{ count: selectedProjectIds.size }) }}
              </div>

              <div class="actions">
                <button class="btn-secondary" (click)="close.emit()">{{ 'adminUsers.settings.cancel' | translate }}</button>
                <button class="btn-primary" [disabled]="savingAccess" (click)="saveAccess()">
                  {{ (savingAccess ? 'adminUsers.settings.saving' : 'adminUsers.settings.save') | translate }}
                </button>
              </div>
            </div>
          }
        }
      </div>
    </div>
  `
})
export class UserSettingsDialogComponent implements OnInit {
  @Input() membre!: MembreDto;
  @Output() close = new EventEmitter<void>();

  private svc     = inject(AdminUserService);
  private projSvc = inject(ProjectService);
  private i18n = inject(I18nService);

  tab: Tab = 'objectifs';
  loading = true;

  // Objectifs tab
  quotaMonth    = new Date().toISOString().slice(0, 7); // "YYYY-MM"
  caCible: number | null = null;
  ventesCountCible: number | null = null;
  savingQuota = false;
  quotaError  = '';
  quotaSuccess = '';

  // Projets tab
  projects: Project[] = [];
  selectedProjectIds = new Set<string>();
  savingAccess = false;
  accessError  = '';
  accessSuccess = '';

  ngOnInit(): void {
    forkJoin({
      quota:  this.svc.getQuota(this.membre.id, this.quotaMonth),
      access: this.svc.getProjectAccess(this.membre.id),
      projs:  this.projSvc.list(),
    }).subscribe({
      next: ({ quota, access, projs }) => {
        this.applyQuota(quota);
        this.projects = projs;
        this.selectedProjectIds = new Set(access.projectIds);
        this.loading = false;
      },
      error: () => { this.loading = false; },
    });
  }

  onMonthChange(): void {
    this.quotaError  = '';
    this.quotaSuccess = '';
    this.svc.getQuota(this.membre.id, this.quotaMonth).subscribe({
      next: (q) => this.applyQuota(q),
    });
  }

  private applyQuota(q: UserQuotaResponse): void {
    this.caCible          = q.caCible;
    this.ventesCountCible = q.ventesCountCible;
  }

  saveQuota(): void {
    this.savingQuota = true;
    this.quotaError  = '';
    this.quotaSuccess = '';
    const req: UserQuotaRequest = {
      month:             this.quotaMonth,
      caCible:           this.caCible,
      ventesCountCible:  this.ventesCountCible,
    };
    this.svc.upsertQuota(this.membre.id, req).subscribe({
      next: (q) => {
        this.applyQuota(q);
        this.quotaSuccess = this.i18n.instant('adminUsers.settings.objectifsSaved');
        this.savingQuota  = false;
      },
      error: () => {
        this.quotaError  = this.i18n.instant('adminUsers.settings.saveError');
        this.savingQuota = false;
      },
    });
  }

  toggleProject(id: string): void {
    if (this.selectedProjectIds.has(id)) this.selectedProjectIds.delete(id);
    else this.selectedProjectIds.add(id);
  }

  saveAccess(): void {
    this.savingAccess = true;
    this.accessError  = '';
    this.accessSuccess = '';
    this.svc.setProjectAccess(this.membre.id, { projectIds: [...this.selectedProjectIds] }).subscribe({
      next: (res) => {
        this.selectedProjectIds = new Set(res.projectIds);
        this.accessSuccess = this.i18n.instant('adminUsers.settings.accessSaved');
        this.savingAccess  = false;
      },
      error: () => {
        this.accessError  = this.i18n.instant('adminUsers.settings.saveError');
        this.savingAccess = false;
      },
    });
  }
}
