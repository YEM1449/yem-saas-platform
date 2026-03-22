# Task 14 — Angular Frontend for User Management Module

## Priority: MEDIUM
## Effort: 4 hours
## Depends on: Task 13 (frontend audit first)

## Problem

The backend `usermanagement` module provides a complete API (`/api/mon-espace/utilisateurs`) but there is no Angular UI for it. Admins currently cannot manage users through the frontend.

## API Endpoints to Consume

From `UserManagementController.java`:

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/mon-espace/utilisateurs` | List members (paginated, filterable) |
| GET | `/api/mon-espace/utilisateurs/{id}` | Get member detail |
| POST | `/api/mon-espace/utilisateurs/inviter` | Invite user by email |
| PUT | `/api/mon-espace/utilisateurs/{id}` | Update user details |
| PATCH | `/api/mon-espace/utilisateurs/{id}/role` | Change role |
| POST | `/api/mon-espace/utilisateurs/{id}/debloquer` | Unblock user |
| DELETE | `/api/mon-espace/utilisateurs/{id}` | Remove (soft-delete) |
| GET | `/api/mon-espace/utilisateurs/invitations/{token}` | Get invitation details |
| POST | `/api/mon-espace/utilisateurs/invitations/{token}/activer` | Activate invitation |
| POST | `/api/mon-espace/utilisateurs/{id}/anonymiser` | GDPR anonymize |
| GET | `/api/mon-espace/utilisateurs/{id}/export` | GDPR data export |

## Files to Create

Following existing frontend patterns (standalone components, signals):

```
hlm-frontend/src/app/features/user-management/
├── user-management.service.ts       # HttpClient API calls
├── user-management.model.ts         # TypeScript interfaces
├── user-list.component.ts           # Paginated table with filters
├── user-detail.component.ts         # User detail / edit
├── user-invite.component.ts         # Invite dialog/form
└── user-management.routes.ts        # Lazy-loaded routes
```

### Model: `user-management.model.ts`

```typescript
export interface Membre {
  userId: string;
  email: string;
  prenom: string;
  nom: string;
  telephone: string;
  role: string;
  actif: boolean;
  emailVerifie: boolean;
  dernierLogin: string | null;
  statut: 'ACTIF' | 'INACTIF' | 'INVITE' | 'BLOQUE';
}

export interface InviterRequest {
  email: string;
  role: string;
  prenom?: string;
  nom?: string;
}

export interface MembreFilter {
  search?: string;
  role?: string;
  actif?: boolean;
}
```

### Route Registration

Add to `app.routes.ts`:
```typescript
{
  path: 'users',
  loadComponent: () => import('./features/user-management/user-list.component')
    .then(m => m.UserListComponent),
  canActivate: [authGuard, adminGuard]
}
```

### Shell Navigation

Add "Utilisateurs" link to `shell.component.ts` nav, visible only for ADMIN role.

## Acceptance Criteria

- [ ] Admin can list, filter, and paginate users
- [ ] Admin can invite new user by email
- [ ] Admin can change user role
- [ ] Admin can unblock locked users
- [ ] Admin can remove users (soft-delete)
- [ ] GDPR export and anonymize buttons work
- [ ] Navigation only visible to ADMIN role
- [ ] Frontend builds successfully
