# PLAN D'ACTION — YEM HLM POST-AUDIT 2026-06-03

Source : `docs/audit/audit-report-2026-06-03.md`. Chaque item a ID, sévérité, effort, fichiers, priorité.

## CRITÈRES DE PRIORISATION
- **P0** = Sécurité / intégrité des données : corriger avant tout déploiement.
- **P1** = Fonctionnel bloquant : le pipeline commercial ne peut pas être fiable sans.
- **P2** = Qualité importante : prochaine Wave.
- **P3** = Amélioration : backlog technique.

Effort : XS=<2h, S=<1j, M=<3j, L=<1sem, XL=>1sem.

---

## PHASE A — URGENT (P0) — sous 48h

| # | Faille | Action | Fichiers impactés | Durée | Changeset |
|---|--------|--------|-------------------|-------|-----------|
| A-001 | F-001 RG-B03 non appliquée | Appeler `existsBySocieteIdAndPropertyIdAndStatutNot` en tête de `create()` → 409 `PROPERTY_ALREADY_ENGAGED` si une vente non-ANNULE existe pour le bien | `vente/service/VenteService.java`, `common/error/ErrorCode.java`, `GlobalExceptionHandler.java` | S | — |
| A-002 | F-001 (filet base) | Index unique partiel anti double-vente | `075-vente-active-unique.yaml` : `CREATE UNIQUE INDEX uk_vente_active_property ON vente(property_id) WHERE statut <> 'ANNULE'` | XS | **075** |

> Note : A-001 et A-002 sont complémentaires (garde applicative lisible + garantie base contre les races concurrentes).

---

## PHASE B — CRITIQUE (P1) — Wave 16 (en cours)

| # | Faille | Action | Fichiers impactés | Durée | Changeset |
|---|--------|--------|-------------------|-------|-----------|
| B-001 | F-002 Vente non testée | `VenteServiceTest` : transitions valides/invalides (→409), double-vente (→409), avancement contact ACTIVE/COMPLETED, bascule bien SOLD@ACTE_NOTARIE / release@ANNULE | `vente/VenteServiceTest.java` | M | — |
| B-002 | F-002 Vente non testée (IT) | `VenteControllerIT` : RBAC par rôle, cross-société → 404, 409 double-vente, `PATCH /{id}/statut` machine à états | `vente/VenteControllerIT.java` | M | — |
| B-003 | F-010 Fallback 3D | ✅ **FAIT** — état `no-model` (404) gated `canManageModel` (ADMIN/MANAGER) sinon message informatif ; légende couleur déjà inline | `project-viewer-3d.component.*` | S | — |

---

## PHASE C — IMPORTANT (P2) — Wave 17

| # | Faille | Action | Fichiers impactés | Durée |
|---|--------|--------|-------------------|-------|
| C-001 | F-003 403/404 | ✅ **FAIT (requalifié faux positif)** — vérifié que le 403 ne concerne que le contexte manquant ; l'accès ressource est déjà 404. Documenté ; aucun changement code. | — | — |
| C-002 | F-004 GLB non validé | ✅ **FAIT** — `GlbValidator` (magic glTF + version + chunk JSON → `KHR_draco_mesh_compression`) → 422 ; gated `app.viewer3d.validate-glb-binary` ; 7 tests | `viewer3d/service/GlbValidator.java`, `Project3dService.java` | S |
| C-003 | F-006 Listes non paginées | ✅ **RÉSOLU (2026-06-13)** — `GET /api/contacts` (déjà paginé), `GET /api/ventes` et `GET /api/properties` migrés `Page<T>` → `PageResponse.of()` (`@PageableDefault` 20/50, sort createdAt DESC). FE : `PagedResult<T>` + `listPage()` ; callers bornés conservent `list()` capé (1000/2000 rows). `GET /api/notifications` : `@Max(200)` ajouté au contrôleur (service clampait déjà). `GET /api/audit/commercial` : `@Max(500)` déjà présent. 26 endpoints sub-ressource restant `List<T>` sont bornés par FK (venteId, projectId, etc.). Tests : `NotificationControllerTest` (5), `PropertyControllerTest` (+4), `VenteServiceTest` (+2) ; suite **200 verts**. | `*Controller.java`, `vente.service.ts`, `property.service.ts`, `NotificationControllerTest.java` | M |
| C-004 | F-005 Services non testés | ⏳ **EN COURS** — `QuotaServiceTest` (8), `ContactCompletenessServiceTest` (5), `VenteServiceTest` (6) ajoutés ; reste à étendre par vagues | `src/test/.../*Test.java` | L |
| C-005 | F-008 Subscriptions | ✅ **FAIT (faux positif)** — 0 flux infini non gardé ; HttpClient one-shot ; polling/3D/keep-alive déjà détruits | — | — |
| C-006 | F-011 Front non testé | ⏳ **EN COURS** — `absorption.spec.ts` ajouté (KPI canonique) ; étendre aux services core par vagues | `*.spec.ts` | L |

