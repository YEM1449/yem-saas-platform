# Société Management Refactoring — Claude Code Execution Plan

## Context for Claude Code

You are working on `yem-saas-platform`, a multi-company real-estate CRM SaaS. Branch: `Epic/users-management`.

The société (company) management module has 10 functional problems identified by audit. You must fix all of them in the order specified below. Follow existing codebase patterns exactly.

**Critical rules:**
- Base package: `com.yem.hlm.backend`
- Next Liquibase changeset number: **051**
- Never edit applied changesets (001-050). Only create new ones.
- Use existing `ErrorCode` enum values when they exist. Add new ones only if needed.
- Use `SocieteContextHelper` (already exists in `societe/`) for société context access.
- Use `BusinessRuleException` (already exists in `usermanagement/exception/`) for business rule violations.
- Run tests after each phase. If tests fail, fix before proceeding.
- All text in French for user-facing strings (error messages, labels). Code and comments in English.

---

## Phase 1 — Quota Enforcement (Problem 2)

### What's broken
`ContactService.create()`, `PropertyService.create()`, and `ProjectService.create()` do not check société quotas (`maxContacts`, `maxBiens`, `maxProjets`). Only user count is enforced in `SocieteService.addMembre()`.

### What to do

**Step 1.1:** Create `hlm-backend/src/main/java/com/yem/hlm/backend/societe/QuotaEnforcementService.java`:

```java
package com.yem.hlm.backend.societe;

@Service
public class QuotaEnforcementService {
    // Inject: SocieteRepository, ContactRepository, PropertyRepository, ProjectRepository

    /** Call before creating a contact. Throws BusinessRuleException(QUOTA_CONTACTS_ATTEINT) if over limit. */
    public void checkContactQuota(UUID societeId) { ... }

    /** Call before creating a property. Throws BusinessRuleException(QUOTA_BIENS_ATTEINT) if over limit. */
    public void checkPropertyQuota(UUID societeId) { ... }

    /** Call before creating a project. Throws BusinessRuleException(QUOTA_PROJETS_ATTEINT) if over limit. */
    public void checkProjectQuota(UUID societeId) { ... }
}
```

Implementation pattern for each method:
1. Load the Societe entity
2. If the relevant `max*` field is null, return (no quota = unlimited)
3. Count current entities: use existing repository count methods like `contactRepository.countBySocieteIdAndDeletedFalse(societeId)`
4. For PropertyRepository, add `countBySocieteIdAndDeletedAtIsNull(UUID societeId)` if it doesn't exist
5. For ProjectRepository, add `countBySocieteId(UUID societeId)` if it doesn't exist
6. If count >= max, throw `BusinessRuleException` with the corresponding `ErrorCode`

**Step 1.2:** Inject `QuotaEnforcementService` into `ContactService`, `PropertyService`, and `ProjectService`. Call the check at the start of their `create()` methods, right after `requireSocieteId()`:

In `ContactService.create()`:
```java
UUID societeId = requireSocieteId();
quotaEnforcementService.checkContactQuota(societeId); // ADD THIS LINE
```

In `PropertyService.create()` (or whichever method creates properties):
```java
UUID societeId = requireSocieteId();
quotaEnforcementService.checkPropertyQuota(societeId); // ADD THIS LINE
```

In `ProjectService.create()`:
```java
UUID societeId = requireSocieteId();
quotaEnforcementService.checkProjectQuota(societeId); // ADD THIS LINE
```

**Step 1.3:** Write unit test `QuotaEnforcementServiceTest.java`:
- Test that no exception is thrown when quota is null (unlimited)
- Test that no exception is thrown when count < max
- Test that `BusinessRuleException` with correct `ErrorCode` is thrown when count >= max

**Step 1.4:** Run: `cd hlm-backend && ./mvnw test -Dtest=QuotaEnforcementServiceTest`

---

## Phase 2 — Impersonation Safety (Problem 4)

### What's broken
Impersonation overwrites the SUPER_ADMIN token, has no return path, no visual indicator, and actions are audited as the target user.

