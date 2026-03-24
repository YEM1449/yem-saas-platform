# PR Fix: Critical Bugs Found During Code Review

## Context
Branch: `Epic/users-management` vs `main`. You are fixing bugs found during professional code review before this PR can merge. These are real bugs — not style issues, not nice-to-haves. Users will hit these failures in production.

## Rules
- Fix each bug in the exact order listed. Each fix is independent — commit after each one.
- Run the specified verification after each fix.
- Do NOT refactor unrelated code. Minimum viable fix for each bug.
- Next Liquibase changeset: 051. Don't touch existing changesets 001-050.

---

## BUG 1 — CRITICAL: Multi-société login is completely broken on frontend

### Root cause
When a user belongs to multiple sociétés, the backend `AuthService.login()` returns:
```json
{
  "accessToken": "<partial-token>",
  "tokenType": "Partial",
  "expiresIn": 300,
  "requiresSocieteSelection": true,
  "societes": [{"id": "uuid-1", "nom": "Société Alpha"}, {"id": "uuid-2", "nom": "Société Beta"}]
}
```

The frontend `LoginComponent.onSubmit()` does NOT check `requiresSocieteSelection`. It always:
1. Stores the partial token as the access token
2. Calls `/auth/me` which FAILS because partial tokens are rejected by `JwtAuthenticationFilter`
3. Falls through to error handler

The frontend `LoginResponse` TypeScript model is also missing `requiresSocieteSelection` and `societes` fields.

### What to fix

**File 1: `hlm-frontend/src/app/core/models/login.model.ts`**
Add the missing fields to `LoginResponse`:
```typescript
export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  requiresSocieteSelection?: boolean;  // ADD
  societes?: SocieteChoice[];           // ADD
}

export interface SocieteChoice {         // ADD
  id: string;
  nom: string;
}
```

**File 2: `hlm-frontend/src/app/core/auth/auth.service.ts`**
The `login()` method must NOT store the token when it's a partial token. Change:
```typescript
login(req: LoginRequest): Observable<LoginResponse> {
  return this.http
    .post<LoginResponse>(`${environment.apiUrl}/auth/login`, req)
    .pipe(tap((res) => {
      // Only store token if this is a full auth (not a société selection prompt)
      if (!res.requiresSocieteSelection) {
        localStorage.setItem(TOKEN_KEY, res.accessToken);
      }
    }));
}
```

Add a new `switchSociete()` method:
```typescript
switchSociete(partialToken: string, societeId: string): Observable<LoginResponse> {
  return this.http
    .post<LoginResponse>(`${environment.apiUrl}/auth/switch-societe`,
      { societeId },
      { headers: { Authorization: `Bearer ${partialToken}` } }
    )
    .pipe(tap((res) => localStorage.setItem(TOKEN_KEY, res.accessToken)));
}
```

**File 3: `hlm-frontend/src/app/features/login/login.component.ts`**
Rewrite `onSubmit()` to handle both cases:
```typescript
// Add state for société selection
showSocieteSelection = false;
societes: SocieteChoice[] = [];
partialToken = '';

onSubmit(): void {
  this.loading = true;
  this.error = '';

  this.auth.login(this.form).subscribe({
    next: (res) => {
      if (res.requiresSocieteSelection && res.societes) {
        // Multi-société: show picker
        this.loading = false;
        this.showSocieteSelection = true;
        this.societes = res.societes;
        this.partialToken = res.accessToken;
      } else {
        // Single société: proceed to /auth/me and navigate
        this.resolvePostLogin();
      }
    },
    error: (err) => this.handleError(err),
  });
}

selectSociete(societeId: string): void {
  this.loading = true;
  this.error = '';
  this.auth.switchSociete(this.partialToken, societeId).subscribe({
    next: () => this.resolvePostLogin(),
    error: (err) => this.handleError(err),
  });
}

private resolvePostLogin(): void {
  this.auth.me().subscribe({
    next: (user) => {
      if (user.role === 'ROLE_SUPER_ADMIN' || user.platformRole === 'SUPER_ADMIN') {
        this.router.navigateByUrl('/superadmin/societes');
      } else {
        this.router.navigateByUrl('/app/properties');
      }
    },
    error: () => this.router.navigateByUrl('/app/properties'),
  });
}
```

