# RAPPORT D'AUDIT YEM HLM — 2026-06-13

**Auditeurs sources :** Claude Code Expert Team (Architecte · Sécurité · 3D · BI · UX · DevOps · Tech Writer) + Cross-Functional Product Review Team (Sara · Karim · Leila · Adam · Nadia · Youssef · Mehdi)
**Version plateforme :** Wave 16 (VEFA Loi 44-00 complète) — branche `Epic/Dashboard-UIUX-improvement`
**Changesets Liquibase :** 001–086 (prochain : **087**)
**Suite de tests backend :** 200 unit tests (Surefire) · 623 @Test annotations au total (incl. IT Failsafe)
**Suite de tests frontend :** 4 specs unitaires · 9 specs E2E Playwright (~35 tests)
**Scope :** Consolidation de l'audit technique 2026-06-03 + revue produit cross-fonctionnelle 2026-06-12 + re-vérification code source 2026-06-13.
**Méthode :** Lecture seule ; chaque constat est adossé à un grep/find exécuté.

---

## RÉSUMÉ EXÉCUTIF

**Ce qui est solide (confirmé en re-vérification 2026-06-13).** La plateforme est dans un état de maturité rare pour ce stade. L'isolation multi-société est défense-en-profondeur : `requireSocieteId()` ×280 + `societe_id` sur chaque requête + RLS PostgreSQL phase 2 + `SocieteContextTaskDecorator` propagé aux threads @Async. Le pipeline VEFA Loi 44-00 est le plus légalement sérieux rencontré sur le marché marocain : garde double-vente (RG-B03, 409 + index partiel), dépôt ≤5% Art.618-4, fenêtre de rétractation 7j avec scheduler, échéancier légal Art.618-17 avec cumul ≤ prix, co-acquéreur, dossier financement. Le JWT vit en cookie httpOnly — vecteur XSS-vol-de-token neutralisé. Zéro injection SQL (0 requête concaténée, JPA paramétré intégral). Hygiène WebGL exemplaire (dispose complet, DPR≤1.5, Page Visibility). La Vue Groupe (#001), l'import CSV contacts (#002), le suivi remboursement (#028), la pagination des listes (#023/F-006), les pages légales portail (#025) et le pointeur 3D (#015) ont tous été livrés depuis le premier audit.

**Ce qui reste à corriger avant GA.** Trois zones de risque subsistent :
1. **Behavior indéfini sur annulation** : l'annulation d'une vente (→`ANNULE`) ne cancelle pas explicitement les échéances pending — elles restent en base avec un statut contradictoire et polluent les vues trésorerie. Aucun test ne couvre ce chemin.
2. **Sécurité CNDP en suspens** : la déclaration CNDP est un acte organisationnel non encore effectué ; le portail affiche les données CIN/financières de l'acheteur sans numéro de récépissé prouvé.
3. **Correction silencieuse du prix** : un `prixVente ≤ 0` soumis à l'API est corrigé silencieusement par le prix catalogue plutôt que rejeté 422 — la money input fait semblant d'accepter une valeur qu'elle ignore.

**La dette technique dominante** est cosmétique mais mesurable : 236 styles inline, 89 composants qui importent encore `CommonModule` inutilement, 4 specs unitaires Angular pour un frontend de 175 fichiers TS, et un javadoc `VenteService.java:53` qui décrit encore l'ancienne machine à états pré-Wave 12.

---

## TABLEAU DE BORD UNIFIÉ DES FINDINGS

> Sources : audit technique 2026-06-03 (F-xxx) + revue produit 2026-06-12 (#xxx).
> Les deux numérotations sont conservées pour traçabilité ; les doublons sont fusionnés.

| ID | Sévérité | Source | Module | Description courte | Effort | Statut |
|----|----------|--------|--------|--------------------|--------|--------|
| **F-001** | 🔴 CRITIQUE | Audit | vente | Double-vente RG-B03 : garde + index partiel | S | ✅ RÉSOLU 2026-06-03 |
| **F-002** | 🔴 CRITIQUE | Audit | vente | VenteService sans tests backend | M | ✅ RÉSOLU 2026-06-03 |
| **#001** | 🔴 CRITIQUE | Revue | groupe | Pas de vue groupe (owner multi-société) | L | ✅ RÉSOLU 2026-06-12 |
| **F-003** | 🔵 INFO | Audit | common | 403 vs 404 cross-société | — | ⓘ FAUX POSITIF |
| **F-004** | 🟠 MAJEUR | Audit | viewer3d | GLB non validé binairement | S | ✅ RÉSOLU 2026-06-03 |
| **#002** | 🟠 MAJEUR | Revue | contacts | Pas d'import CSV/Excel | M | ✅ RÉSOLU 2026-06-12 |
| **#003** | 🟠 MAJEUR | Revue | dashboard | Cash forecast sans courbe mensuelle | S | ✅ RÉSOLU 2026-06-12 |
| **#004** | 🟠 MAJEUR | Revue | users | Pas de désactivation cross-société en 1 action | S | ✅ RÉSOLU 2026-06-12 |
| **#005** | 🟠 MAJEUR | Revue | groupe | Même acheteur = 2 contacts non liés | M | ✅ RÉSOLU 2026-06-12 |
| **#010** | 🟠 MAJEUR | Revue | design | Police Inter jamais chargée | XS | ✅ RÉSOLU 2026-06-12 |
| **#015** | 🟠 MAJEUR | Revue | nav | Viewer 3D orphelin (aucun lien entrant) | XS | ✅ RÉSOLU 2026-06-12 |
| **#016** | 🟠 MAJEUR | Revue | nav | Contrats + Pipeline Ventes : double concept | M | ✅ RÉSOLU 2026-06-12 |
| **#023 = F-006** | 🟠 MAJEUR | Les deux | perf | Endpoints `List<>` non paginés | M | ✅ RÉSOLU 2026-06-12/13 |
| **#025** | 🟠 MAJEUR | Revue | portail | Portail sans politique vie privée ni CNDP | S | ✅ RÉSOLU 2026-06-12 |
| **#028** | 🟠 MAJEUR | Revue | vente | Remboursement dépôt non tracé | M | ✅ RÉSOLU 2026-06-12 |
| **F-007** | 🟡 MINEUR | Audit | frontend | 23 `*ngIf`/`*ngFor` legacy | XS | ✅ RÉSOLU 2026-06-03 |
| **F-010** | 🟡 MINEUR | Audit | viewer3d | Fallback 3D visible par tous | S | ✅ RÉSOLU 2026-06-03 |
| **#030** | 🟡 MINEUR | Revue | vente | **Échéances pending non annulées sur vente ANNULE** | S | 🔲 OUVERT |
| **#006** | 🟡 MINEUR | Revue | export | Export CSV/PDF ventes sans colonnes HT/TVA/TTC | S | 🔲 OUVERT |
| **#007** | 🟡 MINEUR | Revue | vente | Réserves livraison : vue projet + `responsable` absent | M | 🔲 OUVERT |
| **#029** | 🟡 MINEUR | Revue | legal | `penaliteRetardJournalier` stocké, jamais calculé | M | 🔲 OUVERT |
| **#008** | 🟡 MINEUR | Revue | audit | Read-audit absent sur GETs sensibles | M | 🔲 OUVERT |
| **#019** | 🟡 MINEUR | Revue | vente | `prixVente ≤ 0` corrigé silencieusement (→ 422 attendu) | XS | 🔲 OUVERT |
| **#011** | 🟡 MINEUR | Revue | i18n | 14 templates sans traduction (78 occurrences FR hard) | M | 🔲 OUVERT |
| **#017** | 🟡 MINEUR | Revue | nav | Messages + Notifications sous "Analytics" (faux drawer) | S | 🔲 OUVERT |
| **#021** | 🟡 MINEUR | Revue | viewer3d | Uploader Draco : hint inexistant | XS | 🔲 OUVERT |
| **#022** | 🟡 MINEUR | Revue | viewer3d | Tooltips hover-only — tablettes sans info | S | 🔲 OUVERT |
| **#024** | 🟡 MINEUR | Revue | dashboard | Leaderboard top-5 seulement — sous-performants invisibles | XS | 🔲 OUVERT |
| **#027** | 🟡 MINEUR | Revue | legal | Politique de rétention données absente | M | 🔲 OUVERT |
| **#031** | 🟡 MINEUR | Revue | legal | PDFs légaux non relus par notaire | S | 🔲 OUVERT |
| **#009** | 🟡 MINEUR | Revue | notif | Pas de digest d'alertes cross-société | M | 🔲 OUVERT |
| **F-008** | ⓘ INFO | Audit | frontend | Subscriptions — requalifié faux positif | — | ⓘ FAUX POSITIF |
| **#033** | 🔵 POLISH | Revue | code | Javadoc VenteService + DTOs : refs ACTE_NOTARIE pré-W12 | XS | 🔲 OUVERT |
| **#032** | 🔵 POLISH | Revue | code | 89 composants importent CommonModule inutilement | S | 🔲 OUVERT |
| **#013** | 🔵 POLISH | Revue | frontend | 236 `style=""` inline (vs 232 au premier audit — en croissance) | M | 🔲 OUVERT |
| **#014** | 🔵 POLISH | Revue | frontend | `home-dashboard.component.css` = 1656 lignes, budget relevé | S | 🔲 OUVERT |
| **#018** | 🔵 POLISH | Revue | nav | Routes bilingues (`/app/projects` vs `/app/projets`) | XS | 🔲 OUVERT |
| **#020** | 🔵 POLISH | Revue | forms | Money input non masqué (pas de groupement MAD) | S | 🔲 OUVERT |
| **#012** | 🔵 POLISH | Revue | design | Couleurs CSS hardcodées (indigo hero, stroke SVG) | XS | 🔲 OUVERT |
| **#034** | 🔵 POLISH | Revue | docs | Guide manager : identifiants techniques non traduits | XS | 🔲 OUVERT |
| **F-005** | 🟡 MINEUR | Audit | tests | Couverture services backend (en cours) | L | ⏳ EN COURS |
| **F-011** | 🟡 MINEUR | Audit | tests | Couverture frontend (4 specs / 175 fichiers TS) | L | ⏳ EN COURS |
| **F-026** | ⓘ INFO | Revue | legal | CNDP déclaration — acte organisationnel pendant | — | ⏳ ORG PENDING |
| **F-009** | 🔵 POLISH | Audit | frontend | Inline styles (fusionné avec #013) | M | ↗ voir #013 |
| **F-012** | 🔵 INFO | Audit | base | Soft-delete : décision de conception non prise | — | ⏳ DIFFÉRÉ |
| **F-015** | 🔵 INFO | Audit | CI/CD | Pipeline CD — secrets de déploiement nécessaires | S | ⏳ DIFFÉRÉ |

**Totaux :** 23 résolus · 22 ouverts (dont 4 🟡 fonctionnel-critique, 8 🟡 legal/UX, 10 🔵 polish) · 3 faux positifs · 3 différés · 3 en cours/org.

---

## SECTIONS DÉTAILLÉES

### 1 · ISOLATION MULTI-SOCIÉTÉ & SÉCURITÉ

**Statut : ✅ Conforme — défense-en-profondeur confirmée**

Vérifications 2026-06-13 :
- `requireSocieteId()` ×280 (inchangé depuis le premier audit)
- RLS PostgreSQL phase 2 sur toutes les tables domaine (changesets 050/051)
- ThreadLocal (`SocieteContextTaskDecorator`) propagé aux threads @Async
- JWT httpOnly cookie — zéro token en localStorage
- Cross-société → 404 via `*NotFoundException`
- Invitation rate-limit (10/h/admin), login lockout (changeset 027)

**OWASP résumé :**

| Vecteur | Statut | Note 2026-06-13 |
|---------|--------|-----------------|
| A01 Broken Access Control | ✅ | requireSocieteId ×280 + RLS ; cross-société → 404 |
| A02 Cryptographic Failures | ✅ | JWT cookie httpOnly, BCrypt seed conforme |
| A03 Injection | ✅ | 0 requête concaténée ; JPA paramétré |
| A04 Insecure Design | ⚠️ | #030 (échéances orphelines) ; #019 (prix silencieux) |
| A05 Security Misconfiguration | ✅ | CorsConfig restrictif, 0 secret en dur |
| A07 Auth Failures | ✅ | Lockout + rate-limit login/invitation/portal |
| A08 Data Integrity | ⚠️ | #019 : soumission prixVente ≤ 0 silencieusement remplacée |

**Gap confirmé (#008) :** `GET /api/contacts/{id}/legal` et `GET /api/properties/{id}/commercial` ne déclenchent aucun audit event — accès à des données sensibles (CIN, tarification) non tracé.

---

### 2 · PIPELINE VENTE VEFA (Loi 44-00)

**Statut : ✅ Conforme sur les gardes légales ; ⚠️ Gap comportemental #030**

Machine à états Wave 12 : `PROSPECT→OPTION→RESERVE→EN_RETRACTATION→ACOMPTE→COMPROMIS→FINANCEMENT→ACTE→LIVRE_AVEC_RESERVES→RESERVES_LEVEES→LIVRE_DEFINITIF`, terminal `ANNULE`.

**Gardes vérifiées actives :**
- Dépôt > 5% → 422 `VIOLATION_LEGALE` (Art. 618-4)
- Rétractation hors fenêtre → 409 `RETRACTATION_IMPOSSIBLE`
- Double-vente → 409 `PROPERTY_ALREADY_ENGAGED` + index unique partiel (ch. 075)
- Cumul échéances > prix → 422 (Art. 618-17)
- Transition invalide → 409 `INVALID_STATUS_TRANSITION`
- Remboursement auto-créé (`DU`) sur `VenteAnnuleeEvent` via `@EventListener` ✅

**Gap #030 (confirmé par grep 2026-06-13) :** `VenteService.updateStatut()` → branche `ANNULE` publie `VenteAnnuleeEvent` mais **ne modifie pas le statut des `VenteEcheance` pending**. Elles restent `EN_ATTENTE` en base, apparaissent dans les vues trésorerie comme dues, et aucun test ne couvre ce chemin. Behavior indéfini par omission.

**Gap #019 (confirmé) :** `request.prixVente() ≤ 0` → code tombe sur le `else if (property.getPrice() > 0)` sans lancer de validation explicite. L'appelant croit avoir imposé son prix ; la plateforme utilise silencieusement le prix catalogue.

**Gap #029 (confirmé) :** `penaliteRetardJournalier` présent dans l'entité `Property` (ch. 083) et renvoyé dans les DTOs, mais aucune computation : jamais comparé à `dateLivraisonPrevue`, jamais affiché dans un document, jamais alerté.

---

### 3 · RÉSERVES DE LIVRAISON

**Statut : ⚠️ Gap fonctionnel #007**

`ReserveLivraison` existe (ch. 078) avec endpoint `GET /api/ventes/{id}/reserves`. Confirmé absent : vue projet agrégée (`GET /api/projects/{id}/reserves`), champ `responsable_user_id` sur la table. Un directeur de chantier qui veut lister toutes les réserves ouvertes d'un projet doit les reconstituer vente par vente.

---

### 4 · EXPORT CSV/PDF

**Statut : ⚠️ Gap fonctionnel #006**

Les colonnes `prix_ht`, `tva_taux` (ch. 083) sont stockées et exposées dans l'API mais absentes de l'export CSV/PDF des ventes. Le calcul TTC via `TvaCalculator` existe mais n'est pas appelé à l'export. Un directeur financier qui télécharge la liste pour sa compta TVA obtient un fichier sans les colonnes réglementaires.

---

### 5 · TABLEAU DE BORD & REPORTING

**Statut : ✅ Bon — un gap #024**

KPIs cohérents (absorption unifiée FE/BE = `SOLD / (ACTIVE + RESERVED + SOLD)`). Courbe mensuelle 6 mois livée (#003). Budget query documenté (16 requêtes). Caches Redis + Caffeine.

**Gap #024 (confirmé) :** Leaderboard = podium top-5 uniquement. La question quotidienne du manager ("qui est en sous-performance ?") est sans réponse. Correction XS : table complète triée derrière le podium ou strip "bottom-3 / sans vente 14j".

---

### 6 · VIEWER 3D

**Statut : ✅ Conforme (technique) ; ⚠️ UX gaps #021 #022**

Hygiène WebGL confirmée (dispose complet, DPR≤1.5, Page Visibility). Validation binaire GLB active (GlbValidator). Points d'entrée dans l'UI câblés depuis la revue (#015 résolu).

- **#021 :** Le label "Draco obligatoire · Max 50 Mo" est affiché sans explication. Un admin non-technique ne sait pas que Draco est un algorithme de compression ni comment l'activer dans Blender.
- **#022 :** Les tooltips lot-3d sont `mouseover` uniquement — sur tablette (iPad) en démo client, la couche informative disparaît entièrement.

---

### 7 · NAVIGATION & IA

**Statut : ⚠️ Gaps #017 #018 restants**

Legacy contracts masqués par feature flag (#016 résolu). Viewer 3D accessible depuis le projet et le dashboard (#015 résolu).

- **#017 :** Messages (outbox admin) et Notifications restent sous la section "Analytics" — mauvais tiroir. ~16 items plats dans la sidebar.
- **#018 (confirmé 2026-06-13) :** routes bilingues coexistantes : `/app/projects`, `/app/properties` (anglais) vs `/app/ventes`, `/app/projets/:id/viewer-3d`, `/app/immeubles` (français).

---

### 8 · QUALITÉ CODE

**Statut : ⚠️ Dette technique mesurable — non bloquante**

Vérifications 2026-06-13 :
- **#033 (confirmé) :** `VenteService.java:53` javadoc décrit encore `COMPROMIS → FINANCEMENT → ACTE_NOTARIE → LIVRE` (pré-Wave 12). `ShareholderKpiDTO`, `CommercialDashboardSummaryDTO`, `HomeDashboardDTO` ont des commentaires `ACTE_NOTARIE` dans 7 fichiers Java.
- **#032 (confirmé) :** 89 composants Angular importent encore `CommonModule` (inutile depuis la migration standalone `@if`/`@for`).
- **#013 (confirmé + progression) :** 236 occurrences `style=""` inline (232 au premier audit — +4, tendance à la hausse). Top 5 : vente-detail.html (25), property-detail.html (21), home-dashboard.html (21), prospect-detail.html (19), project-create-wizard.html (15).
- **#014 :** `home-dashboard.component.css` = 1 656 lignes ; budget CSS relevé de 16 à 20 kB pour passer le build.

---

### 9 · COUVERTURE DE TESTS

**Statut 2026-06-13 :**

| Périmètre | Compte | Évolution depuis 2026-06-03 |
|-----------|--------|---------------------------|
| Backend unit (@Test, Surefire) | **200** | +80 (était ~120 estimés) |
| Backend IT (Failsafe, Testcontainers) | Inclus dans 623 @Test total | Stable |
| Frontend specs unitaires | **4** (était 3) | +1 absorption.spec.ts |
| E2E Playwright | ~35 tests / 9 specs | Stable |

Cœurs critiques désormais couverts : `VenteService` (19 tests), `RemboursementService` (8), `QuotaService` (8), `ContactImportService` (8), `GroupClientService` (10), `GlbValidator` (7).

Lacunes persistantes (F-005/F-011) :
- ~54 services backend encore sans test dédié
- 4 specs pour 175 fichiers TS frontend
- IT VEFA pipeline (pipeline complet Loi 44-00) différé à CI (besoin Docker)

---

### 10 · LÉGAL & CONFORMITÉ

**Statut : ⚠️ Actes organisationnels en attente**

- **CNDP (#026) :** La déclaration doit être déposée par l'opérateur ; le champ `numeroCndp` sur `Societe` et les pages légales portail affichent le numéro dès qu'il est saisi.
- **Rétention (#027) :** Aucune matrice de rétention (prospect / acquéreur / vente VEFA) n'est documentée. La purge GDPR existe (`DataRetentionScheduler`) mais ses durées sont dans l'implémentation, non dans une politique lisible.
- **PDFs (#031) :** Contrat de réservation + PV de livraison générés mais non relus par un notaire contre les mentions obligatoires Loi 44-00.

---

### 11 · DOCUMENTATION

**Statut : ✅ Bonne base ; gap #034**

11 guides utilisateur + guide légal Loi 44-00 + ADRs + business rules. Guide légal précis sur les articles (5%, 7j, Art.618-17). Gap #034 : le guide manager contient `option_expire_at`, `VenteService.validateTransition`, "scheduler horaire", `MARKET_CODE` — langage ingénieur qui déroute le manager terrain. Passe plain-French requise (XS).

---

## ÉTAT DU SYSTÈME — SCORES SYNTHÉTIQUES

| Domaine | Score | Tendance |
|---------|-------|----------|
| Sécurité multi-tenant | 9/10 | → Stable |
| Conformité VEFA Loi 44-00 | 8/10 | ↑ vs 6/10 au premier audit |
| Couverture tests backend | 6/10 | ↑ vs 3/10 au premier audit |
| Couverture tests frontend | 2/10 | → Stable (4 specs) |
| Qualité code Angular | 5/10 | → Stable (dette mesurable) |
| UX / Navigation | 6/10 | ↑ vs 5/10 (3D entry, font, contracts) |
| Complétude fonctionnelle | 7/10 | ↑ vs 5/10 (Vue Groupe, import, remboursement) |
| Documentation | 7/10 | ↑ vs 5/10 (guides, legal, ADRs) |

---

*Sources consolidées : `docs/audit/audit-report-2026-06-03.md` + `docs/review/team-review-2026-06-12.md`. Plan d'action priorisé : `docs/audit/action-plan-2026-06-13.md`.*
