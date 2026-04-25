# Module 20: 3D Building Visualiser

## Why This Matters

Promoteurs sell from a floor plan today and a model tomorrow. The 3D viewer bridges that gap without adding a separate tool: it pulls live lot-status data from the same database that drives the pipeline and renders it in a WebGL scene inside the CRM shell. Agents click a mesh to open the reservation flow; managers watch colour change as inventory sells in real time; buyers see a read-only version through the portal.

## Learning Goals

- understand how a Three.js scene integrates with Angular change detection
- understand how pre-signed URLs deliver large binary assets without backend streaming
- understand how mesh-to-lot mapping decouples the 3D model from the data model
- understand how portal isolation extends to a new feature without duplicating security logic
- understand the cache and polling strategy that keeps colour-coding live at acceptable cost

## Module Structure

```text
modules/viewer-3d/
  models/         – TypeScript interfaces and colour constants
  services/
    three-engine.service.ts     – renderer, camera, raycaster, RAF loop
    model-loader.service.ts     – GLTFLoader + DRACOLoader as an Observable
    lot-mapping.service.ts      – mesh↔lot paint and highlight
    viewer-3d-api.service.ts    – HTTP calls to backend
  components/
    project-viewer-3d/          – full-screen interactive viewer
    lot-tooltip-3d/             – hover tooltip overlay
    dashboard-3d-tab/           – dashboard embed with KPI overlay and PDF export
  viewer-3d.routes.ts           – lazy route definition
```

Backend:

```text
viewer3d/
  api/        – Project3dController (CRM), PortalProject3dController (portal)
  domain/     – Project3dModel, Lot3dMapping entities
  repo/       – Project3dModelRepository, Lot3dMappingRepository
  service/    – Project3dService
```

## Files To Study

- [../../hlm-frontend/src/app/modules/viewer-3d/services/three-engine.service.ts](../../hlm-frontend/src/app/modules/viewer-3d/services/three-engine.service.ts)
- [../../hlm-frontend/src/app/modules/viewer-3d/services/lot-mapping.service.ts](../../hlm-frontend/src/app/modules/viewer-3d/services/lot-mapping.service.ts)
- [../../hlm-frontend/src/app/modules/viewer-3d/components/project-viewer-3d/project-viewer-3d.component.ts](../../hlm-frontend/src/app/modules/viewer-3d/components/project-viewer-3d/project-viewer-3d.component.ts)
- [../../hlm-frontend/src/app/modules/viewer-3d/components/dashboard-3d-tab/dashboard-3d-tab.component.ts](../../hlm-frontend/src/app/modules/viewer-3d/components/dashboard-3d-tab/dashboard-3d-tab.component.ts)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/viewer3d/service/Project3dService.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/viewer3d/service/Project3dService.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/viewer3d/api/Project3dController.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/viewer3d/api/Project3dController.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/viewer3d/api/PortalProject3dController.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/viewer3d/api/PortalProject3dController.java)

## Key Concepts

### Zone isolation

Three.js runs a `requestAnimationFrame` loop at ~60 Hz. If it ran inside Angular's zone, every frame would trigger change detection across the whole tree. The engine service wraps the loop in `NgZone.runOutsideAngular()` and only re-enters the zone when a hover or click Subject actually emits.

### Pre-signed URL delivery

The GLB file lives in object storage, not in the database. The backend generates a 15-minute pre-signed `GET` URL and returns it inside the model metadata response. The frontend fetches the binary directly from storage — the backend carries no streaming load.

### Mesh-to-lot mapping

The 3D artist names each mesh in the model (for example `LOT-A1-T3`). A mapping table (`lot_3d_mapping`) links each `mesh_id` string to a real `property_id`. The `LotMappingService` holds this index in memory after the scene loads so raycaster hits can be resolved in O(1) without any HTTP call.

### Colour-only status updates

When the 30-second poll returns a fresh snapshot, `LotMappingService.updateColors()` modifies `material.color` in place on existing mesh materials. The scene is not rebuilt and no geometries are re-allocated. This keeps live updates cheap regardless of scene complexity.

### Portal access guard

`PortalProject3dController` is a separate controller under `/api/portal/**`. It calls `Project3dService.portalUserHasAccess()` which runs a native SQL join between `vente` and the requested project's properties to confirm the buyer owns at least one lot in the project. A 404 is returned on failure rather than a 403 to avoid leaking model existence to unauthorized buyers.

### Status colour map

```text
DRAFT / ACTIVE  -> DISPONIBLE  (#3B82F6 blue)
RESERVED        -> RESERVE     (#F59E0B amber)
SOLD            -> VENDU       (#10B981 green)
WITHDRAWN / ARCHIVED -> LIVRE  (#6B7280 grey)
```

## Key Insight

The viewer does not introduce a new data source. It reads from the same `property` table that drives the pipeline. The mapping table is the only 3D-specific data structure; all business logic stays in the existing modules.

## Exercise

Explain why `updateColors()` does not call `mapping.applyMappings()` again on each poll cycle, and what would break if it did.