### What to do

**Step 2.1:** Modify `hlm-frontend/src/app/features/superadmin/societes/societe-members.component.ts`:

In the `impersonate()` method, BEFORE overwriting localStorage:
```typescript
// Save original super admin token for return
const currentToken = localStorage.getItem('hlm_access_token');
if (currentToken) {
  localStorage.setItem('hlm_superadmin_original_token', currentToken);
}
// Set impersonation flag
localStorage.setItem('hlm_impersonation_active', 'true');
localStorage.setItem('hlm_impersonation_target', res.targetUserEmail);
localStorage.setItem('hlm_impersonation_societe', this.societeId);

// Then set the impersonation token as the active token
localStorage.setItem('hlm_access_token', res.token);
```

**Step 2.2:** Add an impersonation banner to `hlm-frontend/src/app/features/shell/shell.component.ts`:

Add to the component class:
```typescript
get isImpersonating(): boolean {
  return localStorage.getItem('hlm_impersonation_active') === 'true';
}
get impersonationTarget(): string {
  return localStorage.getItem('hlm_impersonation_target') ?? '';
}
endImpersonation(): void {
  const originalToken = localStorage.getItem('hlm_superadmin_original_token');
  if (originalToken) {
    localStorage.setItem('hlm_access_token', originalToken);
  }
  localStorage.removeItem('hlm_impersonation_active');
  localStorage.removeItem('hlm_impersonation_target');
  localStorage.removeItem('hlm_impersonation_societe');
  localStorage.removeItem('hlm_superadmin_original_token');
  this.router.navigateByUrl('/superadmin/societes');
}
```

Add to the template (at the very top, before the nav):
```html
@if (isImpersonating) {
  <div class="impersonation-banner">
    ⚠ Vous usurpez l'identité de <strong>{{ impersonationTarget }}</strong>
    <button (click)="endImpersonation()">Revenir au SuperAdmin</button>
  </div>
}
```

Add CSS for the banner (bright orange/red background, fixed top, z-index above everything):
```css
.impersonation-banner {
  background: #e74c3c; color: white; padding: 8px 16px;
  text-align: center; font-size: 14px; position: sticky; top: 0; z-index: 9999;
}
.impersonation-banner button {
  margin-left: 16px; background: white; color: #e74c3c;
  border: none; padding: 4px 12px; border-radius: 4px; cursor: pointer; font-weight: 600;
}
```

**Step 2.3:** Backend audit enrichment. In `JwtAuthenticationFilter.doFilterInternal()`, after setting the authentication, check for the `imp` claim and store it in `SocieteContext` or MDC for logging:

Read the JWT, check if `jwtProvider.extractClaim(token, "imp")` returns a non-null UUID. If so, add it to the MDC logging context:
```java
UUID impersonator = safeExtractImpersonator(token);
if (impersonator != null) {
    org.slf4j.MDC.put("impersonator", impersonator.toString());
}
```

Ensure `AuditEventListener` reads MDC `impersonator` and includes it in audit records if present.

**Step 2.4:** Run: `cd hlm-frontend && npm run build -- --configuration=production`

---

## Phase 3 — Deactivation Recovery (Problem 8)

### What's broken
Reactivating a société does NOT restore member `actif` flags.

### What to do

**Step 3.1:** In `SocieteService.reactiver()`, after `s.setActif(true)`:

```java
// Restore all memberships that were deactivated during the suspension
// (identified by dateRetrait matching the suspension date ± a small window)
appUserSocieteRepository.findByIdSocieteId(id).forEach(aus -> {
    // Only restore members whose removal was part of the deactivation (not individually removed before)
    if (!aus.isActif() && aus.getDateRetrait() != null 
        && s.getDateSuspension() != null 
        && !aus.getDateRetrait().isBefore(s.getDateSuspension().minusSeconds(60))) {
        aus.setActif(true);
        aus.setDateRetrait(null);
        appUserSocieteRepository.save(aus);
    }
});
```

