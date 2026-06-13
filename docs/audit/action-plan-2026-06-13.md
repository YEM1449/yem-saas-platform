# PLAN D'ACTION CONSOLIDÉ — YEM HLM 2026-06-13

Source : `docs/audit/audit-report-2026-06-13.md` (consolidation audit 2026-06-03 + revue produit 2026-06-12 + re-vérification code source 2026-06-13).

Effort : XS=<2h · S=<1j · M=<3j · L=<1sem.

**22 items ouverts au 2026-06-13** — tous les 🔴 Critiques et 🟠 Majeurs sont résolus.

---

## PHASE A — FONCTIONNEL BLOQUANT (livrer avant premier client payant)

| ID | Finding | Action | Fichiers | Effort | Changeset |
|----|---------|--------|----------|--------|-----------|
| A-001 | **#030** Échéances non annulées sur ANNULE | Dans `VenteService.updateStatut()` branche `ANNULE` : appeler `echeanceRepository.cancelAllPendingByVente(venteId)` (nouveau statut `ANNULEE`) ; exclure `ANNULEE` de toutes les requêtes trésorerie/receivables ; ajouter test | `VenteService.java`, `VenteEcheance.java`, `VenteEcheanceRepository.java`, `TresorerieDashboardService.java` | S | — |
| A-002 | **#006** Export CSV/PDF sans TVA | Ajouter colonnes `prix_ht`, `taux_tva`, `prix_ttc` (via `TvaCalculator`) dans l'export CSV ventes + sous-total par taux dans la section PDF ; ajouter colonne dans `VenteResponse` export shape | `VenteExportService.java` (ou équivalent), `TvaCalculator.java` | S | — |
| A-003 | **#007** Réserves livraison : vue projet + responsable | `GET /api/projects/{id}/reserves` (ADMIN/MANAGER, société-scopé) + ajouter `responsable_user_id UUID` nullable sur `reserve_livraison` (nouveau changeset) ; nouveau `ReserveLivraisonProjectController` | `reserve_livraison` table, `ReserveLivraison.java`, nouveau controller | M | **087** |
| A-004 | **#019** Prix silencieux → 422 | Dans `VenteService.create()` lignes 157/190 : si `request.prixVente() != null && request.prixVente() ≤ 0` → lever `ValidationException` 422 (`PRIX_VENTE_INVALIDE`) au lieu de tomber sur le fallback catalogue | `VenteService.java:157–200`, `ErrorCode.java` | XS | — |

---

## PHASE B — LÉGAL & CONFORMITÉ (avant tout usage commercial réel)

| ID | Finding | Action | Fichiers | Effort |
|----|---------|--------|----------|--------|
| B-001 | **#029** Pénalité retard non calculée | Calculer `joursRetard = today - tranche.dateLivraisonPrevue` dans `VenteService` / `TresorerieDashboardService` ; exposer `penaliteAccumulee = joursRetard × penaliteRetardJournalier` dans `VenteResponse` et `TresorerieDashboardDTO` ; alerte dans le dashboard et mention dans le PV livraison | `VenteService.java`, `TresorerieDashboardService.java`, `VenteResponse.java`, PV template | M |
| B-002 | **#027** Politique de rétention | Définir la matrice (prospect 2 ans, acquéreur 5 ans, vente VEFA 10 ans conformément Loi 44-00) ; documenter dans `docs/legal/data-retention.md` ; câbler les durées dans `DataRetentionScheduler` comme constantes nommées | `DataRetentionScheduler.java`, nouveau `docs/legal/data-retention.md` | M |
| B-003 | **#031** Relecture notaire PDFs | Préparer la checklist des mentions obligatoires Loi 44-00 Art.618-1 sq. dans `docs/legal/pdf-review-checklist.md` ; livrer à un notaire ; appliquer les corrections sur les templates Thymeleaf | Templates PDF, `docs/legal/pdf-review-checklist.md` | S + suivis |
| B-004 | **#008** Read-audit sur GETs sensibles | Annoter `ContactController.getLegal()` et `PropertyController.getCommercial()` (et équivalents portail) avec un aspect `@ReadAudit` qui publie un `AuditEvent(READ, entityId, userId)` asynchrone (sampled 100%) | `CommercialAuditController.java`, nouveau `@ReadAudit` aspect | M |
| B-005 | **#026** CNDP déclaration | *(Acte organisationnel)* Déposer la déclaration auprès de la CNDP pour chaque société ; saisir le numéro de récépissé dans le champ `Societe.numeroCndp` (endpoint `/api/admin/societes/{id}/compliance`) → apparaît automatiquement sur les pages légales portail | — | S (org) |

---

## PHASE C — UX & NAVIGATION (qualité démo / first impressions)

| ID | Finding | Action | Fichiers | Effort |
|----|---------|--------|----------|--------|
| C-001 | **#024** Leaderboard incomplet | Ajouter tableau complet trié après le podium, ou strip "bottom-3 / agents sans vente depuis 14j" | `home-dashboard.component.html/.ts` | XS |
| C-002 | **#021** Draco : hint manquant | Ajouter une ligne d'aide sous le label "Draco obligatoire" : *"Exportez depuis Blender avec compression Draco activée — voir [guide 3D]"* + lien `docs/guides/user/3d.md` | `model-upload-admin.component.html` | XS |
| C-003 | **#017** Nav misgrouping | Déplacer "Messages" et "Notifications" vers une section "Communication" dans la sidebar shell ; consolider les ~16 items en ≤12 avec role-based pruning (AGENT ne voit pas Messages/Outbox) | `shell.component.html`, `shell.component.ts` | S |
| C-004 | **#022** 3D touch/tap | Ajouter `(pointerdown)` / `(touchstart)` sur les meshes lot pour épingler le tooltip ; test sur iOS Safari | `project-viewer-3d.component.ts`, `lot-tooltip-3d.component.ts/.css` | S |
| C-005 | **#020** Currency input masqué | Créer `MadInputComponent` (standalone, `type="text"`, `inputmode="numeric"`, formatage live `1 250 000 MAD`) ; remplacer les `type="number"` sur les champs prix vente, bien, commercial | Nouveau `shared/mad-input.component.ts`, vente-create, property-form, commercial-form | S |
| C-006 | **#009** Digest d'alertes cross-société | Email digest (quotidien/hebdo) par appartenance : items overdue + alertes trésorerie de chaque société admin ; scheduler cron + `EmailSender` | `NotificationScheduler.java` (ou nouveau), template email | M |

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
| A — Fonctionnel bloquant | 4 | 0 | **4** |
| B — Légal/Conformité | 5 | 0 | **5** |
| C — UX/Navigation | 6 | 0 | **6** |
| D — Quick wins XS | 5 | 0 | **5** |
| E — Code quality | 5 | 0 | **5** |
| F — Différé | 3 | — | 3 |

**Total ouvert actionnable : 25 items** (A+B+C+D+E) — dont **11 à effort XS/S** débloquables en 1–2 sessions.

---

*Rapport de référence : `docs/audit/audit-report-2026-06-13.md`.*
*Anciens plans supersédés (conserver pour historique) : `docs/audit/action-plan-2026-06-03.md`, `docs/review/team-review-2026-06-12.md`.*