---

## PHASE D — BACKLOG (P3)

| # | Item | Description | Bénéfice | Effort |
|---|------|-------------|----------|--------|
| D-001 | F-007 | ✅ **FAIT** — `template-editor` + `mesh-mapping-admin` migrés `@if`/`@for` (0 legacy restant) | Cohérence Angular 19 | XS |
| D-002 | F-009 | ⏳ **DIFFÉRÉ** — 232 `style=` inline : refactor de masse cosmétique, traiter par lot en passe UI | Design system | M |
| D-003 | F-012 | Décider d'une stratégie soft-delete pour les entités à piste d'audit GDPR | Conformité/traçabilité | M |
| D-004 | F-013 | ✅ **FAIT** — bannière CURRENT STATE en tête de `.sprint-state.md` (renvoi CLAUDE.md) | Clarté | XS |
| D-005 | F-014 | Chemins divergents documentés dans le rapport d'audit (§ écarts) ; CLAUDE.md fait foi | DX | XS |
| D-006 | F-015 | ⏳ **DIFFÉRÉ** — workflow CD nécessite secrets/cibles de déploiement réels | Automatisation | S |

---

## TESTS À CRÉER

### Backend — scénarios critiques manquants
| Scénario | Fichier test | Assertion clé | Priorité |
|----------|-------------|---------------|----------|
| 2ᵉ vente sur bien déjà engagé → 409 | `VenteServiceTest` / `VenteControllerIT` | `assertThrows(...Conflict...)` / `status().isConflict()` | **P0** |
| Transition invalide (ex. COMPROMIS→LIVRE) → 409 | `VenteServiceTest` | `InvalidVenteTransitionException` | P1 |
| Prix modifié après acompte → 422 (RG-B07) | `VenteServiceTest` | statut 422 / exception dédiée | P1 |
| Annulation vente → statut contact recalculé (RG-B10) | `VenteServiceTest` | contact.statut recalculé | P1 |
| Accès vente cross-société → 404 | `VenteControllerIT` | `status().isNotFound()` | P1 |

### Playwright E2E — parcours manquants
| Parcours | Fichier spec | Rôle | Priorité |
|----------|-------------|------|----------|
| Agent → réserver bien → panneau s'ouvre | `vente-flow.spec.ts` | AGENT | P1 |
| Acquéreur → Viewer 3D → son bien surligné | `viewer-3d.spec.ts` (étendre) | PORTAL | P1 |
| Admin → upload GLB → mapping mesh | `model-upload.spec.ts` | ADMIN | P2 |

---

## DETTE TECHNIQUE (BACKLOG)
Fonctionnels mais à refactorer avant montée à l'échelle : pagination des listes (C-003), gardes de subscriptions (C-005), inline styles (D-002), stratégie soft-delete (D-003), CD automatisé (D-006).

---

## MÉTRIQUES CIBLES POST-CORRECTION

| Indicateur | Actuel | Cible Wave 17 |
|------------|--------|----------------|
| Endpoints sans filtre société | 0 (RLS + requireSocieteId) | 0 |
| Garde RG-B03 (double-vente) | ❌ absente | ✅ appli + index |
| `VenteService` testé | ❌ 0 | ✅ unit + IT |
| Services sans test | 54/64 | < 30/64 |
| Scénarios E2E critiques couverts | partiels | 100 % pipeline |
| Subscriptions non gardées | 222 | 0 (longue durée) |
| Endpoints `List<>` non paginés | ~36 | < 10 |