Then clear the suspension metadata:
```java
s.setDateSuspension(null);
s.setRaisonSuspension(null);
```

**Step 3.2:** Add integration test in `SocieteControllerIT.java`: deactivate a société → verify members are inactive → reactivate → verify members are active again.

**Step 3.3:** Run: `cd hlm-backend && ./mvnw test -Dtest=SocieteControllerIT`

---

## Phase 4 — Split SocieteService (Problem 5)

### What's broken
501-line service with 5 responsibilities and 9 injected dependencies.

### What to do

**Step 4.1:** Create `hlm-backend/src/main/java/com/yem/hlm/backend/societe/SocieteMemberService.java`:

Extract these methods from `SocieteService`:
- `listMembres(UUID societeId)` 
- `addMembre(UUID societeId, AddMembreRequest req, UUID actorId)`
- `updateMembreRole(UUID societeId, UUID userId, UpdateMembreRoleRequest req, UUID actorId)`
- `removeMembre(UUID societeId, UUID userId, UUID actorId)`
- `impersonate(UUID societeId, UUID targetUserId, UUID superAdminId)`

Also move the deprecated compatibility methods:
- `addUserToSociete(UUID societeId, AddUserRequest req)` 
- `removeUserFromSociete(UUID societeId, UUID userId)`

Inject: `SocieteRepository` (for `require()`), `AppUserSocieteRepository`, `UserRepository`, `JwtProvider`, `ApplicationEventPublisher`.

Keep the private `require(UUID id)` helper — either duplicate it or extract to a shared `SocieteResolver` utility.

**Step 4.2:** Create `hlm-backend/src/main/java/com/yem/hlm/backend/societe/SocieteStatsService.java`:

Extract:
- `getStats(UUID id)`
- `getCompliance(UUID id)`

Inject: `SocieteRepository`, `AppUserSocieteRepository`, `ContactRepository`, `PropertyRepository`, `ProjectRepository`, `SaleContractRepository`.

**Step 4.3:** Update `SocieteController.java`:

Replace `SocieteService` injections for member and stats methods:
- Member endpoints → inject `SocieteMemberService`
- Stats/compliance endpoints → inject `SocieteStatsService`
- CRUD/lifecycle endpoints → keep `SocieteService`

**Step 4.4:** Update `SocieteService.java`:

Remove the extracted methods. Remove now-unused repository injections (`ContactRepository`, `PropertyRepository`, `ProjectRepository`, `SaleContractRepository`, `JwtProvider`). The service should shrink from ~500 lines to ~200 lines and have only 3 dependencies: `SocieteRepository`, `AppUserSocieteRepository`, `UserRepository`, `ApplicationEventPublisher`.

**Step 4.5:** Run ALL tests: `cd hlm-backend && ./mvnw test`

---

## Phase 5 — Compliance Validation (Problem 3)

### What's broken
Compliance scoring checks `field != null` with no format validation.

### What to do

**Step 5.1:** In `SocieteStatsService.getCompliance()` (after Phase 4 extraction), replace the simple null checks with proper validation:

```java
boolean hasValidEmailDpo = s.getEmailDpo() != null 
    && s.getEmailDpo().matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");

boolean hasValidDpoNom = s.getDpoNom() != null 
    && s.getDpoNom().trim().length() >= 2;

boolean hasValidAdresse = (s.getAdresseSiege() != null && s.getAdresseSiege().trim().length() >= 5)
    || (s.getAdresse() != null && s.getAdresse().trim().length() >= 5);

boolean hasValidRegistre = (s.getNumeroCndp() != null && s.getNumeroCndp().trim().length() >= 3)
    || (s.getNumeroCnil() != null && s.getNumeroCnil().trim().length() >= 3);

boolean hasValidBaseJuridique = s.getBaseJuridiqueDefaut() != null
    && VALID_LEGAL_BASES.contains(s.getBaseJuridiqueDefaut().trim().toUpperCase());
```

