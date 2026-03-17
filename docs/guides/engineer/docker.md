# Docker Guide — Engineer Guide

This guide covers the Docker Compose stack, individual service configurations, the multi-stage Dockerfiles, volume management, and common Docker operations.

## Table of Contents

1. [Stack Overview](#stack-overview)
2. [Docker Compose Services](#docker-compose-services)
3. [Backend Dockerfile](#backend-dockerfile)
4. [Frontend Dockerfile](#frontend-dockerfile)
5. [Common Operations](#common-operations)
6. [Volume Management](#volume-management)
7. [Environment Variables in Compose](#environment-variables-in-compose)
8. [Networking](#networking)
9. [Production Considerations](#production-considerations)

---

## Stack Overview

The complete stack is defined in `docker-compose.yml` at the project root. It starts five services:

| Service | Image | Port | Role |
|---------|-------|------|------|
| `hlm-postgres` | `postgres:16-alpine` | 5432 | Primary database |
| `hlm-redis` | `redis:7-alpine` | 6379 | Cache (when `REDIS_ENABLED=true`) |
| `hlm-minio` | `minio/minio:latest` | 9000, 9001 | S3-compatible object storage |
| `hlm-backend` | Built from `hlm-backend/Dockerfile` | 8080 | Spring Boot API |
| `hlm-frontend` | Built from `hlm-frontend/Dockerfile` | 80 | Nginx + Angular SPA |

Startup order enforced by `depends_on` with `condition: service_healthy`:
- `hlm-backend` waits for `hlm-postgres` (healthy) and `hlm-redis` (healthy).
- `hlm-frontend` has no wait condition.

---

## Docker Compose Services

### `hlm-postgres`

```yaml
hlm-postgres:
  image: postgres:16-alpine
  environment:
    POSTGRES_DB: ${POSTGRES_DB:-hlm}
    POSTGRES_USER: ${POSTGRES_USER:-hlm_user}
    POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-hlm_pwd}
  volumes:
    - postgres_data:/var/lib/postgresql/data
  ports:
    - "5432:5432"
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-hlm_user} -d ${POSTGRES_DB:-hlm}"]
    interval: 10s
    timeout: 5s
    retries: 5
```

### `hlm-redis`

```yaml
hlm-redis:
  image: redis:7-alpine
  volumes:
    - redis_data:/data
  ports:
    - "6379:6379"
  healthcheck:
    test: ["CMD", "redis-cli", "ping"]
    interval: 10s
    timeout: 5s
    retries: 5
```

### `hlm-minio`

```yaml
hlm-minio:
  image: minio/minio:latest
  command: server /data --console-address ":9001"
  environment:
    MINIO_ROOT_USER: ${MINIO_ROOT_USER:-minioadmin}
    MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD:-minioadmin}
  volumes:
    - minio_data:/data
  ports:
    - "9000:9000"
    - "9001:9001"
```

Access the MinIO console at `http://localhost:9001` with credentials `minioadmin` / `minioadmin`.

### `hlm-backend`

```yaml
hlm-backend:
  build:
    context: ./hlm-backend
    dockerfile: Dockerfile
  env_file: .env
  environment:
    DB_URL: jdbc:postgresql://hlm-postgres:5432/${POSTGRES_DB:-hlm}
    REDIS_HOST: hlm-redis
  ports:
    - "8080:8080"
  depends_on:
    hlm-postgres:
      condition: service_healthy
    hlm-redis:
      condition: service_healthy
  volumes:
    - /tmp/hlm-uploads:/tmp/hlm-uploads
```

The backend writes media uploads to `/tmp/hlm-uploads` inside the container, which is mounted from the Docker host's `/tmp/hlm-uploads`. This directory is always writable by the non-root `hlm` user (uid 1001).

### `hlm-frontend`

```yaml
hlm-frontend:
  build:
    context: ./hlm-frontend
    dockerfile: Dockerfile
  ports:
    - "80:80"
```

---

## Backend Dockerfile

Located at `hlm-backend/Dockerfile`. Multi-stage build:

```dockerfile
# Stage 1: Build
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:resolve -q
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy
RUN groupadd --gid 1001 hlm && \
    useradd --uid 1001 --gid hlm --shell /bin/bash --create-home hlm
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN chown -R hlm:hlm /app
USER hlm
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Key design decisions:
- **Two-stage build** — JDK (600 MB+) is not in the final image; JRE (200 MB) is used instead.
- **Non-root user** — `hlm` user with uid/gid 1001 runs the process.
- **SSL keystore** — NOT generated at build time. Inject via `SSL_KEYSTORE_PATH` env var at runtime if TLS is needed.

---

## Frontend Dockerfile

Located at `hlm-frontend/Dockerfile`. Multi-stage build:

```dockerfile
# Stage 1: Build Angular
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Stage 2: Nginx runtime
FROM nginx:alpine
COPY --from=build /app/dist/hlm-frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

---

## Common Operations

### Start the full stack

```bash
docker compose up --build -d
```

### Start only infrastructure (no backend or frontend)

Useful when running the backend locally on the host:

```bash
docker compose up -d hlm-postgres hlm-redis hlm-minio
```

### Watch backend logs

```bash
docker compose logs -f hlm-backend
```

### Watch all logs

```bash
docker compose logs -f
```

### Stop the stack (keep volumes)

```bash
docker compose down
```

### Stop the stack and destroy all data

```bash
docker compose down -v
```

### Rebuild a single service

```bash
docker compose build hlm-backend
docker compose up -d hlm-backend
```

### Shell into the backend container

```bash
docker exec -it hlm-backend bash
```

### Shell into PostgreSQL

```bash
docker exec -it hlm-postgres psql -U hlm_user -d hlm
```

### Check service health

```bash
docker compose ps
```

All services should show `healthy` status. If `hlm-backend` shows `unhealthy`, check logs:
```bash
docker compose logs hlm-backend | tail -50
```

---

## Volume Management

Three named volumes persist data across container restarts:

| Volume | Mounted at | Content |
|--------|-----------|---------|
| `postgres_data` | `/var/lib/postgresql/data` | All database data |
| `redis_data` | `/data` | Redis AOF / RDB snapshots |
| `minio_data` | `/data` | MinIO object storage |

### Reset PostgreSQL only

```bash
docker compose stop hlm-postgres
docker compose rm -f hlm-postgres
docker volume rm yem-saas-platform_postgres_data
docker compose up -d hlm-postgres
```

### List all project volumes

```bash
docker volume ls | grep yem-saas-platform
```

---

## Environment Variables in Compose

Compose reads environment variables from the `.env` file at the project root. The `.env` file is NOT committed to git (`.gitignore` excludes it). `.env.example` provides a template.

Required variable in `.env`:
```bash
JWT_SECRET=my-very-long-dev-secret-replace-me-32chars
```

Variables with defaults (no change needed for local dev):
```bash
POSTGRES_DB=hlm
POSTGRES_USER=hlm_user
POSTGRES_PASSWORD=hlm_pwd
DB_URL=jdbc:postgresql://localhost:5432/hlm
DB_USER=hlm_user
DB_PASSWORD=hlm_pwd
```

The backend service overrides `DB_URL` in `docker-compose.yml` to use the Docker service hostname:
```yaml
DB_URL: jdbc:postgresql://hlm-postgres:5432/${POSTGRES_DB:-hlm}
```

This means the `DB_URL` in `.env` is only used when running the backend outside Docker (local dev mode).

---

## Networking

All services join the `hlm-network` Docker bridge network. Service-to-service communication uses Docker DNS (service names as hostnames):

| From → To | Hostname |
|-----------|---------|
| backend → postgres | `hlm-postgres:5432` |
| backend → redis | `hlm-redis:6379` |
| backend → minio | `hlm-minio:9000` |
| frontend (Nginx) → backend | `hlm-backend:8080` |

Nginx proxies `/api/`, `/auth`, and `/actuator` to `http://hlm-backend:8080`.

---

## Production Considerations

### Running behind Nginx TLS termination

Set in `.env`:
```bash
FORWARD_HEADERS_STRATEGY=FRAMEWORK
```

This tells Spring to trust `X-Forwarded-Proto: https` from the reverse proxy, so HTTPS redirect detection and HSTS work correctly.

### Enable Redis for multi-instance deployments

```bash
REDIS_ENABLED=true
REDIS_HOST=your-redis-host
REDIS_PASSWORD=your-redis-password
```

With Caffeine (the default), each JVM instance has its own local cache. Token revocation via `token_version` will not propagate instantly to other instances. Redis provides a shared cache visible to all instances.

### Media storage in production

For multi-instance or stateless deployments, use S3-compatible object storage instead of local filesystem:

```bash
MEDIA_OBJECT_STORAGE_ENABLED=true
MEDIA_OBJECT_STORAGE_ENDPOINT=https://your-s3-endpoint
MEDIA_OBJECT_STORAGE_BUCKET=hlm-media
MEDIA_OBJECT_STORAGE_ACCESS_KEY=your-access-key
MEDIA_OBJECT_STORAGE_SECRET_KEY=your-secret-key
```

### Healthcheck endpoint

Production load balancers and orchestrators should poll:
```
GET http://hlm-backend:8080/actuator/health
```
Expected response: `{"status":"UP"}` (HTTP 200).
