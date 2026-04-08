# Module 04 — RBAC

## Learning Objectives

- Distinguish platform roles, CRM roles, and portal roles
- Understand how URL rules and method security combine
- Read the effective permission surface for a controller
- Avoid the common Spring Security `ROLE_` mistakes

## The Three Authorization Layers

Authorization in this platform does not live in one place.

It is enforced through:

1. **route-level rules** in [SecurityConfig.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/SecurityConfig.java)
2. **method-level rules** using `@PreAuthorize`
3. **domain-level business checks** inside services

You should think of them as stacked defenses:

- route rules stop obviously wrong traffic early
- method rules protect controller entry points
- service checks enforce business invariants like “last admin cannot be removed”

## Role Families

### Platform role

| Role | Purpose |
| --- | --- |
| `ROLE_SUPER_ADMIN` | platform operations across sociétés |

This role is not just a “stronger admin.” It operates on a different surface: `/api/admin/**`.

### CRM roles

| Role | Purpose |
| --- | --- |
| `ROLE_ADMIN` | full CRM control for one société |
| `ROLE_MANAGER` | create/read/update, but narrower than admin |
| `ROLE_AGENT` | mostly read-oriented CRM usage |

### Portal role

| Role | Purpose |
| --- | --- |
| `ROLE_PORTAL` | buyer-facing read-only access on `/api/portal/**` |

Portal users are not CRM users and are not represented by `UserRole`.

## Where Roles Come From

This is slightly subtle because there are two storage formats.

### In membership records

Société membership roles are stored in short form:

- `ADMIN`
- `MANAGER`
- `AGENT`

### In JWT authorities

Spring Security authorities use the prefixed form:

- `ROLE_ADMIN`
- `ROLE_MANAGER`
- `ROLE_AGENT`
- `ROLE_SUPER_ADMIN`
- `ROLE_PORTAL`

That means code often converts between:

- DB-facing short role names
- JWT-facing `ROLE_*` names

## Spring Security Prefix Rule

Spring Security automatically prefixes `ROLE_` when you use `hasRole(...)`.

Correct:

```java
@PreAuthorize("hasRole('ADMIN')")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
```

Wrong:

```java
@PreAuthorize("hasRole('ROLE_ADMIN')")
```

The wrong form would make Spring look for `ROLE_ROLE_ADMIN`.

## URL-Level Rules

The outer perimeter lives in [SecurityConfig.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/SecurityConfig.java).

Important slices:

| Path | Rule |
| --- | --- |
| `/api/admin/**` | `SUPER_ADMIN` only |
| `/api/portal/auth/**` | public |
| `/api/portal/**` | `PORTAL` only |
| `/api/**` | CRM roles only |

This achieves a key invariant:

- portal sessions cannot call CRM APIs
- CRM sessions cannot call portal-only data APIs
- platform operators use a separate admin namespace

## Method-Level Rules

Once a request reaches a controller, `@PreAuthorize` refines the permission surface further.

Example: [ProjectController.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/project/api/ProjectController.java)

| Endpoint | Effective roles |
| --- | --- |
| `GET /api/projects` | all CRM roles |
| `GET /api/projects/{id}` | all CRM roles |
| `POST /api/projects` | `ADMIN`, `MANAGER` |
| `PUT /api/projects/{id}` | `ADMIN`, `MANAGER` |
| `DELETE /api/projects/{id}` | `ADMIN` |
| `GET /api/projects/{id}/kpis` | `ADMIN`, `MANAGER` |

That is a good pattern to follow:

- class or URL rules establish the broad surface
- method annotations narrow write operations further

## Business Rules Still Matter After RBAC

RBAC answers “is this role allowed to attempt this action?”

It does not answer every business question.

Examples enforced deeper in services:

- a manager may be allowed to update a membership, but not demote the last admin
- a user may be allowed to update a project, but only inside the active société
- a portal user may be allowed to call `/api/portal/contracts`, but only for their own contact-scoped data

That is why removing service checks because “the controller already has `@PreAuthorize`” is a mistake.

## SUPER_ADMIN Is A Separate Surface

This is worth calling out because many systems flatten all admin concepts together.

In this platform:

- `ROLE_ADMIN` is a company-level CRM role
- `ROLE_SUPER_ADMIN` is a platform-level role

Consequences:

- `SUPER_ADMIN` tokens may omit `sid`
- CRM services that require société context should not assume a platform token can call them safely
- platform APIs are intentionally isolated under `/api/admin/**`

## Common Mistakes

### Mistake 1: putting `ROLE_` in `hasRole`

Do not do this.

### Mistake 2: confusing membership role storage with JWT role storage

DB membership rows may store `ADMIN`, but JWTs and `GrantedAuthority` use `ROLE_ADMIN`.

### Mistake 3: assuming route rules are enough

They are not. Use method security and service-level invariants too.

### Mistake 4: treating portal as “just another CRM role”

Portal is a different auth model, a different principal type, and a different API surface.

## Source Files To Study

| File | Why it matters |
| --- | --- |
| [SecurityConfig.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/SecurityConfig.java) | route-level rules |
| [UserRole.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/user/domain/UserRole.java) | CRM role enum |
| [ProjectController.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/project/api/ProjectController.java) | good controller-level rule split |
| [SocieteService.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/societe/SocieteService.java) | business-rule enforcement beyond pure RBAC |
| [PortalContractService.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/portal/service/PortalContractService.java) | portal role plus data scoping |

## Exercises

### Exercise 1 — Build an endpoint matrix

1. Open [ProjectController.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/project/api/ProjectController.java).
2. List each endpoint.
3. Combine the route-level `/api/**` rule with each method-level annotation.
4. Write the effective allowed-role matrix.

### Exercise 2 — Find a business-rule layer

1. Open [SocieteService.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/societe/SocieteService.java).
2. Find the “last admin” protection logic.
3. Explain why RBAC alone cannot encode that rule.

### Exercise 3 — Compare CRM and portal authorization

1. Trace one CRM endpoint and one portal endpoint from `SecurityConfig`.
2. Explain how the platform ensures they cannot be used interchangeably.
