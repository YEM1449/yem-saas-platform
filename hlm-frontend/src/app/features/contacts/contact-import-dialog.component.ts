import { Component, EventEmitter, Output, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { ContactService, ContactImportReport } from './contact.service';
import { I18nService } from '../../core/i18n/i18n.service';

/**
 * CSV contact import dialog (finding #002).
 *
 * Three steps: (1) pick the file + state the Loi 09-08 legal basis, (2) automatic dry-run
 * preview — nothing is created yet, (3) confirmed import with the final report. Duplicates
 * (in-file or already in the société) are skipped, so re-importing the same file is safe.
 */
@Component({
  selector: 'app-contact-import-dialog',
  standalone: true,
  imports: [FormsModule, TranslatePipe],
  template: `
    <div class="modal-backdrop" (click)="close()">
      <div class="modal import-modal" (click)="$event.stopPropagation()">

        <div class="modal-header">
          <h2 class="modal-title">{{ 'contacts.import.title' | translate }}</h2>
          <button class="modal-close" (click)="close()" [disabled]="busy()" [attr.aria-label]="'common.actions.close' | translate">✕</button>
        </div>

        <div class="modal-body">
          @if (error()) {
            <div class="alert alert-error">{{ error() }}</div>
          }

          @if (step() === 'setup') {
            <p class="import-hint">
              {{ 'contacts.import.hintIntro' | translate }}
              <strong>{{ 'contacts.import.colFirstName' | translate }}</strong> {{ 'contacts.import.and' | translate }} <strong>{{ 'contacts.import.colLastName' | translate }}</strong>{{ 'contacts.import.hintRest' | translate }}
              <a href="#" (click)="downloadTemplate($event)">{{ 'contacts.import.downloadTemplate' | translate }}</a>
            </p>

            <div class="form-group">
              <label class="form-label required">{{ 'contacts.import.fileLabel' | translate }}</label>
              <input class="form-control" type="file" accept=".csv,text/csv"
                     (change)="onFile($event)" data-testid="import-file-input" />
            </div>

            <div class="form-group">
              <label class="form-label required">{{ 'contacts.import.legalBasis' | translate }}</label>
              <label class="radio-row">
                <input type="radio" name="basis" value="consent" [(ngModel)]="basisChoice" />
                {{ 'contacts.import.basisConsent' | translate }}
              </label>
              <label class="radio-row">
                <input type="radio" name="basis" value="legitimate" [(ngModel)]="basisChoice" />
                {{ 'contacts.import.basisLegitimate' | translate }}
              </label>
            </div>
          }

          @if (report(); as r) {
            <div class="report" [class.report-final]="step() === 'done'">
              <p class="report-headline">
                @if (r.dryRun) {
                  {{ 'contacts.import.previewHeadline' | translate }}
                } @else {
                  {{ 'contacts.import.doneHeadline' | translate }}
                }
                <strong>{{ r.created }}</strong> {{ 'contacts.import.contactsWord' | translate }} {{ r.dryRun ? ('contacts.import.willBeCreated' | translate) : ('contacts.import.createdWord' | translate) }},
                <strong>{{ r.duplicateCount }}</strong> {{ 'contacts.import.duplicatesIgnored' | translate }},
                <strong>{{ r.errorCount }}</strong> {{ 'contacts.import.linesError' | translate }}
                {{ 'contacts.import.onLines' | translate:{ count: r.totalRows } }}
              </p>
              @if (r.ignoredColumns.length > 0) {
                <p class="report-note">{{ 'contacts.import.ignoredColumns' | translate }} {{ r.ignoredColumns.join(', ') }}</p>
              }
              @if (r.errors.length > 0 || r.duplicates.length > 0) {
                <div class="issues-wrap">
                  <table class="table issues-table">
                    <thead><tr><th>{{ 'contacts.import.thLine' | translate }}</th><th>{{ 'contacts.import.thContact' | translate }}</th><th>{{ 'contacts.import.thReason' | translate }}</th></tr></thead>
                    <tbody>
                      @for (e of r.errors; track e.line) {
                        <tr class="issue-error"><td>{{ e.line }}</td><td>{{ e.identity }}</td><td>{{ e.reason }}</td></tr>
                      }
                      @for (d of r.duplicates; track d.line) {
                        <tr class="issue-dup"><td>{{ d.line }}</td><td>{{ d.identity }}</td><td>{{ d.reason }}</td></tr>
                      }
                    </tbody>
                  </table>
                  @if (r.truncated) {
                    <p class="report-note">{{ 'contacts.import.truncated' | translate }}</p>
                  }
                </div>
              }
            </div>
          }
        </div>

        <div class="modal-footer">
          <button class="btn btn-secondary" (click)="close()" [disabled]="busy()">
            {{ step() === 'done' ? ('common.actions.close' | translate) : ('common.actions.cancel' | translate) }}
          </button>
          @if (step() === 'setup') {
            <button class="btn btn-primary" (click)="analyze()"
                    [disabled]="!file || !basisChoice || busy()" data-testid="import-analyze">
              {{ busy() ? ('contacts.import.analyzing' | translate) : ('contacts.import.analyzeFile' | translate) }}
            </button>
          } @else if (step() === 'preview') {
            <button class="btn btn-secondary" (click)="backToSetup()" [disabled]="busy()">{{ 'contacts.import.changeFile' | translate }}</button>
            <button class="btn btn-primary" (click)="confirmImport()"
                    [disabled]="busy() || report()?.created === 0" data-testid="import-confirm">
              {{ busy() ? ('contacts.import.importing' | translate) : ('contacts.import.importN' | translate:{ count: (report()?.created ?? 0) }) }}
            </button>
          }
        </div>
      </div>
    </div>
  `,
  styles: [`
    .import-modal { max-width: 640px; }
    .import-hint { font-size: 0.85rem; color: var(--c-text-secondary); margin: 0 0 14px; }
    .radio-row { display: flex; align-items: center; gap: 8px; font-size: 0.875rem; padding: 4px 0; cursor: pointer; }
    .report-headline { font-size: 0.9rem; }
    .report-note { font-size: 0.78rem; color: var(--c-text-muted); }
    .issues-wrap { max-height: 260px; overflow-y: auto; border: 1px solid var(--c-border); border-radius: 8px; }
    .issues-table { font-size: 0.8rem; }
    .issue-error td:last-child { color: var(--status-retire-fg); }
    .issue-dup td:last-child { color: var(--c-text-muted); }
    .modal-footer { display: flex; justify-content: flex-end; gap: 8px; }
  `]
})
export class ContactImportDialogComponent {
  private svc = inject(ContactService);
  private i18n = inject(I18nService);

  @Output() closed = new EventEmitter<void>();
  /** Emitted after a successful (non-dry-run) import so the list can reload. */
  @Output() imported = new EventEmitter<number>();

  step = signal<'setup' | 'preview' | 'done'>('setup');
  busy = signal(false);
  error = signal('');
  report = signal<ContactImportReport | null>(null);

  file: File | null = null;
  basisChoice: 'consent' | 'legitimate' | null = null;

  onFile(event: Event): void {
    this.file = (event.target as HTMLInputElement).files?.[0] ?? null;
  }

  analyze(): void { this.run(true); }
  confirmImport(): void { this.run(false); }

  private run(dryRun: boolean): void {
    if (!this.file || !this.basisChoice) return;
    this.busy.set(true);
    this.error.set('');
    this.svc.importCsv(this.file, {
      dryRun,
      consentGiven: this.basisChoice === 'consent',
      processingBasis: this.basisChoice === 'legitimate' ? 'LEGITIMATE_INTEREST' : undefined
    }).subscribe({
      next: r => {
        this.report.set(r);
        this.step.set(dryRun ? 'preview' : 'done');
        this.busy.set(false);
        if (!dryRun) this.imported.emit(r.created);
      },
      error: err => {
        this.error.set(err?.error?.message
          ?? this.i18n.instant('contacts.import.importError'));
        this.busy.set(false);
      }
    });
  }

  backToSetup(): void {
    this.report.set(null);
    this.step.set('setup');
  }

  downloadTemplate(event: Event): void {
    event.preventDefault();
    const csv = 'prenom;nom;telephone;email;cin;adresse;notes\n'
              + 'Karim;Alaoui;0612345678;karim@exemple.ma;AB123456;Casablanca;Prospect salon immobilier\n';
    const url = URL.createObjectURL(new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = 'modele-import-contacts.csv';
    a.click();
    URL.revokeObjectURL(url);
  }

  close(): void {
    if (!this.busy()) this.closed.emit();
  }
}
