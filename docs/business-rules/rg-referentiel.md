# Référentiel des Règles de Gestion — YEM HLM

> Vérifié lors de l'audit 2026-06-03 (`docs/audit/audit-report-2026-06-03.md`). Seules les règles **effectivement contrôlées dans le code** lors de l'audit figurent ici, avec leur statut réel. Document complémentaire de `docs/spec/business-rules-audit.md`.

Statuts : ✅ Implémentée · ⚠️ Partielle · ❌ Manquante

## RG-A01 · Isolation cross-société
**Module :** common/security · **Statut :** ⚠️ Partielle
**Règle :** une société ne doit jamais accéder aux ressources d'une autre ; comportement cohérent (404 de préférence, non-divulgation).
**Backend :** `requireSocieteId()` (~280 sites) + RLS phase 2 (`RlsContextAspect`, changesets 050/051). La plupart des accès cross-société → `*NotFoundException` (404).
**Écart :** `CrossSocieteAccessException` → **403** (incohérent). Voir F-003 / action C-001.
**Tests :** `CrossSocieteIsolationIT` (contacts, projets, properties, timeline → 404).

## RG-B03 · Un seul engagement actif par bien
**Module :** vente · **Statut :** ✅ Implémentée (2026-06-03)
**Règle :** un bien ne peut avoir qu'une vente active (statut ≠ ANNULE) à la fois.
**Backend :** `VenteService.create()` appelle `existsBySocieteIdAndPropertyIdAndStatutNot(societeId, propertyId, ANNULE)` → `PropertyAlreadyEngagedException` (409 `PROPERTY_ALREADY_ENGAGED`). Filet base concurrent : changeset **075** `uk_vente_active_property` (index unique partiel `WHERE statut <> 'ANNULE'`).
**Tests :** ✅ `VenteServiceTest.create_rejectsDuplicateActiveVente` + `VenteControllerIT.create_secondVenteOnSameProperty_returns409` + `cancelledVente_freesPropertyForNewVente`.

## RG-B04 · Machine à états des transitions de vente
**Module :** vente · **Statut :** ✅ Implémentée
**Règle :** COMPROMIS → FINANCEMENT → ACTE_NOTARIE → LIVRE ; ANNULE possible depuis tout état non terminal. LIVRE/ANNULE terminaux.
**Backend :** `VenteService.validateTransition()` (Set d'autorisés par statut) → `InvalidVenteTransitionException` (409).
**Tests :** ✅ (2026-06-03) `VenteServiceTest` (skip-stage, terminal LIVRE, ANNULE-needs-motif) + `VenteControllerIT` (valid 200, skip-stage 409).

## RG-B05 · Cycle de vie du bien piloté par la vente
**Module :** vente / property · **Statut :** ✅ Implémentée
**Règle :** le bien devient SOLD à ACTE_NOTARIE (clôture commerciale) ; ANNULE le relibère vers ACTIVE.
**Backend :** `VenteService.updateStatut()` (`:317-325`) via `PropertyCommercialWorkflowService`.
**Tests :** ❌ backend absent.

## RG-B07 · Prix verrouillé après acompte
**Module :** vente · **Statut :** ⚠️ À vérifier
**Règle :** le prix de vente ne doit plus changer après l'acompte.
**Backend :** `prixVente` figé à la création ; pas de contrôle explicite de modification post-acompte repéré à l'audit.
**Tests :** ❌ à créer (modification prix après acompte → 422).

## RG-B10 · Effets automatiques sur le statut contact
**Module :** vente / contact · **Statut :** ✅ Implémentée (non testée)
**Règle :** création de vente → contact ACTIVE_CLIENT ; livraison (LIVRE) → COMPLETED_CLIENT ; annulation → recalcul.
**Backend :** `advanceContactStatus()` dans la **même** `@Transactional` que la mutation vente (`VenteService:211`, `:305`). Pas de thread séparé. ✅
**Tests :** ❌ backend absent (recalcul à l'annulation à couvrir).

## RG-E05 · Validation du modèle 3D (GLB/Draco)
**Module :** viewer3d · **Statut :** ⚠️ Partielle
**Règle :** seuls des GLB Draco-compressés valides sont acceptés.
**Backend :** validation par flag client `dracoCompressed` ; pas de validation binaire (magic glTF / KHR_draco).
**Correction :** action C-002.

## RG-E10 · Fallback « projet sans modèle 3D »
**Module :** viewer3d · **Statut :** ⚠️ À formaliser
**Règle :** afficher un état clair quand aucun modèle 3D n'est configuré.
**Front :** composant `model-no-config` absent ; vérifier le fallback inline. Voir F-010 / B-003.

---

*Pour l'inventaire métier complet (financier, légal marocain, gouvernance des alertes), voir `docs/spec/business-rules-audit.md` §11–14.*