Define valid legal bases matching RGPD Article 6 / Loi 09-08:
```java
private static final Set<String> VALID_LEGAL_BASES = Set.of(
    "CONSENTEMENT", "CONTRAT", "OBLIGATION_LEGALE", 
    "INTERET_VITAL", "INTERET_PUBLIC", "INTERET_LEGITIME"
);
```

**Step 5.2:** Update the `SocieteComplianceDto` to return validation-specific messages in `missingFields`:
- Instead of `"emailDpo"` → `"Email DPO manquant ou format invalide"`
- Instead of `"baseJuridiqueDefaut"` → `"Base juridique invalide (valeurs: CONSENTEMENT, CONTRAT, OBLIGATION_LEGALE, INTERET_VITAL, INTERET_PUBLIC, INTERET_LEGITIME)"`

**Step 5.3:** Run: `cd hlm-backend && ./mvnw test -Dtest=SocieteControllerIT`

---

## Phase 6 — Expand the Form (Problems 9 + 10)

### What's broken
Frontend form exposes only 13 of 62 entity fields. Country list is hardcoded to 5 options.

### What to do

**Step 6.1:** Rewrite `hlm-frontend/src/app/features/superadmin/societes/societe-form.component.ts`:

Add ALL missing form fields grouped into collapsible sections. The form should have these sections (collapsed by default, expandable):

1. **Identité** (expanded by default): nom*, pays*, nomCommercial, formeJuridique, capitalSocial, siretIce, rc, ifNumber, patente, tvaNumber, cnssNumber
2. **Localisation** (collapsed): adresseSiege, ville, codePostal, region, telephone, telephone2, emailContact, siteWeb, linkedinUrl
3. **RGPD / Conformité** (collapsed): emailDpo, dpoNom, telephoneDpo, numeroCndp, numeroCnil, dateDeclarationCndp, dateDeclarationCnil, baseJuridiqueDefaut (dropdown with 6 legal bases), dureeRetentionJours
4. **Licence / Agrément** (collapsed): numeroAgrement, typeActivite, carteProfessionnelle, caisseGarantie, assuranceRc, dateAgrement, dateExpirationAgrement, zonesIntervention
5. **Marque** (collapsed): logoUrl, couleurPrimaire, couleurSecondaire, langueDefaut, devise, fuseauHoraire, formatDate, mentionsLegales
6. **Abonnement** (collapsed): planAbonnement, maxUtilisateurs, maxBiens, maxContacts, maxProjets, dateDebutAbonnement, dateFinAbonnement, periodeEssai
7. **Notes internes** (collapsed): notesInternes (textarea)

Use a simple collapsible pattern:
```typescript
sectionOpen: Record<string, boolean> = { identite: true };
toggleSection(s: string) { this.sectionOpen[s] = !this.sectionOpen[s]; }
```
```html
<section class="form-section">
  <h3 class="section-title" (click)="toggleSection('localisation')">
    Localisation {{ sectionOpen['localisation'] ? '▾' : '▸' }}
  </h3>
  @if (sectionOpen['localisation']) {
    <!-- fields here -->
  }
</section>
```

**Step 6.2:** Replace the hardcoded country `<select>` with a comprehensive list. Create a constant array of common African and European countries (at minimum: MA, FR, TN, DZ, SN, CI, CM, GA, MR, ML, BF, NE, TG, BJ, GN, CG, CD, MG, EG, AE, SA, BE, CH, ES, PT, DE, IT, GB, NL, CA, US). Use `{ code: 'MA', name: 'Maroc' }` format and sort by name.

**Step 6.3:** Add the `baseJuridiqueDefaut` dropdown with the 6 valid legal bases:
```html
<select [(ngModel)]="baseJuridiqueDefaut" name="baseJuridiqueDefaut">
  <option value="">-- Sélectionner --</option>
  <option value="CONSENTEMENT">Consentement</option>
  <option value="CONTRAT">Exécution d'un contrat</option>
  <option value="OBLIGATION_LEGALE">Obligation légale</option>
  <option value="INTERET_VITAL">Intérêt vital</option>
  <option value="INTERET_PUBLIC">Intérêt public</option>
  <option value="INTERET_LEGITIME">Intérêt légitime</option>
</select>
```

