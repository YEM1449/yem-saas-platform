# ADR-005 : Visualiseur 3D commercial avec Three.js

**Statut :** Accepté
**Date :** 2026-06-03 (rédigé a posteriori d'après le code Wave 13–15)
**Décideurs :** Architecte, Ingénieur 3D Web

## Contexte
Le module commercial doit offrir une vue 3D des projets immobiliers : un acquéreur localise son lot, un agent/manager visualise l'absorption par couleur de statut. Contraintes : modèles GLB potentiellement lourds, exécution navigateur (desktop + mobile), pas de fuite mémoire au montage/démontage répété (navigation SPA), et stockage objet R2 sans charger les binaires via le backend.

## Décision
- **Three.js 0.165.0** (+ `@types/three` 0.165.0), chargé paresseusement dans `modules/viewer-3d/` (composants standalone Angular 19).
- **Upload GLB en deux étapes, direct-to-R2 :** `POST /api/projects/{id}/3d-model/upload-url` renvoie une URL PUT pré-signée (`MediaStorageService.generatePresignedPutUrl`), le client `XMLHttpRequest` téléverse directement vers R2 (barre de progression), puis `POST /3d-model` confirme les métadonnées. Le backend ne relaie jamais le binaire.
- **Hygiène WebGL** centralisée dans `ThreeEngineService.dispose()` : `cancelAnimationFrame`, retrait des listeners (`pointermove`, `click`, `visibilitychange`), `controls.dispose()`, traversée de scène avec `geometry.dispose()` + `material.dispose()`. Appelé depuis `ngOnDestroy` des composants hôtes.
- **Performance mobile :** `renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.5))` ; Page Visibility API (pause du rendu hors-onglet).
- **Statut par couleur** (PropertyStatus → affichage) : DISPONIBLE (#3B82F6), RESERVE (#F59E0B), VENDU (#10B981), RETIRE (#6B7280). Snapshot de statut caché 10 s (Caffeine `LOT_STATUS_3D_CACHE`), poll 30 s côté viewer.
- **Mapping mesh↔lot** persisté (`lot_3d_mapping`, changeset 072), édité via `mesh-mapping-admin`.
- **Accès portail :** `PortalProject3dController` (ROLE_PORTAL) renvoie 404 sauf si l'acquéreur a ≥1 vente sur un lot mappé du projet.

## Conséquences
- ✅ Pas de fuite mémoire WebGL (dispose exhaustif vérifié à l'audit 2026-06-03).
- ✅ Backend déchargé du transfert binaire (scalabilité, coût R2).
- ⚠️ **Dette (F-004) :** la validation du GLB est aujourd'hui basée sur le flag client `dracoCompressed` ; pas de validation binaire serveur (magic glTF + `KHR_draco_mesh_compression`). Voir action plan C-002.
- ⚠️ **Dette (F-010) :** composants `color-legend` et `model-no-config` (fallback RG-E10) à formaliser.

## Alternatives rejetées
- **Relais du binaire via backend :** rejeté (charge CPU/réseau, limite taille requête, coût).
- **Babylon.js :** rejeté (écosystème Angular/typings Three.js plus simple, suffisant pour le besoin).
- **Rendu serveur d'images statiques :** rejeté (perte de l'interaction hover/clic essentielle au parcours commercial).
