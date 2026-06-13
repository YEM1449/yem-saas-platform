# PLAN D'ACTION CONSOLIDÉ — YEM HLM 2026-06-13

Source : `docs/audit/audit-report-2026-06-13.md` (consolidation audit 2026-06-03 + revue produit 2026-06-12 + re-vérification code source 2026-06-13).

Effort : XS=<2h · S=<1j · M=<3j · L=<1sem.

**22 items ouverts au 2026-06-13** — tous les 🔴 Critiques et 🟠 Majeurs sont résolus.

---

## PHASE A — FONCTIONNEL BLOQUANT (livrer avant premier client payant) ✅ RÉSOLU 2026-06-13

| ID | Finding | Action | Fichiers | Effort | Changeset |
|----|---------|--------|----------|--------|-----------|
| A-001 ✅ | **#030** Échéances non annulées sur ANNULE | `EcheanceStatut.ANNULEE` ajouté ; `cancelAllPendingByVente()` `@Modifying` dans le repo ; appelé dans `updateStatut(ANNULE)` et `exerciseRetractation()` ; toutes les requêtes trésorerie/receivables excluent `ANNULEE` (`NOT IN ('PAYEE','ANNULEE')`) | `EcheanceStatut.java`, `VenteEcheanceRepository.java`, `VenteService.java` | S | — |
| A-002 ✅ | **#006** Export CSV/PDF sans TVA | CSV : 3 colonnes ajoutées (Prix HT, Taux TVA, Prix TTC) via `TvaCalculator` ; PDF : colonnes + sous-total par taux (`subtotalByTaux`) ; `PropertyRepository` injecté dans `ReportExportService`, batch-load par `findAllById` | `ReportExportService.java`, `ventes-report.html` | S | — |
| A-003 ✅ | **#007** Réserves livraison : vue projet + responsable | Changeset **087** : `responsable_user_id UUID` nullable sur `reserve_livraison` (FK → `app_user`, ON DELETE SET NULL) ; `ReserveLivraison.responsableUserId` ; `ReserveLivraisonRepository.findByProjectId()` (native) ; `ReserveLivraisonProjectController` `GET /api/projects/{id}/reserves` (ADMIN/MANAGER) ; `ReserveLivraisonProjectResponse` DTO | `087-reserve-livraison-responsable.yaml`, `ReserveLivraison.java`, `ReserveLivraisonRepository.java`, `ReserveLivraisonProjectController.java`, `ReserveLivraisonProjectResponse.java`, `ReserveLivraisonResponse.java` | M | **087** |
| A-004 ✅ | **#019** Prix silencieux → 422 | Guard ajouté aux deux branches de `create()` : `prixVente != null && ≤ 0` → `PrixVenteInvalideException` 422 `PRIX_VENTE_INVALIDE` | `VenteService.java`, `PrixVenteInvalideException.java`, `ErrorCode.java`, `GlobalExceptionHandler.java` | XS | — |

---

## PHASE B — LÉGAL & CONFORMITÉ ✅ RÉSOLU 2026-06-13

