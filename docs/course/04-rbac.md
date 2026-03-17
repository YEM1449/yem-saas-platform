# Module 04 — RBAC

## Learning Objectives

- List the four roles and their permission levels
- Correctly use `@PreAuthorize` with `hasRole` and `hasAnyRole`
- Avoid the common `ROLE_` prefix mistake

---

## Roles

| Role | Defined in | Purpose |
|------|-----------|---------|
| `ROLE_ADMIN` | `UserRole.ROLE_ADMIN` | Full CRUD on all resources; user management; GDPR erasure |
| `ROLE_MANAGER` | `UserRole.ROLE_MANAGER` | Create, read, update; no delete; no user management |
| `ROLE_AGENT` | `UserRole.ROLE_AGENT` | Read-only; can create contracts for own sales |
| `ROLE_PORTAL` | JWT roles claim (not a `UserRole`) | Client portal access; read-only own contracts |

---

## How Roles Flow

1. `UserRole` enum values are stored in `app_user.role` column as strings (`ROLE_ADMIN`, etc.).
2. `JwtProvider` writes `user.getRole().name()` into the `roles` claim.
3. `JwtAuthenticationFilter` reads the `roles` claim and creates `SimpleGrantedAuthority("ROLE_ADMIN")`.
4. Spring Security uses these authorities for `hasRole()` and `hasAnyRole()` checks.

---

## @PreAuthorize Conventions

Spring Security's `hasRole()` automatically prepends `ROLE_`. So:

```java
// Correct:
@PreAuthorize("hasRole('ADMIN')")          // matches ROLE_ADMIN
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")

// Wrong — Spring adds ROLE_ twice, matching ROLE_ROLE_ADMIN which never exists:
@PreAuthorize("hasRole('ROLE_ADMIN')")
```

---

## Controller Pattern

Class-level annotation sets the minimum role. Method-level overrides tighten it:

```java
@RestController
@RequestMapping("/api/properties")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','AGENT')")  // class-level: any CRM user can read
public class PropertyController {

    @GetMapping
    public Page<PropertyResponse> list(...) { ... }  // inherits class-level

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")  // tighter: only admin/manager can create
    @ResponseStatus(HttpStatus.CREATED)
    public PropertyResponse create(...) { ... }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")  // tightest: only admin can delete
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(...) { ... }
}
```

---

## URL-Level Rules in SecurityConfig

`SecurityConfig.securityFilterChain()` has URL rules as the last line of defence:

```
/api/portal/** → hasRole('PORTAL')
/api/**        → hasAnyRole('ADMIN','MANAGER','AGENT')
anyRequest     → authenticated
```

These rules catch requests to endpoints that lack `@PreAuthorize`. Method-level `@PreAuthorize` is the primary enforcement mechanism.

---

## Source Files

| File | Purpose |
|------|---------|
| `user/domain/UserRole.java` | Role enum |
| `auth/security/SecurityConfig.java` | URL-level security rules |
| `property/api/PropertyController.java` | Example of layered @PreAuthorize |
| `gdpr/api/GdprController.java` | Example of ADMIN-only operations |

---

## Exercise

1. Open `PropertyController.java`.
2. List every `@PreAuthorize` annotation at both class and method level.
3. Build the effective permission matrix: for each HTTP method + path, which roles are allowed?
4. Write a unit test that calls `DELETE /api/properties/{id}` with a MANAGER token and asserts 403.
