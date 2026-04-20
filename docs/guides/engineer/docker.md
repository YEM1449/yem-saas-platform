# Docker Guide

Docker Compose is the fastest way to run the platform end to end.

## 1. Services In The Local Stack

| Service | Role |
| --- | --- |
| `postgres` | primary relational database |
| `redis` | optional distributed cache |
| `minio` | local S3-compatible storage |
| `hlm-backend` | Spring Boot API |
| `hlm-frontend` | Nginx-served Angular build |

## 2. Standard Startup

```bash
cp .env.example .env
docker compose up -d --wait --wait-timeout 180
```

## 3. Common Workflows

### Start only infrastructure

```bash
docker compose up -d postgres redis minio
```

Useful when running backend and frontend manually.

### Restart one service

```bash
docker compose restart hlm-backend
```

### Inspect logs

```bash
docker compose logs hlm-backend -f
docker compose logs hlm-frontend -f
```

## 4. What Compose Is Doing For You

- wires backend to PostgreSQL and Redis
- exposes MinIO for object storage testing
- sets environment defaults for local execution
- serves the built frontend on port 80

## 5. Data And Reset Strategy

Compose uses named volumes for:

- PostgreSQL data
- Redis data
- MinIO data

If you intentionally need a clean environment, remove volumes with care and only when you mean to discard local state.

## 6. When Compose Is Not Enough

Switch to manual service runs when:

- you need Angular live rebuild behavior
- you are debugging backend startup or Maven runs directly
- you are iterating on tests faster than container rebuilds would allow

## 7. Common Docker Issues

- backend unhealthy because database or Redis is not ready yet
- frontend healthy but backend unreachable because backend crashed on startup
- MinIO enabled but storage config mismatched
- local `.env` not matching desired origin or cookie settings

## 8. Production Relationship

Local Compose is a developer convenience.
Production usually runs behind a reverse proxy and may use managed storage, managed Redis, and different TLS handling.