| ID | Finding | Action | Fichiers | Effort |
|----|---------|--------|----------|--------|
| B-001 ✅ | **#029** Pénalité retard non calculée | `MarketConfig.getPenaliteRetardJournalierMad()` (défaut 500 MAD/j) ; `joursRetard`+`penaliteAccumulee` dans `VenteService.toResponse()` ; `countVentesEnRetardLivraison`+`sumRetardJoursLivraison` dans `VenteRepository` ; `ventesEnRetardLivraison`+`penaliteRetardTotale` dans `TresorerieDashboardDTO`+Service ; section `.penalite` conditionnelle dans PV livraison | `MarketConfig.java`, `VenteResponse.java`, `VenteService.java`, `VenteRepository.java`, `TresorerieDashboardDTO.java`, `TresorerieDashboardService.java`, `pv-livraison-vefa.html` | M |
| B-002 ✅ | **#027** Politique de rétention | Matrice 3 tiers : prospect 2 ans / acquéreur 5 ans / VEFA 10 ans ; `findRetentionCandidatesByStatuses()` dans `ContactRepository` ; `DataRetentionScheduler` réécrit 3 passes + constantes nommées ; `docs/legal/data-retention.md` | `ContactRepository.java`, `DataRetentionScheduler.java`, `docs/legal/data-retention.md` | M |
| B-003 ✅ | **#031** Relecture notaire PDFs | `docs/legal/pdf-review-checklist.md` (27 items — Art.618-3/618-13/618-17, mentions obligatoires, actions correctives identifiées) | `docs/legal/pdf-review-checklist.md` | S |
| B-004 ✅ | **#008** Read-audit sur GETs sensibles | `AuditEventType.SENSITIVE_DATA_READ` ; `SensitiveDataReadEvent` ; `@ReadAudit` annotation + `ReadAuditAspect` (`@Around`, SocieteContext static, erreur silencieuse) ; `onSensitiveDataRead()` dans `AuditEventListener` (REQUIRES_NEW) ; annoté sur `getLegalDetails()` et `getCommercial()` | `AuditEventType.java`, `SensitiveDataReadEvent.java`, `ReadAudit.java`, `ReadAuditAspect.java`, `AuditEventListener.java`, `ContactController.java`, `PropertyController.java` | M |
| B-005 ✅ | **#026** CNDP déclaration | `Societe.numeroCndp` existait — pas de changeset ; `UpdateComplianceRequest`, `ComplianceInfoResponse`, `ComplianceController` `GET/PATCH /api/mon-espace/compliance` (ADMIN self-service) | `UpdateComplianceRequest.java`, `ComplianceInfoResponse.java`, `ComplianceController.java` | S |

---

## PHASE C — UX & NAVIGATION ✅ RÉSOLU 2026-06-13

| ID | Finding | Action | Fichiers | Effort |
|----|---------|--------|----------|--------|
| C-001 ✅ | **#024** Leaderboard incomplet | Séparateur visuel après rang 3 + classe `rank-low` pour rang 4+ (muted) ; `rank-separator` CSS | `home-dashboard.component.html`, `home-dashboard.component.css` | XS |
| C-002 ✅ | **#021** Draco : hint manquant | Hint text + lien `docs/guides/user/3d-visualiseur.md` sous "Draco obligatoire" | `model-upload-admin.component.html`, `.scss` | XS |
| C-003 ✅ | **#017** Nav misgrouping | Section "Communication" créée ; Messages déplacé (ADMIN/MANAGER only via `@if (isAdminOrManager)`) ; Notifications tous rôles | `shell.component.html` | S |
| C-004 ✅ | **#022** 3D touch/tap | `ThreeEngineService.tap$` Subject + `touchstart` listener ; `pinnedMapping`/`pinnedStatus` signals dans le viewer ; bouton × dans `LotTooltip3dComponent` (pinned mode) ; suppression du click synthétique mobile (`lastTouchAt` guard) | `three-engine.service.ts`, `project-viewer-3d.component.ts/.html`, `lot-tooltip-3d.component.ts/.html/.scss` | S |
| C-005 ✅ | **#020** Currency input masqué | `MadInputComponent` standalone (`ControlValueAccessor`, `type="text"`, `inputmode="numeric"`, format fr-MA sur blur) ; remplacé sur `prixVente` (vente-create), `editForm.price` + `commercialForm.prixHt` (property-detail) | `core/components/mad-input.component.ts`, `vente-create.component.ts/.html`, `property-detail.component.ts/.html` | S |
| C-006 ✅ | **#009** Digest d'alertes cross-société | `NotificationDigestScheduler` (cron `app.digest.cron` 07:30 lun–ven) ; pour chaque société active : ADMIN/MANAGER récupérés via `AppUserSocieteRepository`, comptes via `echeanceRepository.countOverdue` + `venteRepository.count*` ; email HTML inline via `EmailSender` ; skip si 0 alertes | `NotificationDigestScheduler.java`, `application.yml` | M |

---

## PHASE D — QUICK WINS (XS, enchaînables en une session)