**Step 6.4:** Update the `UpdateSocieteRequest` interface in `societe.model.ts` to include ALL fields.

**Step 6.5:** Update the `submit()` method to send all fields to the API.

**Step 6.6:** Run: `cd hlm-frontend && npm run build -- --configuration=production`

---

## Phase 7 — Unify Member Management (Problem 6)

### What's broken
Two parallel systems (`SocieteController` for SUPER_ADMIN and `UserManagementController` for société ADMIN) manage members with overlapping logic.

### What to do

**Step 7.1:** Create `hlm-backend/src/main/java/com/yem/hlm/backend/societe/MemberOperationService.java`:

This is the single source of truth for member operations. Both controllers delegate to it:

```java
@Service
public class MemberOperationService {
    /**
     * @param callerRole "SUPER_ADMIN" or the caller's société role
     * @param canAssignAdmin true only for SUPER_ADMIN callers
     */
    public MembreSocieteDto addMember(UUID societeId, UUID userId, String role, 
                                       UUID actorId, boolean canAssignAdmin) {
        if ("ADMIN".equals(role) && !canAssignAdmin) {
            throw new BusinessRuleException(ROLE_ESCALATION_FORBIDDEN, "...");
        }
        // shared logic: quota check, duplicate check, save, token version bump, event
    }

    public MembreSocieteDto changeRole(UUID societeId, UUID userId, String newRole,
                                        UUID actorId, boolean canAssignAdmin) {
        // shared logic: last-admin protection, role validation, save, token bump, event
    }

    public void removeMember(UUID societeId, UUID userId, UUID actorId) {
        // shared logic: last-admin protection, soft-delete, token bump, event
    }
}
```

**Step 7.2:** Refactor `SocieteMemberService` (from Phase 4) to delegate to `MemberOperationService` with `canAssignAdmin=true`.

**Step 7.3:** Refactor `UserManagementService` to delegate to `MemberOperationService` with `canAssignAdmin=false`.

**Step 7.4:** Run: `cd hlm-backend && ./mvnw test`

---

## Phase 8 — Onboarding Wizard (Problem 7)

### What to do

**Step 8.1:** Create `hlm-frontend/src/app/features/superadmin/societes/societe-onboarding.component.ts`:

A 3-step wizard shown after société creation:

```
Step 1: Société created ✓ (summary of what was just created)
Step 2: Invite first admin (email, prenom, nom, role=ADMIN pre-selected)
Step 3: Done — show activation link and next steps
```

**Step 8.2:** In `societe-form.component.ts`, after successful creation, navigate to the onboarding component instead of the detail page:

```typescript
this.router.navigate(['/superadmin/societes', res.id, 'onboarding']);
```

**Step 8.3:** Add route in `app.routes.ts`:
```typescript
{ path: 'societes/:id/onboarding', loadComponent: () => 
  import('./features/superadmin/societes/societe-onboarding.component')
    .then(m => m.SocieteOnboardingComponent) }
```

**Step 8.4:** Run: `cd hlm-frontend && npm run build -- --configuration=production`

---

## Verification

After all 8 phases, run the full verification:

```bash
# Backend
cd hlm-backend && ./mvnw verify

# Frontend
cd hlm-frontend && npm run build -- --configuration=production

# Specific tests
cd hlm-backend && ./mvnw test -Dtest=QuotaEnforcementServiceTest
cd hlm-backend && ./mvnw test -Dtest=SocieteControllerIT
cd hlm-backend && ./mvnw test -Dtest=CrossSocieteIsolationIT
cd hlm-backend && ./mvnw test -Dtest=UserManagementIsolationIT
cd hlm-backend && ./mvnw test -Dtest=RbacPrivilegeEscalationIT
```

All tests must pass. If any test fails, fix it before considering the phase complete.
