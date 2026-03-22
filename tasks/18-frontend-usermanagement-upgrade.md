# Task 18 — Upgrade Admin Users UI to New UserManagement API

## Priority: HIGH
## Effort: 3 hours
## Backend API: `/api/mon-espace/utilisateurs` (10 endpoints, ready)

## Problem

The current `admin-users` component uses the old `AdminUserService` with basic CRUD. The new `UserManagementController` provides a richer API with invitation flow, GDPR compliance, role management, blocking/unblocking, filtering, and pagination.

## Approach

Replace the existing `admin-users` feature with a modern implementation consuming the new API.

## Backend Endpoints

Check the actual DTOs and endpoints:
```bash
cat hlm-backend/src/main/java/com/yem/hlm/backend/usermanagement/UserManagementController.java
cat hlm-backend/src/main/java/com/yem/hlm/backend/usermanagement/dto/MembreDto.java
cat hlm-backend/src/main/java/com/yem/hlm/backend/usermanagement/dto/InviterUtilisateurRequest.java
cat hlm-backend/src/main/java/com/yem/hlm/backend/usermanagement/dto/MembreFilter.java
cat hlm-backend/src/main/java/com/yem/hlm/backend/usermanagement/dto/MembreStatut.java
```

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/mon-espace/utilisateurs?search=&role=&actif=&page=0&size=20` | List members |
| GET | `/api/mon-espace/utilisateurs/{id}` | Member detail |
| POST | `/api/mon-espace/utilisateurs` | Invite new user |
| POST | `/api/mon-espace/utilisateurs/{id}/reinviter` | Resend invitation |
| PATCH | `/api/mon-espace/utilisateurs/{id}` | Update user |
| PATCH | `/api/mon-espace/utilisateurs/{id}/role` | Change role |
| DELETE | `/api/mon-espace/utilisateurs/{id}` | Remove user |
| POST | `/api/mon-espace/utilisateurs/{id}/debloquer` | Unblock user |
| GET | `/api/mon-espace/utilisateurs/{id}/export-donnees` | GDPR export |
| DELETE | `/api/mon-espace/utilisateurs/{id}/anonymiser` | GDPR anonymize |

## Files to Modify/Create

### Option A: Rewrite in-place (preferred)

Replace the contents of the existing `admin-users` feature:

```
hlm-frontend/src/app/features/admin-users/
├── admin-user.model.ts          # UPDATE: match MembreDto
├── admin-user.service.ts        # UPDATE: new API base path + methods
├── admin-users.component.ts     # UPDATE: pagination, filters, new actions
├── admin-users.component.html   # UPDATE: redesigned template
├── admin-users.component.css    # UPDATE: styles
├── user-invite-dialog.component.ts   # NEW: invitation form
└── user-invite-dialog.component.html # NEW
```

### Key Changes

**Service:** Change base URL from `/api/admin/users` to `/api/mon-espace/utilisateurs`. Add new methods:
- `inviter(request)` → POST
- `reinviter(userId)` → POST reinviter
- `changerRole(userId, role)` → PATCH role
- `debloquer(userId)` → POST debloquer
- `exportDonnees(userId)` → GET export
- `anonymiser(userId)` → DELETE anonymiser

**List component:** Add:
- Pagination controls (page/size)
- Filter bar: search text, role dropdown, active/inactive toggle
- Status badges: ACTIF (green), INACTIF (gray), INVITE (blue), BLOQUE (red)
- Action buttons per row: change role, resend invitation, block/unblock, remove
- GDPR section: export data, anonymize (with confirmation)

**Invite dialog:** 
- Email field (required)
- Role selection (ADMIN/MANAGER/AGENT)
- Optional: first name, last name
- Submit → calls `POST /api/mon-espace/utilisateurs`

## Acceptance Criteria

- [ ] Member list shows all société users with status and role
- [ ] Pagination and filtering work
- [ ] Invite new user by email works
- [ ] Change role works
- [ ] Unblock locked user works
- [ ] Remove user works (with confirmation)
- [ ] GDPR export downloads data
- [ ] GDPR anonymize works (with strong confirmation)
- [ ] Non-admin users cannot access the page
- [ ] Frontend builds successfully