**File 4: `hlm-frontend/src/app/features/login/login.component.html`**
Add the société picker UI after the form:
```html
@if (showSocieteSelection) {
  <div class="societe-selection">
    <h2>Choisir une société</h2>
    <p>Votre compte est associé à plusieurs sociétés. Sélectionnez celle à utiliser :</p>
    @for (s of societes; track s.id) {
      <button class="societe-choice" (click)="selectSociete(s.id)" [disabled]="loading">
        {{ s.nom }}
      </button>
    }
  </div>
}
```

Add imports: `import { SocieteChoice } from '../../core/models/login.model';`

### Verify
```bash
cd hlm-frontend && npm run build -- --configuration=production
```
The build must succeed. Then manually test: create a user with memberships in 2 sociétés, login, and verify the picker appears.

---

## BUG 2 — CRITICAL: Impersonation token stored under wrong key

### Root cause
`societe-members.component.ts` stores the impersonation token as:
```typescript
const IMPERSONATION_TOKEN_KEY = 'hlm_superadmin_impersonation_token';
localStorage.setItem(IMPERSONATION_TOKEN_KEY, res.token);
```

But `AuthService.token` reads from `'hlm_access_token'` and the auth interceptor uses `auth.token`. The impersonated token is NEVER sent to the backend. The SUPER_ADMIN navigates to `/app/properties` but all API calls use the original SUPER_ADMIN token (which has no société scope) → every request fails or returns wrong data.

### What to fix

**File: `hlm-frontend/src/app/features/superadmin/societes/societe-members.component.ts`**

Replace the `impersonate()` method:
```typescript
impersonate(m: MembreSocieteDto): void {
  if (!confirm(`Usurper l'identité de ${m.email ?? m.userId} ? Vous pourrez revenir via le bandeau.`)) return;
  this.impersonating[m.userId] = true;
  this.error = '';
  this.svc.impersonate(this.societeId, m.userId).subscribe({
    next: (res) => {
      this.impersonating[m.userId] = false;
      // Save original token for return
      const currentToken = localStorage.getItem('hlm_access_token');
      if (currentToken) {
        localStorage.setItem('hlm_superadmin_original_token', currentToken);
      }
      // Set impersonation token as THE active token
      localStorage.setItem('hlm_access_token', res.token);
      localStorage.setItem('hlm_impersonation_active', 'true');
      localStorage.setItem('hlm_impersonation_target', res.targetUserEmail);
      // Navigate to CRM
      this.router.navigateByUrl('/app/properties');
    },
    error: (err: HttpErrorResponse) => {
      this.impersonating[m.userId] = false;
      this.error = this.extractError(err);
    },
  });
}
```

Remove the `IMPERSONATION_TOKEN_KEY` constant (no longer needed).

**File: `hlm-frontend/src/app/features/shell/shell.component.ts`**

Add impersonation banner support. Add to the component class:
```typescript
get isImpersonating(): boolean {
  return localStorage.getItem('hlm_impersonation_active') === 'true';
}
get impersonationTarget(): string {
  return localStorage.getItem('hlm_impersonation_target') ?? '';
}
endImpersonation(): void {
  const original = localStorage.getItem('hlm_superadmin_original_token');
  if (original) {
    localStorage.setItem('hlm_access_token', original);
  }
  localStorage.removeItem('hlm_impersonation_active');
  localStorage.removeItem('hlm_impersonation_target');
  localStorage.removeItem('hlm_superadmin_original_token');
  this.router.navigateByUrl('/superadmin/societes');
}
```

Inject `Router` if not already injected. Add to the template (top of the component, before the nav):
```html
@if (isImpersonating) {
  <div style="background:#e74c3c;color:#fff;text-align:center;padding:8px 16px;font-size:14px;">
    ⚠ Mode usurpation : <strong>{{ impersonationTarget }}</strong>
    <button (click)="endImpersonation()" style="margin-left:16px;background:#fff;color:#e74c3c;border:none;padding:4px 12px;border-radius:4px;cursor:pointer;">
      Revenir au SuperAdmin
    </button>
  </div>
}
```

### Verify
```bash
cd hlm-frontend && npm run build -- --configuration=production
```

---

## BUG 3 — CRITICAL: Société reactivation doesn't restore members

### Root cause
`SocieteService.reactiver()` sets `s.setActif(true)` but does NOT restore member `actif` flags. All members remain locked out even after the société is reactivated.

### What to fix

**File: `hlm-backend/src/main/java/com/yem/hlm/backend/societe/SocieteService.java`**

In the `desactiver()` method, ADD `dateRetrait` when deactivating members so we can identify them during reactivation:
```java
// In the forEach loop that deactivates members:
appUserSocieteRepository.findByIdSocieteId(id).forEach(aus -> {
    if (aus.isActif()) {  // Only deactivate active members
        aus.setActif(false);
        aus.setDateRetrait(s.getDateSuspension()); // Mark with suspension timestamp
        appUserSocieteRepository.save(aus);
        userRepository.findById(aus.getUserId()).ifPresent(user -> {
            user.incrementTokenVersion();
            userRepository.save(user);
        });
    }
});
```

In the `reactiver()` method, AFTER `s.setActif(true)`, ADD member restoration:
```java
s.setActif(true);

