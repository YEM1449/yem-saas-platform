# RAPPORT D'AUDIT YEM HLM — 2026-06-03

**Auditeur :** Claude Code Expert Team (Architecte · Sécurité · 3D · BI · UX · DevOps · Tech Writer)
**Version plateforme :** Wave 15 (CLAUDE.md) — branche `Epic/Dashboard-UIUX-improvement`
**Changesets Liquibase :** 001–074 (prochain : **075**)
**Scope :** Plateforme complète + Module 3D + Documentation
**Méthode :** Audit en lecture seule. Chaque conclusion est adossée à une commande exécutée (grep/find/lecture de fichier), pas à une supposition. Aucune modification de code pendant les phases 0–8.

---

## RÉSUMÉ EXÉCUTIF

**Ce qui fonctionne bien.** La plateforme est dans un état de maturité élevé pour un produit à ce stade. L'isolation multi-société est solide et appliquée en profondeur : `requireSocieteId()` est invoqué sur ~280 sites, le RLS PostgreSQL phase 2 (`RlsContextAspect` + changesets 050/051) sert de filet de sécurité au niveau base, et les rares `findAll()` repérés sont soit scopés société (`VenteService.findAll()` appelle bien `requireSocieteId()`), soit réservés au SUPER_ADMIN (`SocieteService`). La sécurité frontend est exemplaire : le JWT vit dans un cookie httpOnly (`hlm_auth`), aucun token n'est stocké en `localStorage` (réservé aux préférences langue/affichage), ce qui neutralise le vecteur XSS-vol-de-token. La surface d'injection SQL est nulle (0 requête construite par concaténation ; 0 `BeanUtils.copyProperties`/`ModelMapper` donc pas de mass-assignment). Les machines à états Vente et Tranche sont implémentées explicitement et gardées (transitions interdites → 409). Le module 3D fait preuve d'une hygiène WebGL remarquable (dispose complet, devicePixelRatio plafonné à 1.5, Page Visibility API).

**Risques majeurs.** Deux problèmes dominent. (1) **La règle « un seul engagement actif par bien » (RG-B03) n'est PAS appliquée.** La méthode de garde `VenteRepository.existsBySocieteIdAndPropertyIdAndStatutNot(...)` existe — preuve de l'intention — mais n'est appelée nulle part. `VenteService.create()` se contente de vérifier que le bien est `ACTIVE` ou `RESERVED` ; comme une vente laisse le bien en `RESERVED` (état non exclusif), une seconde vente repasse le contrôle et peut être créée sur le même lot. Aucune contrainte d'unicité partielle en base ne rattrape ce cas. C'est un risque de double-vente sur le cœur métier. (2) **Le service le plus critique de la plateforme — `VenteService` — n'a aucun test backend** (ni `VenteServiceTest`, ni `VenteControllerIT` : `find` sur `*vente*` dans `src/test` ne retourne rien). La machine à états, l'avancement automatique du statut contact et le cycle de vie du bien ne sont vérifiés qu'indirectement par `pipeline.spec.ts` (E2E, 17 tests).

**Priorité d'action.** Avant tout déploiement supplémentaire, traiter RG-B03 (garde applicative + index unique partiel, effort < 1 j) et couvrir `VenteService` de tests (effort < 3 j). En second rideau : harmoniser la sémantique cross-société (403 vs 404), durcir la validation binaire des GLB côté backend, et combler la dette de tests (54/64 services sans test dédié, 3 specs unitaires frontend pour 175 fichiers TS).

---

## TABLEAU DE BORD DES FAILLES

