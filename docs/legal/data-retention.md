# Politique de rétention des données personnelles

**Références légales :** Loi n°09-08 relative à la protection des personnes physiques à l'égard du traitement des données à caractère personnel (Maroc) ; Loi n°44-00 relative à la vente en l'état futur d'achèvement (Art. 618-17) ; prescription commerciale de droit commun marocain.

**Responsable :** DPO de chaque société enregistrée dans la plateforme HLM.

---

## Matrice de rétention

| Catégorie de contact | Statuts couverts | Durée de conservation | Base légale |
|---|---|---|---|
| **Prospects** | `PROSPECT`, `QUALIFIED_PROSPECT` | **2 ans** après suppression | Loi 09-08 Art. 4 — prospection commerciale |
| **Acquéreurs simples** | `CLIENT`, `ACTIVE_CLIENT`, `REFERRAL`, `LOST` | **5 ans** après suppression | Prescription commerciale (Art. 5 Code de commerce marocain) |
| **Acquéreurs VEFA** | `COMPLETED_CLIENT` | **10 ans** après suppression | Loi 44-00 Art. 618-17 — obligation d'archivage acte VEFA |

> **Règle générale :** le délai court à partir de la date de suppression logique (`deleted = true`) du contact, pas à partir de la date de fin de contrat.

---

## Implémentation technique

Le `DataRetentionScheduler` exécute trois passes quotidiennes à 02 h 00 (configurable via `app.gdpr.retention-cron`) :

1. **Passe prospects (2 ans)** — `findRetentionCandidatesByStatuses(societeId, now - 730j, [PROSPECT, QUALIFIED_PROSPECT])`
2. **Passe acquéreurs (5 ans)** — `findRetentionCandidatesByStatuses(societeId, now - 1825j, [CLIENT, ACTIVE_CLIENT, REFERRAL, LOST])`
3. **Passe VEFA (10 ans)** — `findRetentionCandidatesByStatuses(societeId, now - 3650j, [COMPLETED_CLIENT])`

Chaque contact candidat est passé à `AnonymizationService.anonymize()`. Si un contrat SIGNÉ bloque l'effacement (`GdprErasureBlockedException`), le contact est **ignoré** et un WARN est loggué — l'obligation d'archivage contractuel prime sur la rétention GDPR.

Constantes nommées dans le code (`DataRetentionScheduler.java`) :
```java
RETENTION_PROSPECT_DAYS  = 730
RETENTION_ACQUEREUR_DAYS = 1825
RETENTION_VEFA_DAYS      = 3650
```

---

## Ce qui est anonymisé

`AnonymizationService.anonymize()` efface les champs identifiants et positionne `anonymizedAt = now()`. Les données transactionnelles (ventes, échéanciers, audits) sont **conservées** sous forme anonymisée pour les obligations comptables et légales.

---

## Déclaration CNDP

Chaque société doit déposer une déclaration auprès de la **Commission Nationale de contrôle de la Protection des Données à caractère personnel (CNDP)** avant tout traitement de données personnelles. Le numéro de récépissé est enregistré dans le champ `Societe.numeroCndp` via l'endpoint `PATCH /api/mon-espace/compliance`.

Voir `docs/legal/cndp-declaration.md` pour le détail de la procédure.

---

## Révision

Cette politique doit être révisée à chaque modification des textes de référence ou à la demande du DPO. Dernière révision : **2026-06-13**.
