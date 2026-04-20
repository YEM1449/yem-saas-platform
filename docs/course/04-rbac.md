# Module 04: Role-Based Access Control

## Why This Matters

The platform has both platform-wide and societe-scoped roles, and confusing them is a common source of bugs.

## Learning Goals

- distinguish `SUPER_ADMIN` from societe roles
- understand route and method-level authorization
- understand business rules around role assignment

## Role Model

### Platform role

- `SUPER_ADMIN`

### Societe roles

- `ADMIN`
- `MANAGER`
- `AGENT`

### Portal role

- `ROLE_PORTAL`

## Files To Study

- [../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/SecurityConfig.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/SecurityConfig.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/common/security/SocieteRoleValidator.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/common/security/SocieteRoleValidator.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/usermanagement/UserManagementController.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/usermanagement/UserManagementController.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/societe/api/SocieteController.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/societe/api/SocieteController.java)

## Important Business Rule

Only `SUPER_ADMIN` can assign `ADMIN`.
This prevents ordinary company administrators from escalating themselves into platform governance patterns.

## Exercise

Pick one route from each of these families and explain why the required role makes sense:

- `/api/admin/**`
- `/api/**`
- `/api/portal/**`
