# ADR 002 — Company ADMIN Cannot Assign the ADMIN Role

**Date:** 2026-03-22
**Authors:** YEM Platform Team

---

## Status

Accepted

---

## Context

The platform has four roles arranged in two tiers:

- **Platform tier:** `ROLE_SUPER_ADMIN` — manages all companies, is created via bootstrap.
- **Company tier:** `ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_AGENT` — scoped to a single société.

Company-level ADMINs have broad powers within their société: they can invite users, change roles, remove members, and perform GDPR operations. A natural question arises: can an ADMIN invite or promote another user to the ADMIN role?

Allowing this would create a privilege escalation path. A malicious or compromised ADMIN could create unlimited ADMIN accounts, bypassing any quota or oversight the platform operator has configured. It would also undermine the invariant that "who holds the ADMIN role in a company" is something the SUPER_ADMIN can always audit and control.

At the same time, SUPER_ADMINs must be able to assign ADMIN roles — for example, when onboarding a new company and designating its first administrator.

---

## Decision

**A company-level ADMIN cannot assign the `ADMIN` role.** Attempting to do so returns `403 ROLE_ESCALATION_FORBIDDEN`.

This rule is enforced in two layers:

1. **Controller layer** (`UserManagementController`): `SocieteRoleValidator.validateAssignableRole(req.role())` is called before the service method. If the caller is not a SUPER_ADMIN and the target role is `ADMIN`, the validator throws `BusinessRuleException(ROLE_ESCALATION_FORBIDDEN)`.

2. **Service layer** (`UserManagementService.changerRole()`): The same `validateAssignableRole()` call is made again inside the service. This is intentional defense-in-depth: if someone calls the service directly (e.g., in a future internal workflow), the invariant is still enforced.

The check in `SocieteRoleValidator` reads the caller's context from `SocieteContext.isSuperAdmin()` — it does not trust any value from the request body or path parameter. SUPER_ADMIN tokens set `SocieteContext.setSuperAdmin(true)` in `JwtAuthenticationFilter`; company-tier tokens do not.

Valid roles that a company ADMIN can assign: `MANAGER`, `AGENT`.

Error response shape:

```json
{
  "status": 403,
  "code": "ROLE_ESCALATION_FORBIDDEN",
  "message": "Seul un SUPER_ADMIN peut attribuer le rôle ADMIN. Contactez l'administrateur de la plateforme."
}
```

---

## Consequences

**Positive:**
- No ADMIN can silently create another ADMIN, limiting the blast radius of a compromised ADMIN account.
- The SUPER_ADMIN tier retains exclusive control over who holds the ADMIN role, enabling platform-level governance.
- The dual-layer check (controller + service) prevents the rule from being bypassed by future internal callers.

**Negative:**
- If a company needs to add a new ADMIN (e.g., the original ADMIN leaves), a SUPER_ADMIN must perform this action. This introduces a dependency on platform support for a relatively common company-level operation.
- The two-layer check adds a small amount of code duplication. This is accepted as a deliberate security pattern.

---

## Alternatives Considered

**Allow ADMINs to assign ADMIN role, but require confirmation:** Rejected. It does not address the audit and governance concern — a determined bad actor would confirm.

**Allow ADMINs to nominate ADMIN candidates, with SUPER_ADMIN approval:** Not implemented due to complexity. Could be revisited if the platform-support overhead becomes a real operational pain point.

**Enforce only at the API layer (no service-layer check):** Rejected. Defense-in-depth is standard practice for privilege enforcement. A future programmatic caller should not be able to bypass the rule by calling the service directly.
