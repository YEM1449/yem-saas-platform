# Checklist de relecture notaire — Documents VEFA (Loi 44-00)

**Usage :** Remettre ce document à l'étude notariale avant première utilisation en production. Cocher chaque item après vérification. Relecture recommandée à chaque modification des templates Thymeleaf.

Templates concernés :
- `hlm-backend/src/main/resources/templates/documents/contrat-reservation-vefa.html`
- `hlm-backend/src/main/resources/templates/documents/pv-livraison-vefa.html`

---

## CONTRAT DE RÉSERVATION (Art. 618-3 et suivants Loi 44-00)

### Mentions obligatoires — Art. 618-3

| # | Mention | Template | Statut |
|---|---------|----------|--------|
| 1 | Identité et adresse du promoteur (vendeur) | `model.societeNom`, `model.societeAdresse` | ☐ |
| 2 | Identité et adresse de l'acquéreur | `model.acquereurNom`, `model.acquereurAdresse` | ☐ |
| 3 | Description précise de l'immeuble (adresse, nature, superficie) | `model.propertyRef`, `model.surfaceHabitable` | ☐ |
| 4 | Prix prévisionnel de vente (HT + TVA + TTC) | `model.prixHt`, `model.tvaTaux`, `model.prixTtc` | ☐ |
| 5 | Délai de livraison prévisionnelle | `model.dateLivraisonPrevue` | ☐ |
| 6 | Délai de rétractation (7 jours — Art. 618-3 Loi 44-00) | Clause textuelle présente | ☐ |
| 7 | Montant et modalités du dépôt de garantie (≤ 5 % Art. 618-4) | `model.montantAcompte`, clause 5 % | ☐ |
| 8 | Conditions de remboursement du dépôt en cas d'annulation | Clause textuelle présente | ☐ |
| 9 | Référence du permis de construire ou de l'autorisation administrative | À ajouter : `model.numeroPc` | ☐ |
| 10 | Références cadastrales du terrain | À ajouter : `model.referencesCadastrales` | ☐ |
| 11 | Nom et adresse du notaire rédacteur de l'acte définitif | `model.notaireNom`, `model.notaireEmail` | ☐ |
| 12 | Mention de l'assurance dommages-ouvrage ou garantie bancaire | À ajouter : `model.garantieBancaire` | ☐ |

### Conformité formelle

| # | Point | Statut |
|---|-------|--------|
| 13 | Document rédigé en français (langue officielle du contrat) | ☐ |
| 14 | Chaque page parafée — prévoir espace paraphe en bas de page | ☐ |
| 15 | Signature manuscrite des deux parties (acquéreur + promoteur) | ☐ |
| 16 | Date et lieu de signature | ☐ |
| 17 | Deux exemplaires originaux remis à chaque partie | ☐ |

---

## PROCÈS-VERBAL DE LIVRAISON (Art. 618-13 à 618-17 Loi 44-00)

### Mentions obligatoires

| # | Mention | Template | Statut |
|---|---------|----------|--------|
| 18 | Identité promoteur + acquéreur | `model.societeName`, `model.acquereur` | ☐ |
| 19 | Référence de la vente et du bien | `model.venteRef`, `model.propertyRef` | ☐ |
| 20 | Date effective de livraison | `model.dateLivraison` | ☐ |
| 21 | Liste exhaustive des réserves constatées | Table `model.reserves` | ☐ |
| 22 | Délai de levée des réserves (60 jours — pratique contractuelle) | Mention textuelle présente | ☐ |
| 23 | **Pénalités de retard** (Art. 618-17) : jours de retard, taux journalier, total MAD | Section `.penalite` conditionnelle (`model.joursRetard`) | ☐ |
| 24 | Signatures manuscrites des deux parties | Tableau de signatures présent | ☐ |

### Conformité Art. 618-17 (pénalités de retard)

| # | Point | Statut |
|---|-------|--------|
| 25 | Le taux journalier est conforme au contrat de réservation signé | ☐ |
| 26 | Le calcul `joursRetard × tauxJournalier` est vérifié manuellement | ☐ |
| 27 | La mention "sous réserve des dispositions contractuelles" est présente | ☐ |

---

## Actions correctives identifiées

Les items suivants nécessitent un développement complémentaire avant validation notariale :

| Item | Action | Priorité |
|------|--------|---------|
| #9 — Numéro PC | Ajouter `numeroPc` aux champs commerciaux du projet (`ProjectCommercialFields`) et exposer dans le template | M |
| #10 — Cadastre | Ajouter `referencesCadastrales` à `Societe` ou `Project`, exposer dans le template | M |
| #12 — Garantie bancaire | Ajouter `garantieBancaire` à `Societe`, exposer dans le template + portail | S |

---

## Historique des relectures

| Date | Notaire | Résultat | Corrections appliquées |
|------|---------|----------|------------------------|
| (à compléter après première relecture) | | | |

---

*Référence : Dahir n°1-02-298 du 25 rejeb 1423 portant promulgation de la Loi n°44-00 sur la VEFA (Maroc). Dernière mise à jour : 2026-06-13.*
