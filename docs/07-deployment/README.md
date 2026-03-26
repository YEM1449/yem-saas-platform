# Deployment Guides

This section covers everything needed to run the YEM SaaS Platform locally and in production.

## Guides

| Guide | Description |
| --- | --- |
| [local-setup.md](local-setup.md) | Zero-to-hero local development setup — Docker Compose, mixed mode, complete env var reference, WSL2 notes |
| [production.md](production.md) | Production deployment — containerized single-host, TLS, first-time bootstrap, security hardening checklist |

## Deployment Modes

| Mode | Description | Best For |
| --- | --- | --- |
| **Local (Docker Compose)** | Full stack via `docker compose up -d` — all services containerized | Day-to-day development; no local JDK/Node required |
| **Mixed Local** | Infra (Postgres, Redis, MinIO) in Docker; backend + frontend on host | Active backend/frontend development — hot reload, fast iteration |
| **Production (Containerized)** | Docker Compose with optional prod overlay behind TLS-terminating reverse proxy | Single-host VPS/server production deployment |

## Quick Start

```bash
# 1. Copy environment template
cp .env.example .env
# 2. Edit .env — set JWT_SECRET at minimum (32+ chars)

# Full local stack (all services in Docker)
docker compose up -d

# Verify backend health
curl -s http://localhost:8080/actuator/health

# Frontend
open http://localhost
```

## Further Reading

- Architecture: [02-architecture/README.md](../02-architecture/README.md)
- Operations runbook: [runbook-operations.md](../runbook-operations.md)
- Integrations: [08-integrations/README.md](../08-integrations/README.md)
