# Spec Glossary (Phase-1 Consolidation)

## Canonical vocabulary

| Term (canonical) | Synonyms in specs | Canonical meaning | Open point |
|---|---|---|---|
| Tenant | Société, Multi-sociétés | Logical business boundary used for data isolation and access scope. | Should "tenant" always equal legal company, or can a holding host multiple legal entities? |
| Project | Projet | Real-estate program grouped under a tenant/company. | Project hierarchy not specified (phase/block/sub-project). |
| Property | Lot | Sellable unit (apartment/villa/local/commercial lot). | Naming conflict in specs (Lot) vs code (Property). |
| Contact | Prospect, Client, Temp Client | Person/legal entity in commercial funnel; can become client based on workflow. | "Temp Client" lifecycle precision needed for legal/contract semantics. |
| Deposit | Acompte, Dépôt, Réservation | Monetary reservation event that locks a property and drives status transitions. | Distinguish between reservation fee vs contractual down payment. |
| Reservation | Réservation | Business action binding contact + property before full contract execution. | Separate aggregate from Deposit currently unclear in specs. |
| Notification | Alerte, Relance | In-app event informing users for workflow state changes. | SMS/email channels and templates not yet fully specified. |
| RBAC | Rôles/Permissions | Role-based permissions (Admin/Manager/Agent minimum in code). | Fine-grained module permissions matrix still open. |
| Audit trail | Traçabilité | Immutable (or append-only) event/log trail for sensitive actions. | Required depth unclear (field-level vs action-level). |
| Document management | Archivage documentaire | Storage and retrieval of generated and uploaded documents. | Storage backend and retention policy undecided. |

## Roadmap extraction (from spec set)
- **V1 (MVP)**: MOD-01..MOD-05 + MOD-11 emphasis (IAM, multi-tenant, commercial core, foncier/admin simplifié, dashboards).
- **V2**: MOD-06..MOD-09 emphasis (construction, stocks, achats, finance control).
- **V3**: MOD-10 + MOD-12 + MOD-13 and deeper integrations/quality/security.

## Conflicts seen in spec text
1. Lot vs Property naming.
2. Prospect/Client/Temp Client lifecycle partially ambiguous.
3. Acompte/Dépôt/Réservation modeled as one or multiple bounded contexts.
4. Technical stack proposed in old docs differs from implemented stack (actual code is Spring Boot + Angular).
