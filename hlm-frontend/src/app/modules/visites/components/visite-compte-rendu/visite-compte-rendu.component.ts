import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslatePipe } from '@ngx-translate/core';
import { I18nService } from '../../../../core/i18n/i18n.service';
import { VisiteApiService, Visite, ResultatVisite } from '../../services/visite-api.service';

/**
 * Compte-rendu capture (RG-V06). Recording it transitions the visite to REALISEE. When the
 * outcome is OPPORTUNITE_CREEE we surface a "Créer une vente" CTA that opens the existing
 * pipeline pre-filled from the visite (linking is completed in P5-T2).
 */
@Component({
  selector: 'app-visite-compte-rendu',
  standalone: true,
  imports: [FormsModule, RouterLink, TranslatePipe],
  templateUrl: './visite-compte-rendu.component.html',
  styleUrl: './visite-compte-rendu.component.css',
})
export class VisiteCompteRenduComponent implements OnInit {
  private api = inject(VisiteApiService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private i18n = inject(I18nService);

  readonly resultats: ResultatVisite[] = ['INTERESSE', 'A_RELANCER', 'PAS_INTERESSE', 'OPPORTUNITE_CREEE'];

  visite = signal<Visite | null>(null);
  saved = signal<Visite | null>(null);
  compteRendu = '';
  resultat: ResultatVisite | null = null;

  saving = signal(false);
  error = signal('');

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.api.get(id).subscribe({
      next: v => this.visite.set(v),
      error: () => this.error.set(this.i18n.instant('visites.detail.loadError')),
    });
  }

  get canSubmit(): boolean { return !!this.compteRendu.trim() && !!this.resultat; }

  submit(): void {
    if (!this.canSubmit) {
      this.error.set(this.i18n.instant(
        this.compteRendu.trim() ? 'visites.compteRendu.resultatRequired' : 'visites.compteRendu.texteRequired'));
      return;
    }
    this.error.set('');
    this.saving.set(true);
    this.api.enregistrerCompteRendu(this.visite()!.id, {
      compteRendu: this.compteRendu.trim(),
      resultat: this.resultat!,
    }).subscribe({
      next: v => { this.saved.set(v); this.saving.set(false); },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set((err.error as { message?: string })?.message
          ?? this.i18n.instant('visites.compteRendu.genericError', { status: err.status }));
      },
    });
  }

  /** Open the existing vente pipeline pre-filled from this visite (P5-T2 links vente_id back). */
  createVente(): void {
    const v = this.saved()!;
    this.router.navigate(['/app/ventes/new'], {
      queryParams: { visiteId: v.id, contactId: v.contactId, propertyId: v.propertyId },
    });
  }

  backToDetail(): void {
    this.router.navigate(['/app/visites', this.visite()!.id]);
  }
}
