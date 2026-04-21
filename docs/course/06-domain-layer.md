# Module 06: Domain Layer And Service Design

## Why This Matters

Most business changes happen in service code, not in controllers.

## Learning Goals

- understand the controller-service-repository pattern
- identify where business rules belong
- learn how bounded modules are represented in code

## Design Pattern

- controllers handle HTTP concerns
- services enforce workflow rules
- repositories persist or query state
- entities and enums model business state

## Good Modules To Compare

- `contact`
- `property`
- `reservation`
- `vente`
- `task`

## Files To Study

- [../../hlm-backend/src/main/java/com/yem/hlm/backend/contact/api/ContactController.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/contact/api/ContactController.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/property/api/PropertyController.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/property/api/PropertyController.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/vente/api/VenteController.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/vente/api/VenteController.java)

## Exercise

Choose one service and identify:

- the business invariants it protects
- the repositories it relies on
- the exceptions it raises
