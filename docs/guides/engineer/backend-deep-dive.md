# Backend Deep Dive

This guide explains how the backend is organized and how to extend it safely.

## 1. Structural Pattern

Most business modules use:

```text
api/
domain/
repo/
service/
```

Typical responsibilities:

- `api`: request / response DTOs and controllers
- `domain`: JPA entities and enums
- `repo`: Spring Data repositories
- `service`: business logic, orchestration, and transactions

## 2. Request Lifecycle

1. `JwtAuthenticationFilter` authenticates the request.
2. `SecurityConfig` and `@PreAuthorize` guard the route.
3. `SocieteContext` carries the current scope.
4. controller validates transport-level input.
5. service enforces business rules.
6. repository executes societe-scoped queries.
7. `RlsContextAspect` sets the database-local societe context.

## 3. High-Value Cross-Cutting Classes

| Class | Responsibility |
| --- | --- |
| `AuthService` | login and societe switching |
| `JwtAuthenticationFilter` | request authentication |
| `UserSecurityCacheService` | revocation cache |
| `SocieteContextHelper` | safe access to active scope |
| `RlsContextAspect` | PostgreSQL RLS context bridge |
| `GlobalExceptionHandler` | standardized API errors |
| `OutboundDispatcherService` | async message dispatch |
| `DocumentGenerationService` | Thymeleaf -> PDF rendering |

## 4. Domain Hotspots

### Identity and access

- `auth`
- `societe`
- `user`
- `usermanagement`

### Sales lifecycle

- `contact`
- `reservation`
- `deposit`
- `vente`
- `contract`
- `payments`

### Supporting operations

- `task`
- `notification`
- `outbox`
- `document`
- `media`
- `audit`
- `gdpr`

## 5. How To Add A Feature

### New route in an existing module

1. add DTOs in `api/dto` when needed
2. add controller method
3. add service logic
4. add or update repository methods
5. verify role and societe scoping
6. add tests
7. update docs

### New aggregate

1. add Liquibase changeset
2. add entity
3. add repository
4. add service
5. add controller
6. add RLS coverage if tenant-scoped

## 6. Service Design Rules

- resolve `societeId` from context, not from client trust
- keep controller thin
- keep cross-module orchestration in service code
- use explicit business exceptions for state errors
- prefer transaction boundaries at the service level

## 7. Security Rules For Backend Changes

- never bypass role or scope checks because “the UI already hides it”
- keep portal and CRM assumptions separate
- remember that `SUPER_ADMIN` can be platform-scoped without a societe ID
- preserve token revocation logic when changing auth or user lifecycle code

## 8. Async And Scheduler Considerations

- outbox work uses DB-backed claiming and retries
- schedulers may operate across societes and must be explicit when doing so
- async tasks require propagated context if they depend on current scope

## 9. Good Files To Read While Learning

- [../../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/SecurityConfig.java](../../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/SecurityConfig.java)
- [../../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/JwtAuthenticationFilter.java](../../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/JwtAuthenticationFilter.java)
- [../../../hlm-backend/src/main/java/com/yem/hlm/backend/societe/RlsContextAspect.java](../../../hlm-backend/src/main/java/com/yem/hlm/backend/societe/RlsContextAspect.java)
- [../../../hlm-backend/src/main/java/com/yem/hlm/backend/contact/api/ContactController.java](../../../hlm-backend/src/main/java/com/yem/hlm/backend/contact/api/ContactController.java)
- [../../../hlm-backend/src/main/java/com/yem/hlm/backend/vente/api/VenteController.java](../../../hlm-backend/src/main/java/com/yem/hlm/backend/vente/api/VenteController.java)

## 10. What Usually Breaks During Refactors

- AOP ordering around RLS
- duplicated security rules between controllers
- session behavior when switching societes
- contract / vente side effects on property status
- implicit assumptions in integration tests
