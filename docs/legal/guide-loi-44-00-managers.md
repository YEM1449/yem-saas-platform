# Guide légal Loi 44-00 (VEFA Maroc) — pour managers

> Comment la plateforme YEM HLM applique la **Loi n°44-00** (Dahir n°1-02-298 du 3 octobre 2002,
> articles 618-1 à 618-21 du DOC) à la Vente en l'État Futur d'Achèvement. Constantes légales :
> [`docs/legal/loi-44-00-constantes.md`](loi-44-00-constantes.md). Implémentation Wave 12.

## 1. Le pipeline de vente VEFA

```
PROSPECT → OPTION → RESERVE → EN_RETRACTATION → ACOMPTE → COMPROMIS
        → FINANCEMENT → ACTE → LIVRE_AVEC_RESERVES → RESERVES_LEVEES → LIVRE_DEFINITIF
ANNULE = terminal (depuis tout état non terminal)
```

Chaque transition est vérifiée par la plateforme — une transition interdite est refusée
(**409 `INVALID_STATUS_TRANSITION`**). L'interface n'affiche que les actions autorisées par
l'état courant.

## 2. L'option (blocage temporaire)

- **Quoi** : bloquer un bien 24 à 72 h pour un prospect, avant réservation.
- **Comment** : depuis un bien `ACTIVE` → « Poser une option ». Le bien passe `RESERVED`.
- **Expiration automatique** : si l'option n'est pas confirmée avant l'heure d'expiration, une
  vérification automatique toutes les heures l'annule (`ANNULE`) et libère le bien. Aucune action manuelle requise.
- **Règle** : un seul engagement actif par bien (RG-B03) — impossible d'optionner/vendre un bien
  déjà engagé (**409 `PROPERTY_ALREADY_ENGAGED`**).

## 3. La réservation et le délai de rétractation (Art. 618-3 / 618-4)

- **Confirmer la réservation** (fiche vente → *Actions VEFA*) : saisir le **dépôt de garantie**.
  - **Plafond légal 5 %** du prix (Art. 618-4) : un dépôt supérieur est **refusé**
    (**422 `VIOLATION_LEGALE`**).
- À la confirmation, la vente entre en **`EN_RETRACTATION`** et la plateforme ouvre le
  **délai légal de rétractation de 7 jours** (Art. 618-3 ; configurable par marché via la
  configuration marché, ex. France = 10 j). La date limite est affichée sur la fiche.
- **Exercer la rétractation** (bouton dédié, ADMIN/MANAGER) :
  - **Dans le délai** → la vente est annulée, le bien libéré, et un **remboursement « à rembourser »
    est créé automatiquement** sur la fiche vente (montant = dépôt versé). Le gestionnaire ajuste le
    montant si besoin puis le marque **« remboursé »** (date + moyen : virement/chèque/espèces) — la
    confirmation est tracée dans le journal d'audit (`REMBOURSEMENT_EFFECTUE`).
  - **Hors délai** → l'action est refusée (**409 `RETRACTATION_IMPOSSIBLE`**).
  - **Sans rétractation** : à l'expiration du délai, une vérification automatique fait passer la vente en `RESERVE`
    (la vente continue).

## 4. L'échéancier légal des appels de fonds (Art. 618-17)

- Bouton **« Générer l'échéancier légal »** sur la fiche vente (prix de vente requis).
- Génère les **7 appels** réglementaires, plafonnés :

  | Étape | % max |
  |---|---|
  | Signature du contrat | 5 % |
  | Achèvement fondations | 10 % |
  | Achèvement plancher RDC | 15 % |
  | Achèvement gros œuvre | 20 % |
  | Achèvement couverture | 20 % |
  | Achèvement second œuvre | 20 % |
  | Livraison / titre de propriété | 10 % |

- **Garde-fou** : le cumul des échéances ne peut **jamais dépasser le prix de vente**
  (**422 `VIOLATION_LEGALE`**). Chaque échéance porte sa base légale.

## 5. La livraison et les réserves

- **Enregistrer la livraison** (depuis `ACTE`) :
  - **Sans réserve** → `LIVRE_DEFINITIF` (vente finalisée, contact → client finalisé).
  - **Avec réserves** → `LIVRE_AVEC_RESERVES` ; chaque réserve a une **échéance de levée de 60 jours**
    (pratique contractuelle Maroc).
- **Lever les réserves** une à une. Quand la **dernière** est levée → `RESERVES_LEVEES`, puis passage
  manuel possible en `LIVRE_DEFINITIF`.

## 6. TVA applicable (Code Général des Impôts)

Sur la fiche commerciale du bien : prix **HT** + **taux TVA** → **prix TTC calculé** (jamais stocké).

| Cas | Taux |
|---|---|
| Logement social (≤ 100 m², ≤ 250 000 MAD, primo-accédant) | 0 % |
| Logement moyen (≤ 150 m², ≤ 700 000 MAD) | 10 % |
| Tous autres cas | 20 % |

Le taux peut être **suggéré automatiquement** depuis la surface et le prix.

## 7. Acquéreurs, co-acquéreurs, financement

- **Profil légal** du contact (fiche contact → *Profil légal VEFA*) : CIN/passeport, naissance,
  situation matrimoniale, **type d'acquéreur** (Résident / **MRE** / Étranger — réglementation Office
  des Changes), apport personnel.
- **Co-acquéreur** (fiche vente) : un co-acquéreur par vente (conjoint, co-investisseur, SCI…).
- **Dossier de financement** (fiche vente) : suivi `EN_COURS → ACCORD_PRINCIPE → ACCORD_DEFINITIF / REFUSE`,
  banque, montant, taux, durée, **date d'expiration de l'accord**.

## 8. Documents légaux

Génération PDF avec la mention obligatoire « Conformément à la Loi n°44-00 » :
**contrat de réservation** et **PV de livraison** (liste des réserves). Stockés sur la vente et
accessibles à l'acquéreur via son portail.

## 9. Le tableau de bord trésorerie (managers)

`Trésorerie` (menu) : **encaissé / à encaisser / prévisionnel 6 mois / en retard**, plus les
**alertes** — options actives, rétractations en cours, **accords bancaires expirant sous 15 j**, et la
**liste des appels de fonds en retard** (avec ancienneté). Réservé ADMIN/MANAGER.

## 10. Garde-fous légaux — récapitulatif

| Règle | Base | Comportement |
|---|---|---|
| 1 engagement actif / bien | RG-B03 | 409 `PROPERTY_ALREADY_ENGAGED` |
| Dépôt ≤ 5 % | Art. 618-4 | 422 `VIOLATION_LEGALE` |
| Rétractation 7 j | Art. 618-3 | 409 `RETRACTATION_IMPOSSIBLE` hors délai |
| Cumul appels ≤ prix | Art. 618-17 | 422 `VIOLATION_LEGALE` |
| Transitions de statut | machine d'états | 409 `INVALID_STATUS_TRANSITION` |