| ID | Finding | Action | Fichiers | Effort |
|----|---------|--------|----------|--------|
| D-001 | **#033** Javadoc stale | Mettre à jour `VenteService.java:53` (machine VEFA Wave 12) ; supprimer les commentaires `ACTE_NOTARIE` dans `ShareholderKpiDTO`, `CommercialDashboardSummaryDTO`, `HomeDashboardDTO` (7 fichiers) | 4 fichiers Java | XS |
| D-002 | **#012** Couleurs CSS hardcodées | Remplacer le gradient `indigo` `home-dashboard.component.css:326-329` par `var(--c-primary-*)` ; `stroke="#16a34a"` SVG → `currentColor` | `home-dashboard.component.css`, `shell.component.html` | XS |
| D-003 | **#018** Routes bilingues | Normaliser en français : `/app/properties` → `/app/biens` (redirect alias) + `/app/projects` → `/app/projets` (redirect alias) | `app.routes.ts` | XS |
| D-004 | **#034** Guide manager : passe plain-French | Remplacer `option_expire_at` → *"l'heure d'expiration"*, `VenteService.validateTransition` → *"la plateforme refuse cette transition"*, `scheduler horaire` → *"vérification automatique toutes les heures"*, `MARKET_CODE` → *"configuration marché"* ; sweep "MAD" + "DD/MM/YYYY" | `docs/legal/guide-loi-44-00-managers.md` | XS |
| D-005 | **#011** i18n : décision + sweep | Décider FR-only ou i18n complet ; si FR-only : supprimer les `| translate` et simplifier la config ; si i18n : balayer les 14 templates (78 occurrences hardcodées détectées dont home-dashboard) | `i18n/` + features/*.html | M |

---

## PHASE E — CODE QUALITY (backlog technique)

| ID | Finding | Action | Fichiers | Effort |
|----|---------|--------|----------|--------|
| E-001 | **#032** CommonModule × 89 | Script : `grep -rl CommonModule src/app | xargs sed -i 's/CommonModule, //g'` + lint rule `no-restricted-imports CommonModule` (vérifier case par case pour `AsyncPipe`/`DatePipe` encore nécessaires) | 89 fichiers `.ts` | S |
| E-002 | **#013** 236 inline styles | Convertir en classes utilitaires (commencer par vente-detail.html 25 occurrences, property-detail.html 21, home-dashboard.html 21) ; ajouter ESLint `no-inline-style` à `angular-eslint` config | `styles.css`, templates prioritaires | M |
| E-003 | **#014** home-dashboard CSS oversized | Extraire composants enfants (`KpiCardComponent`, `ShortcutGridComponent`, `VentesRecentesComponent`) ; restaurer le budget `anyComponentStyle` à 16 kB | `home-dashboard.component.*` | S |
| E-004 | **F-005** Couverture services | Ajouter tests par vagues : `VenteEcheanceService`, `ReservationService`, `ProjectGenerationService`, `TrancheService`, `DossierFinancementService` — au minimum les chemins d'erreur | `src/test/java/.../service/` | L |
| E-005 | **F-011** Couverture frontend | Ajouter specs : `VenteService`, `PropertyService`, `AuthService` (fonctions pures d'abord) ; passer de 4 à ≥15 specs | `src/app/**/*.spec.ts` | L |

---

## PHASE F — DIFFÉRÉ (justifié)

| ID | Finding | Raison | Réouvrir quand |
|----|---------|--------|----------------|
| F-001 | **F-012** Soft-delete | Décision de conception non tranchée ; suppressions physiques actuelles sont correctes | Avant GDPR audit ou demande client |
| F-002 | **F-015** Pipeline CD | Nécessite secrets de déploiement réels (Render/Cloudflare) | Accès secrets DevOps |
| F-003 | **CNDP org** (#026) | Acte humain uniquement — ne peut être automatisé | Après dépôt déclaration opérateur |

---

## SUIVI D'AVANCEMENT

| Phase | Items | Résolus | Restants |
|-------|-------|---------|---------|
| A — Fonctionnel bloquant | 4 | **4** | **0** |
| B — Légal/Conformité | 5 | **5** | **0** |
| C — UX/Navigation | 6 | **6** | **0** |
| D — Quick wins XS | 5 | 0 | **5** |
| E — Code quality | 5 | 0 | **5** |
| F — Différé | 3 | — | 3 |

**Total ouvert actionnable : 10 items** (D+E) — dont **5 à effort XS** débloquables en 1 session.

---

*Rapport de référence : `docs/audit/audit-report-2026-06-13.md`.*
*Anciens plans supersédés (conserver pour historique) : `docs/audit/action-plan-2026-06-03.md`, `docs/review/team-review-2026-06-12.md`.*
