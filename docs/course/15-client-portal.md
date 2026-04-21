# Module 15: Client Portal

## Why This Matters

The portal gives buyers self-service visibility without turning them into CRM users.

## Learning Goals

- understand the portal auth flow
- understand portal authorization boundaries
- understand how portal data depends on underlying CRM records

## Portal Flow

1. request link
2. email sends one-time token
3. buyer verifies the token
4. portal session cookie is created
5. buyer views owned data only

## Files To Study

- [../../hlm-backend/src/main/java/com/yem/hlm/backend/portal/service/PortalAuthService.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/portal/service/PortalAuthService.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/portal/api/PortalAuthController.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/portal/api/PortalAuthController.java)
- [../../hlm-frontend/src/app/portal/core/portal-auth.service.ts](../../hlm-frontend/src/app/portal/core/portal-auth.service.ts)
- [../guides/user/client-portal.md](../guides/user/client-portal.md)

## Important Boundary

The portal is not a simplified CRM.
It is a separate security surface with its own session and ownership checks.

## Exercise

Describe two reasons why the platform should not reuse the staff auth flow for buyers.
