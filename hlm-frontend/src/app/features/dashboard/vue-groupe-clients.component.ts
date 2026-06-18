import { Component, OnInit, inject, signal } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { GroupClientService, LinkCandidate, GroupClient } from './group-client.service';

/**
 * Clients Groupe — recognise the same buyer across the owner's sociétés (#005).
 *
 * "Lier, ne pas fusionner" : chaque société garde sa fiche ; on enregistre seulement que c'est
 * la même personne, avec le consentement requis par la Loi 09-08. Le CIN n'est jamais affiché en
 * clair (masqué côté serveur).
 */
@Component({
  selector: 'app-vue-groupe-clients',
  standalone: true,
  imports: [DatePipe, RouterLink, TranslatePipe],
  template: `
    <div class="page">
      <header class="page-head">
        <div>
          <h1>{{ 'dashboard.vueGroupeClients.title' | translate }}</h1>
          <p class="sub">{{ 'dashboard.vueGroupeClients.sub' | translate }}</p>
        </div>
        <a class="btn btn-secondary" routerLink="/app/groupe">{{ 'dashboard.vueGroupeClients.backToGroupe' | translate }}</a>
      </header>

      @if (loading()) {
        <div class="skeleton-stack">
          <span class="skeleton skeleton-card"></span>
          <span class="skeleton skeleton-card"></span>
        </div>
      } @else {
        @if (error()) { <div class="alert alert-error">{{ error() }}</div> }
        @if (notice()) { <div class="alert alert-success">{{ notice() }}</div> }

        <!-- Candidates -->
        <section class="card">
          <h2 class="card-title">{{ 'dashboard.vueGroupeClients.toLink' | translate }} <span class="muted">({{ candidates().length }})</span></h2>
          <p class="card-sub">{{ 'dashboard.vueGroupeClients.toLinkSub' | translate }}</p>
          @if (candidates().length === 0) {
            <p class="empty">{{ 'dashboard.vueGroupeClients.noDuplicate' | translate }}</p>
          } @else {
            @for (cand of candidates(); track cand.cinMasque) {
              <div class="cand">
                <div class="cand-head">
                  <span class="cin">{{ 'dashboard.vueGroupeClients.cin' | translate:{ cin: cand.cinMasque } }}</span>
                  <span class="muted">{{ 'dashboard.vueGroupeClients.fiches' | translate:{ count: cand.contacts.length } }}</span>
                </div>
                <ul class="refs">
                  @for (c of cand.contacts; track c.contactId) {
                    <li>
                      <strong>{{ c.nomComplet }}</strong>
                      <span class="soc-chip">{{ c.societeNom }}</span>
                      @if (c.statut) { <span class="muted">· {{ 'contacts.status.' + c.statut | translate }}</span> }
                    </li>
                  }
                </ul>
                <label class="consent-row">
                  <input type="checkbox" [checked]="consentByCin()[cand.cinMasque] || false"
                         (change)="toggleConsent(cand.cinMasque)" />
                  {{ 'dashboard.vueGroupeClients.consent' | translate }}
                </label>
                <button class="btn btn-primary btn-sm"
                        [disabled]="!consentByCin()[cand.cinMasque] || busy()"
                        (click)="link(cand)">{{ 'dashboard.vueGroupeClients.linkFiches' | translate }}</button>
              </div>
            }
          }
        </section>

        <!-- Linked clients -->
        <section class="card">
          <h2 class="card-title">{{ 'dashboard.vueGroupeClients.linkedClients' | translate }} <span class="muted">({{ linked().length }})</span></h2>
          @if (linked().length === 0) {
            <p class="empty">{{ 'dashboard.vueGroupeClients.noGroupIdentity' | translate }}</p>
          } @else {
            @for (gc of linked(); track gc.groupePersonneId) {
              <div class="linked">
                <div class="linked-contacts">
                  @for (c of gc.contacts; track c.contactId) {
                    <span class="linked-pill">
                      {{ c.nomComplet }} · {{ c.societeNom }}
                      <button class="pill-x" [title]="'dashboard.vueGroupeClients.removeFromGroupTitle' | translate"
                              (click)="unlink(c.contactId)">✕</button>
                    </span>
                  }
                </div>
                <span class="muted small">{{ 'dashboard.vueGroupeClients.linkedOn' | translate:{ date: (gc.lieLe | date:'dd/MM/yyyy') } }}</span>
              </div>
            }
          }
        </section>
      }
    </div>
  `,
  styles: [`
    .page { max-width: 980px; margin: 0 auto; }
    .page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
    .page-head h1 { margin: 0 0 4px; font-size: 1.4rem; }
    .sub { margin: 0; color: var(--c-text-secondary); font-size: 0.875rem; }
    .card { background: var(--c-surface-raised); border: 1px solid var(--c-border); border-radius: 10px; padding: 16px; margin-bottom: 16px; }
    .card-title { margin: 0 0 4px; font-size: 1rem; }
    .card-sub { margin: 0 0 12px; font-size: 0.8rem; color: var(--c-text-muted); }
    .muted { color: var(--c-text-muted); font-weight: 400; }
    .small { font-size: 0.75rem; }
    .empty { color: var(--c-text-muted); font-size: 0.875rem; }
    .cand { border: 1px solid var(--c-border); border-radius: 8px; padding: 12px; margin-bottom: 10px; }
    .cand-head { display: flex; justify-content: space-between; margin-bottom: 8px; }
    .cin { font-family: var(--font-mono, monospace); font-weight: 600; }
    .refs { list-style: none; margin: 0 0 10px; padding: 0; display: flex; flex-direction: column; gap: 4px; }
    .soc-chip { display: inline-block; margin-left: 6px; padding: 1px 8px; border-radius: 999px; background: var(--c-primary-50); color: var(--c-primary-700); font-size: 0.75rem; }
    .consent-row { display: flex; gap: 8px; align-items: flex-start; font-size: 0.8rem; color: var(--c-text-secondary); margin-bottom: 10px; cursor: pointer; }
    .linked { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 8px 0; border-bottom: 1px solid var(--c-border); }
    .linked-contacts { display: flex; flex-wrap: wrap; gap: 6px; }
    .linked-pill { display: inline-flex; align-items: center; gap: 6px; padding: 3px 10px; border-radius: 999px; background: var(--c-bg); border: 1px solid var(--c-border); font-size: 0.8rem; }
    .pill-x { border: none; background: none; cursor: pointer; color: var(--c-text-muted); font-size: 0.8rem; line-height: 1; }
    .pill-x:hover { color: var(--c-danger); }
  `]
})
export class VueGroupeClientsComponent implements OnInit {
  private i18n = inject(I18nService);
  private api = inject(GroupClientService);

