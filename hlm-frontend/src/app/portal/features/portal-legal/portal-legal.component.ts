import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { PortalAuthService } from '../../core/portal-auth.service';
import { PortalTenantInfo } from '../../../core/models/portal.model';

/**
 * Mentions légales — portail acquéreur (finding #025). Page publique.
 * Éléments propres à l'exploitant à compléter (« [à compléter] »).
 */
@Component({
  selector: 'app-portal-legal',
  standalone: true,
  imports: [RouterLink, TranslateModule],
  template: `
    <div class="legal-page">
      <div class="legal-card">
        <a routerLink="/portal/login" class="legal-back">← {{ 'portal.legal.back' | translate }}</a>
        <h1>Mentions légales</h1>
        <p class="legal-updated">Dernière mise à jour : juin 2026</p>

        <h2>Éditeur de la plateforme</h2>
        <p>
          L'espace acquéreur est édité et exploité techniquement par la plateforme <strong>YEM HLM</strong>,
          pour le compte du promoteur immobilier vendeur@if (info()?.legalName) {
            <strong> {{ info()!.legalName }}</strong>@if (info()?.adresseSiege) {, dont le siège est
            situé {{ info()!.adresseSiege }}}@if (info()?.rc) { — RC {{ info()!.rc }}}@if (info()?.ice) {,
            ICE {{ info()!.ice }}}.
          } @else {
            (raison sociale, RC et ICE disponibles auprès de votre promoteur).
          }
        </p>

        <h2>Hébergement</h2>
        <p>
          L'application est hébergée sur une infrastructure cloud sécurisée ; le stockage des
          documents est assuré sur un service objet en région européenne.
          <strong>[à compléter : nom de l'hébergeur et coordonnées]</strong>.
        </p>

        <h2>Propriété intellectuelle</h2>
        <p>
          L'ensemble des éléments de cet espace (marques, contenus, interface) est protégé. Toute
          reproduction non autorisée est interdite.
        </p>

        <h2>Responsabilité</h2>
        <p>
          Les informations relatives à votre dossier sont fournies par le promoteur. En cas
          d'écart entre l'espace en ligne et vos documents contractuels signés, ces derniers font foi.
        </p>

        <h2>Données personnelles</h2>
        <p>
          Le traitement de vos données personnelles est décrit dans notre
          <a routerLink="/portal/privacy">politique de confidentialité</a> (Loi 09-08).
        </p>
      </div>
    </div>
  `,
  styles: [`
    .legal-page { min-height: 100vh; background: #f5f3ec; padding: 32px 16px; display: flex; justify-content: center; }
    .legal-card { background: #fff; max-width: 760px; width: 100%; border-radius: 14px; padding: 32px 36px;
      box-shadow: 0 2px 16px rgba(0,0,0,.06); line-height: 1.6; color: #2d241a; }
    .legal-back { display: inline-block; margin-bottom: 16px; color: #16a34a; text-decoration: none; font-size: .9rem; }
    .legal-back:hover { text-decoration: underline; }
    h1 { font-size: 1.5rem; margin: 0 0 4px; }
    h2 { font-size: 1.05rem; margin: 24px 0 6px; }
    .legal-updated { color: #9a8e77; font-size: .8rem; margin: 0 0 20px; }
    a { color: #16a34a; }
  `]
})
export class PortalLegalComponent implements OnInit {
  private auth = inject(PortalAuthService);

  /** Société legal identity — populated for authenticated buyers; null pre-auth (public visit). */
  info = signal<PortalTenantInfo | null>(null);

  ngOnInit(): void {
    this.auth.getTenantInfo().subscribe({
      next: (i) => this.info.set(i),
      error: () => this.info.set(null),
    });
  }
}
