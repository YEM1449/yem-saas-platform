import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PortalAuthService } from '../../core/portal-auth.service';
import { PortalTenantInfo } from '../../../core/models/portal.model';

/**
 * Politique de confidentialité — portail acquéreur (finding #025).
 *
 * Notice d'information au sens de la Loi 09-08 (protection des données personnelles, Maroc)
 * et conforme aux attentes de la CNDP. Page publique : accessible avant authentification
 * (un acheteur doit pouvoir lire la politique avant de demander un lien d'accès).
 *
 * Le contenu est rédigé en français (langue du dossier VEFA). Les éléments propres au
 * promoteur (raison sociale, n° de déclaration CNDP, contact DPO) sont à compléter par
 * l'exploitant — repérés par « [à compléter] ».
 */
@Component({
  selector: 'app-portal-privacy',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="legal-page">
      <div class="legal-card">
        <a routerLink="/portal/login" class="legal-back">← Retour</a>
        <h1>Politique de confidentialité</h1>
        <p class="legal-updated">Dernière mise à jour : juin 2026</p>

        <p>
          La présente notice décrit comment vos données personnelles sont traitées dans le cadre
          de votre dossier d'acquisition immobilière (Vente en l'État Futur d'Achèvement), en
          application de la <strong>Loi n° 09-08</strong> relative à la protection des personnes
          physiques à l'égard du traitement des données à caractère personnel.
        </p>

        <h2>1. Responsable du traitement</h2>
        <p>
          Le responsable du traitement est le promoteur immobilier auprès duquel vous avez engagé
          votre acquisition (la société vendeuse)@if (info()?.legalName) { :
          <strong>{{ info()!.legalName }}</strong>@if (info()?.adresseSiege) {, {{ info()!.adresseSiege }}}}.
          La plateforme YEM HLM agit en qualité de sous-traitant technique pour le compte de ce
          promoteur.
        </p>

        <h2>2. Données collectées</h2>
        <ul>
          <li>Données d'identité : nom, prénom, numéro de CIN ou de passeport, date de naissance ;</li>
          <li>Coordonnées : adresse, e-mail, téléphone ;</li>
          <li>Données relatives au dossier : bien acquis, échéancier de paiement, documents contractuels ;</li>
          <li>Données financières strictement nécessaires au suivi des appels de fonds.</li>
        </ul>

        <h2>3. Finalités</h2>
        <p>
          Vos données sont utilisées uniquement pour : la gestion de votre dossier d'acquisition,
          le suivi de l'échéancier légal des appels de fonds (Art. 618-17 de la Loi 44-00), la mise
          à disposition de vos documents (contrat de réservation, PV de livraison), et la
          communication relative à votre acquisition.
        </p>

        <h2>4. Base légale</h2>
        <p>
          Le traitement repose sur l'exécution du contrat qui vous lie au promoteur et, le cas
          échéant, sur votre consentement (notamment pour l'accès au portail).
        </p>

        <h2>5. Destinataires</h2>
        <p>
          Vos données ne sont communiquées qu'aux personnes habilitées du promoteur, et, lorsque
          la loi l'exige, au notaire chargé de l'acte et à l'établissement de financement concerné.
          Elles ne sont jamais cédées à des tiers à des fins commerciales.
        </p>

        <h2>6. Durée de conservation</h2>
        <p>
          Vos données sont conservées pendant la durée de la relation contractuelle, puis archivées
          conformément aux obligations légales et fiscales applicables, avant suppression ou
          anonymisation.
        </p>

        <h2>7. Vos droits</h2>
        <p>
          Conformément à la Loi 09-08, vous disposez d'un droit d'accès, de rectification,
          d'opposition et de suppression de vos données. Pour exercer ces droits, contactez
          @if (info()?.dpoEmail) {
            le délégué à la protection des données à l'adresse
            <strong>{{ info()!.dpoEmail }}</strong>@if (info()?.dpoName) { ({{ info()!.dpoName }})}.
          } @else {
            votre promoteur, qui vous indiquera les modalités d'exercice de ces droits.
          }
          Vous pouvez également saisir la <strong>CNDP</strong> (Commission Nationale de contrôle
          de la Protection des Données à caractère personnel) en cas de difficulté.
        </p>

        <h2>8. Déclaration CNDP</h2>
        <p>
          @if (info()?.cndpNumber) {
            Ce traitement a fait l'objet d'une déclaration auprès de la CNDP sous le numéro
            <strong>{{ info()!.cndpNumber }}</strong>@if (info()?.cndpDeclarationDate) {
              (déclaration du {{ info()!.cndpDeclarationDate }})}.
          } @else {
            Le traitement de vos données est déclaré auprès de la CNDP conformément à la Loi 09-08 ;
            le numéro de récépissé peut être obtenu auprès de votre promoteur.
          }
        </p>

        <h2>9. Sécurité</h2>
        <p>
          L'accès à votre espace se fait par lien sécurisé à usage unique (sans mot de passe stocké),
          via une connexion chiffrée. Vos données financières ne sont jamais exposées à d'autres
          acquéreurs.
        </p>

        <p class="legal-links">
          <a routerLink="/portal/mentions-legales">Mentions légales</a>
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
    ul { padding-left: 20px; }
    li { margin-bottom: 4px; }
    .legal-links { margin-top: 28px; padding-top: 16px; border-top: 1px solid #e8dec5; }
    .legal-links a { color: #16a34a; }
  `]
})
export class PortalPrivacyComponent implements OnInit {
  private auth = inject(PortalAuthService);

  /** Société legal/CNDP info — populated for authenticated buyers; null pre-auth (public visit). */
  info = signal<PortalTenantInfo | null>(null);

  ngOnInit(): void {
    // Best-effort: a logged-in buyer sees the recorded values; a public visitor (no session)
    // gets a clean generic notice (error ignored).
    this.auth.getTenantInfo().subscribe({
      next: (i) => this.info.set(i),
      error: () => this.info.set(null),
    });
  }
}
