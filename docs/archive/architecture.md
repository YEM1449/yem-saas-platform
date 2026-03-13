# Architecture (Legacy Alias)

This file remains as a short alias. Canonical architecture docs are:
- [01_ARCHITECTURE.md](01_ARCHITECTURE.md)
- [../context/ARCHITECTURE.md](../context/ARCHITECTURE.md)

## Core Architectural Rules
- Tenant isolation is enforced from JWT `tid` through `TenantContext` into tenant-scoped queries.
- Security routing separates CRM (`/api/**`) from portal (`/api/portal/**`) access.
- Controllers expose DTOs only; business rules live in services.
- Schema evolution is Liquibase-only with additive changesets.

## Related References
- Security model: [security.md](security.md)
- Backend package details: [backend.md](backend.md)
- API contract map: [api.md](api.md)
