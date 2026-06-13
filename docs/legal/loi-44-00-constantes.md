# Loi 44-00 — Constantes légales VEFA Maroc
# Dahir n°1-02-298 du 25 rejeb 1423 (3 octobre 2002)
# Complétant le Dahir des Obligations et Contrats (DOC) — Articles 618-1 à 618-21
#
# RÉFÉRENCE IMMUABLE — ne pas modifier sans justification légale documentée.
# Source de vérité des plafonds/délais. La logique métier lit ces valeurs via
# MarketConfig (jamais de hardcode dispersé).

## Délai de rétractation
DELAI_RETRACTATION_JOURS = 7
Base légale : Art. 618-3 — L'acquéreur dispose de 7 jours à compter de la
signature du contrat préliminaire pour se rétracter.
Effet : remboursement intégral du dépôt de garantie sous 30 jours.
**État codebase :** le champ existe déjà — `vente.date_fin_delai_reflexion`
(renommé en changeset 073 depuis `date_fin_delai_sru`), période issue de
`@Value("${app.vente.default-reflection-period-days:10}")` dans `VenteService`.

## Dépôt de garantie maximum
DEPOT_GARANTIE_MAX_PCT = 5
Base légale : Art. 618-4 — Le montant versé à la signature ne peut excéder 5 %
du prix de vente convenu.

## Échéancier légal des appels de fonds (Art. 618-17)
ECHEANCIER_LEGAL = [
  { etape: "SIGNATURE_CONTRAT",        pct_max: 5  },
  { etape: "ACHEVEMENT_FONDATIONS",    pct_max: 10 },
  { etape: "ACHEVEMENT_PLANCHER_RDC",  pct_max: 15 },
  { etape: "ACHEVEMENT_GROS_OEUVRE",   pct_max: 20 },
  { etape: "ACHEVEMENT_COUVERTURE",    pct_max: 20 },
  { etape: "ACHEVEMENT_SECOND_OEUVRE", pct_max: 20 },
  { etape: "LIVRAISON_TITRE_PROPRIETE",pct_max: 10 }
]
TOTAL = 100 %. Aucun appel ne dépasse son plafond ; le cumul ne dépasse jamais
l'avancement réel des travaux.
**État codebase :** un système d'appels de fonds existe déjà — module `payments`
(`payment_schedule` ch. 020, `payment_call` ch. 021, `CallForFundsWorkflowService`,
`CallForFundsPdfService`, `PaymentScheduleService`). À ÉTENDRE avec ces plafonds
légaux plutôt que dupliquer.

## TVA applicable (Code Général des Impôts Maroc)
TVA_LOGEMENT_SOCIAL = 0 %   (surface ≤ 100 m², prix ≤ 250 000 MAD, résidence
                             principale, primo-accédant)
TVA_LOGEMENT_MOYEN  = 10 %  (surface ≤ 150 m², prix ≤ 700 000 MAD)
TVA_TAUX_NORMAL     = 20 %  (tous autres cas)

## Garantie Financière d'Achèvement (GFA)
REQUIRED = true
Base légale : Art. 618-6 — Le promoteur doit justifier soit d'une garantie
d'achèvement, soit d'une garantie de remboursement.

## Délai de réponse aux réserves de livraison
DELAI_LEVEE_RESERVES_JOURS = 60
Base : pratique contractuelle — les réserves doivent être levées dans les 60
jours suivant la livraison, sauf accord contraire.

## Types d'acquéreurs (réglementation Office des Changes Maroc)
RESIDENT_MAROC = "Résident marocain (CIN)"
MRE            = "Marocain Résidant à l'Étranger (CIN + document séjour)"
ETRANGER       = "Étranger non-résident (Passeport)"
Chaque type a des règles de financement et de transfert de fonds différentes.
**État codebase :** `contact.national_id` existe déjà (= CIN).

## Internationalisation future
MARKET_CONFIG = {
  "MA":      { law: "Loi 44-00",          delai_retractation: 7,  depot_max_pct: 5 },
  "FR":      { law: "Loi SRU / CCH L261-1",delai_retractation: 10, depot_max_pct: 5 },
  "DEFAULT": { law: "custom",             delai_retractation: 7,  depot_max_pct: 5 }
}
Toutes les constantes légales sont lues depuis un `MarketConfig` (clé d'env
`MARKET_CODE`), jamais hardcodées dans la logique métier.
