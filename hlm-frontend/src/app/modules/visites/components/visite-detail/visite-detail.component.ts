import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslatePipe } from '@ngx-translate/core';
import { I18nService } from '../../../../core/i18n/i18n.service';
import { VisiteApiService, Visite } from '../../services/visite-api.service';
import { formatHeure, formatDateLong } from '../../services/casablanca-time';

/**
 * Visite detail panel + action surface (RG-V02 transitions, RG-V08 cancel). Actions are
 * gated by the current statut so only valid transitions are offered. The .ics export and the
 * linked-vente link are P5 integrations.
 */
@Component({
  selector: 'app-visite-detail',
  standalone: true,
  imports: [RouterLink, FormsModule, TranslatePipe],
  templateUrl: './visite-detail.component.html',
  styleUrl: './visite-detail.component.css',
})
export class VisiteDetailComponent implements OnInit {
  private api = inject(VisiteApiService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private i18n = inject(I18nService);

  visite = signal<Visite | null>(null);
  loadError = signal('');
  actionError = signal('');
  busy = signal(false);
  showAnnuler = signal(false);
  annulerRaison = '';

  readonly fmtHeure = formatHeure;
  readonly fmtDate = formatDateLong;

  readonly statut = computed(() => this.visite()?.statut ?? null);
  readonly canConfirmer = computed(() => this.statut() === 'PLANIFIEE');
  readonly canRealiser = computed(() => this.statut() === 'CONFIRMEE');
  readonly canNoShow = computed(() => this.statut() === 'CONFIRMEE');
  readonly canAnnuler = computed(() => this.statut() === 'PLANIFIEE' || this.statut() === 'CONFIRMEE');
  readonly canModifier = computed(() => this.canAnnuler());
  readonly canIcs = computed(() => this.statut() !== 'ANNULEE');

  ngOnInit(): void { this.load(); }

  private load(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.api.get(id).subscribe({
      next: v => this.visite.set(v),
      error: () => this.loadError.set(this.i18n.instant('visites.detail.loadError')),
    });
  }

  statutClass(): string { return `pill-${this.statut()?.toLowerCase()}`; }

  confirmer(): void { this.run(this.api.confirmer(this.id())); }
  noShow(): void {
    if (!confirm(this.i18n.instant('visites.detail.noShowConfirm'))) return;
    this.run(this.api.noShow(this.id()));
  }
  annulerConfirm(): void {
    if (!this.annulerRaison.trim()) return;
    this.run(this.api.annuler(this.id(), this.annulerRaison.trim()));
    this.showAnnuler.set(false);
  }

  compteRendu(): void { this.router.navigate(['/app/visites', this.id(), 'compte-rendu']); }
  modifier(): void { this.router.navigate(['/app/visites', this.id(), 'modifier']); }

  exportIcs(): void {
    this.api.ics(this.id()).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url; a.download = `visite-${this.id()}.ics`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (err: HttpErrorResponse) =>
        this.actionError.set(this.i18n.instant('visites.detail.actionError', { status: err.status })),
    });
  }

  private id(): string { return this.visite()!.id; }

  private run(obs: import('rxjs').Observable<Visite>): void {
    this.actionError.set('');
    this.busy.set(true);
    obs.subscribe({
      next: v => { this.visite.set(v); this.busy.set(false); },
      error: (err: HttpErrorResponse) => {
        this.busy.set(false);
        this.actionError.set((err.error as { message?: string })?.message
          ?? this.i18n.instant('visites.detail.actionError', { status: err.status }));
      },
    });
  }
}