  loading = signal(true);
  busy = signal(false);
  error = signal('');
  notice = signal('');
  candidates = signal<LinkCandidate[]>([]);
  linked = signal<GroupClient[]>([]);
  consentByCin = signal<Record<string, boolean>>({});

  ngOnInit(): void { this.reload(); }

  reload(): void {
    this.loading.set(true);
    this.error.set('');
    let pending = 2;
    const done = () => { if (--pending === 0) this.loading.set(false); };
    this.api.candidates().subscribe({
      next: c => { this.candidates.set(c); done(); },
      error: () => { this.error.set(this.i18n.instant('dashboard.vueGroupeClients.candidatesError')); done(); }
    });
    this.api.list().subscribe({
      next: l => { this.linked.set(l); done(); },
      error: () => { done(); }
    });
  }

  toggleConsent(cin: string): void {
    const map = { ...this.consentByCin() };
    map[cin] = !map[cin];
    this.consentByCin.set(map);
  }

  link(cand: LinkCandidate): void {
    if (!this.consentByCin()[cand.cinMasque]) return;
    this.busy.set(true);
    this.error.set('');
    this.notice.set('');
    this.api.link(cand.contacts.map(c => c.contactId), true).subscribe({
      next: () => { this.notice.set(this.i18n.instant('dashboard.vueGroupeClients.linked')); this.busy.set(false); this.reload(); },
      error: (err) => {
        this.error.set(err?.error?.message ?? this.i18n.instant('dashboard.vueGroupeClients.linkError'));
        this.busy.set(false);
      }
    });
  }

  unlink(contactId: string): void {
    if (!confirm(this.i18n.instant('dashboard.vueGroupeClients.unlinkConfirm'))) return;
    this.api.unlink(contactId).subscribe({
      next: () => { this.notice.set(this.i18n.instant('dashboard.vueGroupeClients.unlinked')); this.reload(); },
      error: () => { this.error.set(this.i18n.instant('dashboard.vueGroupeClients.unlinkError')); }
    });
  }

}