// Restore members that were deactivated during suspension
// (dateRetrait matches dateSuspension — these were NOT individually removed before suspension)
Instant suspensionDate = s.getDateSuspension();
if (suspensionDate != null) {
    appUserSocieteRepository.findByIdSocieteId(id).forEach(aus -> {
        if (!aus.isActif() && aus.getDateRetrait() != null
            && !aus.getDateRetrait().isBefore(suspensionDate.minusSeconds(5))) {
            aus.setActif(true);
            aus.setDateRetrait(null);
            appUserSocieteRepository.save(aus);
        }
    });
}

s.setDateSuspension(null);
s.setRaisonSuspension(null);
societeRepository.save(s);
```

### Verify
```bash
cd hlm-backend && ./mvnw test -Dtest=SocieteControllerIT
```
Verify: deactivate société → members inactive → reactivate → members active again.

---

## BUG 4 — MEDIUM: `SocieteContext.setRole()` is never called (dead code)

### Root cause
`JwtAuthenticationFilter` sets `societeId`, `userId`, and `superAdmin` but never calls `SocieteContext.setRole()`. The `SocieteContextHelper.getRole()` method always returns null.

### What to fix

**File: `hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/JwtAuthenticationFilter.java`**

In `doFilterInternal()`, after setting societeId and userId, add the role:
```java
// After: SocieteContext.setUserId(userId);
// Extract role from authorities and store in context
authorities.stream()
    .map(GrantedAuthority::getAuthority)
    .findFirst()
    .ifPresent(SocieteContext::setRole);
```

This goes in both the portal token branch and the CRM token branch.

### Verify
```bash
cd hlm-backend && ./mvnw test -Dtest=JwtAuthenticationFilterTest
```

---

## BUG 5 — MEDIUM: Dead code cleanup

### `AuthStore` — defined but never used

**File: `hlm-frontend/src/app/core/store/auth.store.ts`**

Do NOT delete the file — it may be used in the future. But add a comment:
```typescript
/**
 * Signal-based auth state store.
 * NOTE: Currently unused. AuthService manages auth state via localStorage.
 * TODO: Migrate AuthService to use AuthStore for reactive state management.
 */
```

### Verify
```bash
cd hlm-frontend && npm run build -- --configuration=production
```

---

## Final verification after ALL fixes

```bash
# Backend compiles and all tests pass
cd hlm-backend && ./mvnw verify

# Frontend builds successfully  
cd hlm-frontend && npm run build -- --configuration=production

# Cross-société isolation still works
cd hlm-backend && ./mvnw test -Dtest=CrossSocieteIsolationIT

# User management tests pass
cd hlm-backend && ./mvnw test -Dtest=UserManagementIsolationIT,RbacPrivilegeEscalationIT,SocieteControllerIT

# SocieteContextHelper tests pass
cd hlm-backend && ./mvnw test -Dtest=SocieteContextHelperTest
```

All must pass green before this PR can merge.
