import { Component, EventEmitter, Output, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ContactService, ContactImportReport } from './contact.service';

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
  imports: [FormsModule],
  template: `
    <div class="modal-backdrop" (click)="close()">
      <div class="modal import-modal" (click)="$event.stopPropagation()">

        <div class="modal-header">
          <h2 class="modal-title">Importer des contacts (CSV)</h2>
          <button class="modal-close" (click)="close()" [disabled]="busy()" aria-label="Fermer">✕</button>
        </div>

        <div class="modal-body">
          @if (error()) {
            <div class="alert alert-error">{{ error() }}</div>
          }

          @if (step() === 'setup') {
            <p class="import-hint">
              Fichier CSV (Excel : « Enregistrer sous → CSV UTF-8 »). Colonnes obligatoires :
              <strong>prénom</strong> et <strong>nom</strong> ; reconnues : téléphone, email, CIN,
              adresse, notes. Chaque ligne doit avoir un téléphone ou un email.
              <a href="#" (click)="downloadTemplate($event)">Télécharger le modèle</a>
            </p>

            <div class="form-group">
              <label class="form-label required">Fichier</label>
              <input class="form-control" type="file" accept=".csv,text/csv"
                     (change)="onFile($event)" data-testid="import-file-input" />
            </div>

            <div class="form-group">
              <label class="form-label required">Base juridique (Loi 09-08)</label>
              <label class="radio-row">
                <input type="radio" name="basis" value="consent" [(ngModel)]="basisChoice" />
                Ces contacts ont donné leur consentement (papier, email…)
              </label>
              <label class="radio-row">
                <input type="radio" name="basis" value="legitimate" [(ngModel)]="basisChoice" />
                Intérêt légitime — relation commerciale existante
              </label>
            </div>
          }

          @if (report(); as r) {
            <div class="report" [class.report-final]="step() === 'done'">
              <p class="report-headline">
                @if (r.dryRun) {
                  Aperçu — rien n'a encore été créé :
                } @else {
                  Import terminé :
                }
                <strong>{{ r.created }}</strong> contact(s) {{ r.dryRun ? 'seront créés' : 'créés' }},
                <strong>{{ r.duplicateCount }}</strong> doublon(s) ignoré(s),
                <strong>{{ r.errorCount }}</strong> ligne(s) en erreur
                (sur {{ r.totalRows }} lignes).
              </p>
              @if (r.ignoredColumns.length > 0) {
                <p class="report-note">Colonnes non reconnues, ignorées : {{ r.ignoredColumns.join(', ') }}</p>
              }
              @if (r.errors.length > 0 || r.duplicates.length > 0) {
                <div class="issues-wrap">
                  <table class="table issues-table">
                    <thead><tr><th>Ligne</th><th>Contact</th><th>Motif</th></tr></thead>
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
                    <p class="report-note">Liste tronquée — seules les 100 premières anomalies sont affichées.</p>
                  }
                </div>
              }
            </div>
          }
        </div>

        <div class="modal-footer">
          <button class="btn btn-secondary" (click)="close()" [disabled]="busy()">
            {{ step() === 'done' ? 'Fermer' : 'Annuler' }}
          </button>
          @if (step() === 'setup') {
            <button class="btn btn-primary" (click)="analyze()"
                    [disabled]="!file || !basisChoice || busy()" data-testid="import-analyze">
              {{ busy() ? 'Analyse…' : 'Analyser le fichier' }}
            </button>
          } @else if (step() === 'preview') {
            <button class="btn btn-secondary" (click)="backToSetup()" [disabled]="busy()">Changer de fichier</button>
            <button class="btn btn-primary" (click)="confirmImport()"
                    [disabled]="busy() || report()?.created === 0" data-testid="import-confirm">
              {{ busy() ? 'Import en cours…' : 'Importer ' + (report()?.created ?? 0) + ' contact(s)' }}
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
          ?? 'Import impossible. Vérifiez le format du fichier puis réessayez.');
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
