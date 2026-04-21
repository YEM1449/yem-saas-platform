# Module 12: Property Lifecycle And Inventory Modeling

## Why This Matters

Inventory is the backbone of the CRM. If properties are modeled badly, the sales process becomes unreliable.

## Learning Goals

- understand the project / immeuble / tranche / property hierarchy
- understand editorial versus workflow-driven property states
- understand how imports and media support the business flow

## Property Statuses

- `DRAFT`
- `ACTIVE`
- `RESERVED`
- `SOLD`
- `WITHDRAWN`
- `ARCHIVED`

## Files To Study

- [../../hlm-backend/src/main/java/com/yem/hlm/backend/property/domain/Property.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/property/domain/Property.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/property/domain/PropertyStatus.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/property/domain/PropertyStatus.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/property/api/PropertyController.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/property/api/PropertyController.java)
- [../guides/user/properties.md](../guides/user/properties.md)

## Things To Notice

- some statuses are controlled by business workflows, not just property editing
- type-specific validation is part of the domain model
- hierarchy and media both matter to user-facing quality

## Exercise

Explain why `RESERVED` and `SOLD` should not be treated the same way as `DRAFT` or `ACTIVE` in ordinary editing flows.
