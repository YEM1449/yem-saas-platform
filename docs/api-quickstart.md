# API Quickstart

Minimum calls to integrate a frontend. All examples assume `http://localhost:8080`.

## Step 0 — Health check

```bash
curl -i http://localhost:8080/actuator/health
```

Expected: `200 {"status":"UP"}`. If this fails, the backend is not running — check logs for `Tomcat started on port(s): 8080`.

## Step 1 — Login

```bash
curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "tenantKey": "acme",
    "email": "admin@acme.com",
    "password": "Admin123!"
  }'
```

**Request body** (`LoginRequest`):
| Field       | Type   | Required | Notes                     |
|-------------|--------|----------|---------------------------|
| `tenantKey` | string | yes      | Tenant identifier         |
| `email`     | string | yes      | User email                |
| `password`  | string | yes      | User password             |

**Response** (`LoginResponse`, 200 OK):
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

Save `accessToken` — all subsequent requests need it.

## Step 2 — Verify identity

```bash
TOKEN="<paste accessToken here>"

curl -s http://localhost:8080/auth/me \
  -H "Authorization: Bearer $TOKEN"
```

**Response** (200 OK):
```json
{
  "userId": "22222222-2222-2222-2222-222222222222",
  "tenantId": "11111111-1111-1111-1111-111111111111"
}
```

## Step 3 — List properties (protected, all roles)

```bash
curl -s http://localhost:8080/api/properties \
  -H "Authorization: Bearer $TOKEN"
```

**Response** (200 OK): JSON array of `PropertyResponse` objects.

Optional query params: `?type=APARTMENT&status=AVAILABLE`.

## Step 4 — List contacts (protected, all roles)

```bash
curl -s http://localhost:8080/api/contacts \
  -H "Authorization: Bearer $TOKEN"
```

## Error responses

All errors return a consistent `ErrorResponse` shape (see `common/error/ErrorResponse.java`):

### 401 Unauthorized — missing or invalid token

```json
{
  "timestamp": "2026-02-09T12:00:00.000+00:00",
  "status": 401,
  "error": "Unauthorized",
  "code": "UNAUTHORIZED",
  "message": "Full authentication is required to access this resource",
  "path": "/api/properties"
}
```

### 403 Forbidden — insufficient role

```json
{
  "timestamp": "2026-02-09T12:00:00.000+00:00",
  "status": 403,
  "error": "Forbidden",
  "code": "FORBIDDEN",
  "message": "Access Denied",
  "path": "/api/properties"
}
```

### 400 Validation Error — bad request body

```json
{
  "timestamp": "2026-02-09T12:00:00.000+00:00",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "path": "/auth/login",
  "fieldErrors": [
    { "field": "email", "message": "must not be blank" }
  ]
}
```

### 404 Not Found

```json
{
  "timestamp": "2026-02-09T12:00:00.000+00:00",
  "status": 404,
  "error": "Not Found",
  "code": "NOT_FOUND",
  "message": "Property not found",
  "path": "/api/properties/00000000-0000-0000-0000-000000000000"
}
```

## RBAC summary

| Endpoint pattern           | ADMIN | MANAGER | AGENT |
|----------------------------|-------|---------|-------|
| `POST /api/properties`     | yes   | yes     | 403   |
| `GET /api/properties`      | yes   | yes     | yes   |
| `PUT /api/properties/{id}` | yes   | yes     | 403   |
| `DELETE /api/properties/{id}` | yes | 403     | 403   |

## JWT claims reference

Decoded JWT payload:
```json
{
  "sub": "22222222-2222-2222-2222-222222222222",
  "tid": "11111111-1111-1111-1111-111111111111",
  "roles": ["ROLE_ADMIN"],
  "iat": 1739100000,
  "exp": 1739103600
}
```

- `sub` — user ID (UUID)
- `tid` — tenant ID (UUID), used for data isolation
- `roles` — list of role strings (prefixed with `ROLE_`)
