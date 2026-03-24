# Using the YEM API with Postman

## Import

1. Export the OpenAPI spec (backend must be running):
   ```bash
   curl -s http://localhost:8080/api-docs -o scripts/yem-api-spec.json
   ```
2. Open Postman → **Import** → select `scripts/yem-api-spec.json`
3. Open Postman → **Import** → select `scripts/POSTMAN_ENVIRONMENT.json`
4. Select the **"YEM SaaS — Local"** environment in the top-right dropdown

## Getting tokens

### Admin token
```
POST {{base_url}}/auth/login
Content-Type: application/json

{"email": "admin@demo.ma", "password": "Admin123!Secure"}
```
Copy `accessToken` from the response → paste into `token_admin`.

### Super Admin token
```
POST {{base_url}}/auth/login
Content-Type: application/json

{"email": "superadmin@yourcompany.com", "password": "YourSecure2026!"}
```
Copy `accessToken` → paste into `token_sa`.

### Manager / Agent tokens
Same pattern with:

- `manager@demo.ma / Manager123!Sec`
- `agent@demo.ma / Agent123!Secure`

### Multi-societe note

If `/auth/login` returns:

```json
{
  "requiresSocieteSelection": true
}
```

then you must call:

```http
POST {{base_url}}/auth/switch-societe
Authorization: Bearer {{partial_token}}
Content-Type: application/json

{"societeId":"{{societe_id}}"}
```

## Testing RBAC — expected results

| Request | Token | Expected |
|---------|-------|----------|
| `GET {{base_url}}/api/admin/societes` | `token_admin` | **403** |
| `GET {{base_url}}/api/admin/societes` | `token_sa` | **200** |
| `POST {{base_url}}/api/mon-espace/utilisateurs` `{"role":"ADMIN"}` | `token_admin` | **403** `ROLE_ESCALATION_FORBIDDEN` |
| `POST {{base_url}}/api/mon-espace/utilisateurs` `{"role":"MANAGER"}` | `token_admin` | **201** |
| `GET {{base_url}}/api/mon-espace/utilisateurs` | `token_manager` | **200** |
| `GET {{base_url}}/api/mon-espace/utilisateurs` | `token_agent` | **403** |
| `POST {{base_url}}/api/mon-espace/utilisateurs` | `token_manager` | **403** |
| `PATCH {{base_url}}/api/mon-espace/utilisateurs/{{membre_id}}/role` `{"nouveauRole":"ADMIN"}` | `token_admin` | **403** `ROLE_ESCALATION_FORBIDDEN` |

## Key RBAC rules

- **SUPER_ADMIN only**: create/list/deactivate companies (`/api/admin/societes/**`)
- **ADMIN of own company**: invite MANAGER or AGENT, change roles, remove members
- **ADMIN CANNOT**: assign the ADMIN role (privilege escalation — returns 403 with `ROLE_ESCALATION_FORBIDDEN`)
- **MANAGER**: read-only access to team members, GDPR export
- **AGENT**: cannot manage users at all

## Common error codes

| Code | HTTP | Meaning |
|------|------|---------|
| `ROLE_ESCALATION_FORBIDDEN` | 403 | Company ADMIN tried to assign ADMIN role |
| `INSUFFICIENT_ROLE` | 403 | Caller's role is too low for this operation |
| `DERNIER_ADMIN` | 409 | Cannot remove or demote the last ADMIN |
| `SOCIETE_ALREADY_EXISTS` | 409 | Company name already taken |
| `CONCURRENT_UPDATE` | 409 | Optimistic lock failure — reload and retry |
| `MEMBRE_DEJA_EXISTANT` | 409 | User is already an active member |
| `TOKEN_INVALIDATED` | 401 | JWT revoked (role change, company suspension) |
