# Module 01 — Multi-Tenancy

> Legacy learning module
>
> This course module explains an earlier `tenant`-based design. The implemented code now uses
> `societe`, `app_user_societe`, `sid`, and `SocieteContext`.
> Read [../context/ARCHITECTURE.md](../context/ARCHITECTURE.md) and
> [../context/DATA_MODEL.md](../context/DATA_MODEL.md) for the current model.

## Learning Objectives

- Understand the shared-database, separate-schema-by-column tenancy model
- Explain how `TenantContext` isolates data per tenant
- Identify where tenant isolation would break and how the codebase prevents it

---

## Concept

The platform serves multiple real estate agencies from a single database. Each agency is a **tenant**. All data is stored in shared tables, and every row carries a `tenant_id` foreign key that identifies which agency owns it.

This is called the **shared-database, shared-schema** model. It is simpler to operate than separate schemas or databases, but requires strict discipline: every query must filter by `tenant_id`.

---

## The Tenant Table

```sql
CREATE TABLE tenant (
    id   UUID PRIMARY KEY,
    key  VARCHAR(100) UNIQUE NOT NULL,  -- e.g., "acme"
    name VARCHAR(200) NOT NULL
);
```

Every domain entity table has:
```sql
tenant_id UUID NOT NULL REFERENCES tenant(id)
```

---

## TenantContext

`TenantContext` (`tenant/context/TenantContext.java`) is a static utility backed by two `ThreadLocal<UUID>` fields:
- `TENANT_ID` — populated from the JWT `tid` claim
- `USER_ID` — populated from the JWT `sub` claim

In Spring's synchronous servlet model, one thread handles one request. ThreadLocal gives zero-overhead request-scoped storage without Spring bean injection.

```java
// In JwtAuthenticationFilter (after JWT validation):
TenantContext.set(tenantId, userId);

// In every service method:
UUID tenantId = TenantContext.getTenantId();
return repo.findByTenantIdAndId(tenantId, id)
    .orElseThrow(NotFoundException::new);

// In JwtAuthenticationFilter finally block:
TenantContext.clear();
```

---

## Why Not Trust the Request Body

Services never read `tenantId` from the URL path or request body. A malicious client could send `"tenantId": "someone-elses-tenant"` in a request body. By reading only from `TenantContext` (populated from the cryptographically signed JWT), cross-tenant access is structurally impossible.

---

## Source Files

| File | Purpose |
|------|---------|
| `tenant/context/TenantContext.java` | ThreadLocal holder |
| `tenant/domain/Tenant.java` | JPA entity |
| `auth/security/JwtAuthenticationFilter.java` | Sets and clears TenantContext |
| `contact/service/ContactService.java` | Example of correct usage |

---

## Exercise

1. Open `ContactService.java` and find the `get(UUID id)` method.
2. Confirm it calls `TenantContext.getTenantId()` and passes the result to `contactRepo.findByTenantIdAndId(...)`.
3. Now try to find any service that reads `tenantId` from a method parameter passed in from a controller. You should not find any.