| ID | Sévérité | Module | Description | RG impactée | Effort | Priorité |
|----|----------|--------|-------------|-------------|--------|----------|
| F-001 | 🔴 CRITIQUE | vente | `create()` n'empêche pas une 2ᵉ vente active sur un bien déjà engagé. Garde `existsBySocieteIdAndPropertyIdAndStatutNot` jamais appelée ; pas d'index unique partiel. Risque double-vente. | RG-B03 | S | **P0** |
| F-002 | 🔴 CRITIQUE | vente | `VenteService` (machine à états + effets contact/bien) sans aucun test backend (0 `*Test`, 0 `*IT`). | RG-B04/B05/B10 | M | **P1** |
| F-003 | 🟠 MAJEUR | common/error | `CrossSocieteAccessException` → 403 alors que le reste du cross-société renvoie 404 (tests d'isolation). Incohérence + divulgation d'existence. | RG-A01 | XS | P2 |
| F-004 | 🟠 MAJEUR | viewer3d | Validation GLB backend = métadonnées seules ; le flag client `dracoCompressed` est cru. Pas de validation binaire (magic glTF / KHR_draco via Range request). | RG-E05 | S | P2 |
| F-005 | 🟠 MAJEUR | (transverse) | 54/64 services sans test unitaire/IT dédié (couverture partielle via 45 IT contrôleur). Cœurs non couverts directement : Vente, Reservation, ProjectGeneration, Auth. | — | L | P2 |
| F-006 | 🟡 MINEUR | (transverse) | ~36 endpoints contrôleur renvoient `List<>` non paginé. Bornés par la société mais non bornés pour une grande société. | RG-G* | M | P2 |
| F-007 | 🟡 MINEUR | viewer3d / templates | 2 templates encore en `*ngIf`/`*ngFor` (`mesh-mapping-admin`, `template-editor`). Migration Angular 19 control-flow incomplète. | — | XS | P3 |
| F-008 | 🟡 MINEUR | frontend | 222 `.subscribe()` sans `takeUntilDestroyed`. Beaucoup sont one-shot HTTP, mais à auditer sur composants longue durée (polling, 3D). | — | M | P2 |
| F-009 | 🟡 MINEUR | frontend | 232 `style="..."` inline dans les templates → incohérence avec le design system / tokens. | — | M | P3 |
| F-010 | 🟡 MINEUR | viewer3d | Composants `color-legend` et `model-no-config` (fallback RG-E10) nommés au spec mais absents ; vérifier que le fallback « aucun modèle » est géré inline. | RG-E10 | S | P2 |
| F-011 | 🟡 MINEUR | frontend | 3 specs unitaires pour 175 fichiers TS → couverture unitaire frontend quasi nulle. | — | L | P2 |
| F-012 | 🔵 INFO | base | Pas de soft-delete (`deleted_at`) sur les tables domaine — suppressions physiques. Choix de conception ; impact piste d'audit/GDPR à documenter. | RG-D* | — | P3 |
| F-013 | 🔵 INFO | docs | Fichiers d'état périmés : `.sprint-state.md`/`.audit-state` (Wave 10, 58 changesets) contredisent CLAUDE.md (Wave 15, 74). CLAUDE.md fait foi. | — | XS | P3 |
| F-014 | 🔵 INFO | docs | Chemins du prompt absents : `db/changelog/changes/` est sous `hlm-backend/src/main/resources/` ; `docs/ai/quick-context.md`/`deep-context.md` n'existent pas ; les guides sont sous `docs/guides/user/` (pas `docs/user-guide/`). | — | — | P3 |
| F-015 | 🔵 INFO | CI/CD | Aucun workflow de déploiement/CD (Render/Cloudflare manuels). Pas de job lint dédié distinct du build. | — | S | P3 |

Légende effort : XS=<2h, S=<1j, M=<3j, L=<1sem, XL=>1sem.

---

## DÉTAIL PAR MODULE

### MODULE : vente (pipeline commercial)
**Statut global :** ⚠️ Partiellement conforme
**Failles détectées :** 3 (F-001, F-002, et dépendance F-006)
**Tests manquants :** tous (cœur métier non testé côté backend)

#### Failles
- **F-001 — RG-B03 non appliquée.** `VenteService.create()` (`hlm-backend/.../vente/service/VenteService.java:197-208`) ne vérifie que `property.getStatus()` ∈ {ACTIVE, RESERVED}. Une vente laisse le bien `RESERVED`, donc une seconde `create()` repasse le test. La garde existe (`VenteRepository.java:25 existsBySocieteIdAndPropertyIdAndStatutNot`) mais `grep` confirme **0 appel**. Aucune contrainte unique partielle dans les changesets 058 ou 026.
  - **Correction recommandée :** appeler la garde en tête de `create()` (`if existsActiveVente → throw ConflictException 409`) **et** ajouter un index unique partiel `CREATE UNIQUE INDEX ... ON vente(property_id) WHERE statut <> 'ANNULE'` (changeset 075).
- **F-002 — Aucun test backend.** Machine à états `validateTransition()` (`:580-590`), avancement contact `ACTIVE_CLIENT`/`COMPLETED_CLIENT` (`:211`, `:305`), bascule bien `SOLD` à `ACTE_NOTARIE` / release à `ANNULE` (`:317-325`) : aucun `VenteServiceTest`/`VenteControllerIT`.
  - **Correction :** créer `VenteServiceTest` (transitions valides/invalides, prix verrouillé, effets contact) + `VenteControllerIT` (RBAC, cross-société 404, 409 double-vente).

**Points positifs :** machine à états explicite et gardée (Set d'autorisés par statut → `InvalidVenteTransitionException` 409) ; effets de bord dans la même `@Transactional` que la mutation (pas de thread séparé) ; `findAll()`/`findByContactId()`/`findById()` tous scopés `requireSocieteId()`.

### MODULE : viewer3d (3D)
**Statut global :** ✅ Conforme (front) / ⚠️ Partiellement conforme (validation backend)
**Failles :** F-004 (validation binaire), F-010 (composants fallback)

#### Constat
- **Front excellent :** `three-engine.service.ts.dispose()` annule le `requestAnimationFrame`, retire tous les listeners (`pointermove`, `click`, `visibilitychange`), `controls.dispose()`, et traverse la scène pour `geometry.dispose()` + `material.dispose()`. `project-viewer-3d.component.ts.ngOnDestroy()` appelle `subs.unsubscribe()` + `engine.dispose()`. `setPixelRatio(Math.min(window.devicePixelRatio, 1.5))`. Page Visibility API câblée. → Aucune fuite mémoire WebGL détectée.
- **F-004 :** le workflow upload (`Project3dController` POST `/3d-model/upload-url` → pré-signé R2 → confirm) ne valide pas le binaire GLB côté serveur ; la compression Draco est attestée par un flag client. Conformité RG-E05 partielle.
- **F-010 :** les composants `color-legend.component.ts` et `model-no-config.component.ts` du spec sont absents (présents : `dashboard-3d-tab`, `lot-tooltip-3d`, `mesh-mapping-admin`, `model-upload-admin`, `project-viewer-3d`). Vérifier que le cas « projet sans modèle 3D » est géré (fallback RG-E10).

### MODULE : auth / sécurité
**Statut global :** ✅ Conforme
- JWT en cookie httpOnly (`auth.service.ts` : « no token is stored in JS »), interceptor `withCredentials` sans header Authorization (sauf switchSociete partiel). 0 `[innerHTML]`. `localStorage` réservé aux préférences.
- `CorsConfig` présent (origines restrictives, cf. memory CORS local dev).
- 0 secret en dur dans le code Java/TS.
- **F-003 :** `CrossSocieteAccessException` → 403 ; à harmoniser avec la sémantique 404 dominante (les requêtes scopées société renvoient `*NotFoundException` → 404).

### MODULE : base de données / migrations
**Statut global :** ✅ Conforme
- 74 changesets séquentiels, FK société sur les tables domaine, RLS phase 2.
- **Pas d'anti-pattern ROW_NUMBER-in-UPDATE** : les changesets 060 et 064 utilisent `ROW_NUMBER() OVER` dans une **CTE** explicitement pour contourner la restriction (commentaire à l'appui). Pattern correct.
- N+1 faible : 3 `@OneToMany`, 0 `FetchType.EAGER`.
- **F-012 :** pas de `deleted_at` (un seul changeset le mentionne) → suppressions physiques.

### MODULE : frontend (architecture Angular 19)
**Statut global :** ✅ Conforme (majoritairement)
- 0 `NgModule`, 0 `standalone: false`, routing standalone.
- **F-007 :** 2 templates legacy `*ngIf`/`*ngFor`. **F-008 :** 222 `.subscribe()` non gardés. **F-009 :** 232 styles inline. **F-011 :** 3 specs pour 175 TS.

### MODULE : dashboard / reporting
**Statut global :** ✅ Conforme
- KPI cohérents via services dédiés (`HomeDashboardService`, `KpiComputationService`, `CommercialDashboardService`) ; absorption unifiée (`core/utils/absorption.ts` = backend `HomeDashboardDTO`). Distinction CA acté (ACTE_NOTARIE) / CA livré (LIVRE) respectée.
- **F-006 :** ~36 endpoints `List<>` non paginés.

---

## COUVERTURE DE TESTS

| Périmètre | Existant | Scénarios critiques couverts | Taux estimé |
|-----------|----------|------------------------------|-------------|
| Backend unit (`*Test.java`) | 20 | machine à états Tranche, complétude contact, dates, KPI | ~30 % des services |
| Backend IT (`*IT.java`, Testcontainers) | 45 | RBAC, isolation cross-société (404), contrôleurs principaux | bon sur contrôleurs |
| **Vente (cœur)** | **0** | **aucun** (uniquement E2E) | **0 % backend** |
| Frontend unit (`*.spec.ts`) | 3 | quasi nul | <2 % |
| E2E Playwright | 9 specs (~35 tests) | auth, contacts, tasks, pipeline (17), portal, wizard, 3D | parcours principaux |

**Scénarios critiques NON couverts (backend) :** double-vente → 409 (RG-B03) ; prix verrouillé après acompte → 422 (RG-B07) ; annulation vente → recalcul statut contact (RG-B10) ; accès cross-société vente → 404.

---

## SÉCURITÉ (OWASP)

| Vecteur | Statut | Preuve |
|---------|--------|--------|
| A01 Broken Access Control | ✅ Maîtrisé (1 incohérence) | `requireSocieteId()` ×280, RLS phase 2 ; F-003 (403 vs 404) |
| A02 Cryptographic Failures | ✅ | JWT cookie httpOnly, BCrypt seed conforme |
| A03 Injection | ✅ | 0 requête concaténée ; JPA paramétré ; 0 native query dynamique |
| A04 Insecure Design | ⚠️ | F-001 (RG-B03 non appliquée) |
| A05 Security Misconfiguration | ✅ | `CorsConfig` restrictif, 0 secret en dur |
| A07 Auth Failures | ✅ | lockout (changeset 027), rate-limit login/invitation/portal |
| A08 Data Integrity | ⚠️ | F-004 (GLB non validé binairement) |
| Mass Assignment | ✅ | 0 `BeanUtils`/`ModelMapper`, DTO-first |

---

## PERFORMANCE

- **N+1 :** risque faible (3 `@OneToMany`, 0 EAGER).
- **Pagination :** ~36 endpoints `List<>` non bornés (F-006) — à paginer pour les sociétés volumineuses.
- **WebGL :** optimisé (DPR≤1.5, Page Visibility, dispose complet, Draco côté chargement). Cache statut lots 10 s (Caffeine).
- **Cache :** Redis (`PROJECTS_CACHE`, `SOCIETES_CACHE`) + Caffeine par cache nommé.

---

## DETTE TECHNIQUE

1. **Tests** — `VenteService` non testé (F-002), 54/64 services sans test (F-005), frontend quasi non testé (F-011). *Priorité absolue avant montée à l'échelle.*
2. **Pagination** — 36 listes non paginées (F-006).
3. **Cohérence front** — 222 subscriptions non gardées (F-008), 232 styles inline (F-009), 2 templates legacy (F-007).
4. **Fallback 3D** — composants légende/no-config (F-010).
5. **Documentation d'état** — fichiers `.X-state` périmés (F-013), chemins divergents (F-014).
6. **CD** — pas de pipeline de déploiement automatisé (F-015).

---

*Fin du rapport. Plan d'action priorisé : `docs/audit/action-plan-2026-06-03.md`.*
