# Déclaration CNDP — registre & procédure (Loi 09-08)

> Finding #026. Le traitement de données personnelles opéré par la plateforme (identité, **CIN**,
> coordonnées, **données financières** des dossiers VEFA) doit être déclaré à la **CNDP**
> (Commission Nationale de contrôle de la Protection des Données à caractère personnel) par
> **chaque société** (responsable de traitement). Ce document est l'artefact d'évidence pour un
> audit / une revue d'achat.

## Qui déclare quoi

- **Responsable de traitement** : chaque société promotrice (pas la plateforme). YEM HLM est
  **sous-traitant technique**.
- **Traitement déclaré** : « Gestion des dossiers d'acquisition immobilière (VEFA) — prospects,
  acquéreurs, échéanciers d'appels de fonds, documents contractuels ».
- **Données sensibles à mentionner** : numéro de CIN, données financières (appels de fonds,
  dossier de financement).

## Où le numéro est enregistré dans le produit

Une fois la déclaration déposée, l'administrateur saisit le **n° de récépissé** et la **date**
sur la fiche société (champs déjà présents en base) :

| Champ Societe | Colonne DB | Usage |
|---|---|---|
| `numeroCndp` | `numero_cndp` | Affiché dans la politique de confidentialité du portail (§8) |
| `dateDeclarationCndp` | `date_declaration_cndp` | Affiché à côté du numéro |
| `emailDpo` / `dpoNom` | `email_dpo` / `dpo_nom` | Contact d'exercice des droits (§7) |
| `nom` / `rc` / `siretIce` / `adresseSiege` | — | Mentions légales (éditeur / responsable) |

Dès que `numeroCndp` est renseigné, le portail **acquéreur** affiche automatiquement le numéro
réel à la place du texte générique — la déclaration devient *évidente sur la surface
consommateur* (`GET /api/portal/tenant-info` → pages `/portal/privacy` et
`/portal/mentions-legales`).

Le **score de conformité** (`GET /api/admin/societes/{id}/compliance`) compte déjà le numéro
CNDP (+30) et le signale dans `missingFields` tant qu'il est absent — tableau de bord pour
l'administrateur.

## Checklist de dépôt (par société)

- [ ] Identifier le responsable de traitement (raison sociale, RC, ICE, siège).
- [ ] Désigner le point de contact / DPO (e-mail dédié pour l'exercice des droits).
- [ ] Décrire le traitement (finalités, données, durée de conservation, destinataires :
      promoteur, notaire, établissement de financement).
- [ ] Déposer la déclaration sur le portail CNDP (https://www.cndp.ma).
- [ ] Récupérer le **récépissé** (numéro + date).
- [ ] Saisir `numeroCndp`, `dateDeclarationCndp`, `emailDpo`, `dpoNom` sur la fiche société.
- [ ] Vérifier l'affichage sur `/portal/privacy` et `/portal/mentions-legales`.
- [ ] Vérifier que le score de conformité société atteint 100 %.

## Registre des déclarations

| Société | Responsable de traitement | N° récépissé CNDP | Date dépôt | DPO / contact | Statut |
|---|---|---|---|---|---|
| _(à compléter)_ | | | | | ⬜ à déposer |

> Mettre à jour ce tableau à chaque dépôt. Conserver une copie du récépissé CNDP dans le coffre
> documentaire de la société.

## Transfert intra-groupe (lien #005)

Le rapprochement d'un même acheteur entre plusieurs sociétés d'un groupe (« Clients Groupe »)
constitue un **transfert intra-groupe** : il n'est réalisé qu'avec le **consentement** du client
(déjà imposé par `GroupClientService`). Mentionner ce transfert dans la déclaration CNDP de
chaque société concernée.
