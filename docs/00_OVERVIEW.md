# 00 — Project Overview

## Mission

YEM SaaS Platform is a **multi-tenant CRM** for real estate promotion and construction teams. It provides:
- Tenant-isolated data access via JWT + ThreadLocal context
- Role-based permissions (Admin / Manager / Agent)
- Client-facing portal for property buyers (ROLE_PORTAL)
- REST API consumed by an Angular 19 SPA
- Transactional outbox for reliable email/SMS delivery
- Commercial intelligence dashboards (KPIs, commissions, receivables)

## Repository Layout

```
yem-saas-platform/
├── hlm-backend/          # Spring Boot 3.5.8, Java 21
│   ├── src/main/java/com/yem/hlm/backend/
│   │   ├── audit/        # Audit log entity + listener
│   │   ├── auth/         # JWT, security config, login endpoint
│   │   ├── commission/   # Commission rules + calculations (Phase 3)
│   │   ├── common/       # Error envelope, shared utilities
│   │   ├── contact/      # Contacts, prospects, lead management
│   │   ├── contract/     # Sale contracts
│   │   ├── dashboard/    # KPI + commercial intelligence dashboards
│   │   ├── deposit/      # Reservation deposits
│   │   ├── media/        # File upload/download
│   │   ├── notification/ # In-app CRM bell notifications
│   │   ├── outbox/       # Transactional outbox (email/SMS)
│   │   ├── payment/      # Payment tracking
│   │   ├── payments/     # Payment-related services (v2; preferred)
│   │   ├── portal/       # Client-facing portal (Phase 4)
│   │   ├── project/      # Real estate projects
│   │   ├── property/     # Properties (units/lots)
│   │   ├── reminder/     # Scheduled reminders
│   │   ├── tenant/       # Tenant management + context
│   │   └── user/         # Users + RBAC
│   └── src/main/resources/
│       ├── db/changelog/ # Liquibase migrations (changesets 001–027+)
│       └── templates/    # Thymeleaf PDF templates
├── hlm-frontend/         # Angular 19.2, standalone components
│   ├── src/app/          # Feature modules + routes
│   └── proxy.conf.json   # Dev proxy → :8080
├── docs/                 # All documentation (this directory)
│   ├── specs/            # Functional, Technical, User Guide specs
│   ├── adr/              # Architecture Decision Records
│   └── ai/               # LLM context (legacy)
├── context/              # LLM-optimized context files
├── scripts/              # Utility scripts (smoke-auth.sh)
├── .github/workflows/    # 4 CI workflows
├── .env.example          # Environment variable template
├── AGENTS.md             # Canonical project guide (read this first)
└── CLAUDE.md             # Lightweight quick-reference
```

## Key Technology Choices

| Concern | Choice | Rationale |
|---------|--------|-----------|
| Backend framework | Spring Boot 3.5.8 | Mature, production-ready, wide library ecosystem |
| Language | Java 21 | LTS, virtual threads ready, modern records |
| Database | PostgreSQL 16 | ACID, JSON support, Testcontainers support |
| Schema mgmt | Liquibase | Additive-only changesets; audit trail |
| ORM | Spring Data JPA (Hibernate 6) | Repository pattern, JPQL, pagination |
| Auth | Spring Security + JWT (OAuth2 Resource Server) | Stateless, multi-tenant via `tid` claim |
| Cache | Caffeine (local, per-node) | Fast in-process cache; TTL per cache name |
| Messaging | Transactional Outbox | At-least-once email/SMS without 2PC |
| Frontend | Angular 19 standalone | Modern component model, lazy loading |
| PDF | OpenHtmlToPDF + Thymeleaf | Server-side PDF generation from HTML templates |
| API docs | SpringDoc OpenAPI/Swagger UI | Auto-generated from annotations |

## Domain Glossary

| Term | Definition |
|------|-----------|
| Tenant | An isolated organization (e.g., a real estate company). Identified by `tid` UUID. |
| Property | A sellable unit/lot. States: DRAFT → ACTIVE → RESERVED → SOLD / CANCELLED |
| Contact | A person in the CRM. Can be a prospect, buyer, or other type. |
| Prospect | A Contact with `contactType=PROSPECT` and a `ProspectDetail` record. |
| Deposit | A reservation deposit linking a Contact to a Property. Creates a RESERVED state. |
| Contract | A signed sale contract. Links Contact, Property, Project. |
| Project | A real estate development project. Groups multiple Properties. |
| Outbox | Transactional outbox table for reliable email/SMS delivery. |
| Portal | Client-facing login portal for property buyers (separate JWT, ROLE_PORTAL). |
| Commission | Rule-based calculation applied to contract amounts (project-specific or tenant default). |

## Phases Delivered

| Phase | Feature Area | Status |
|-------|-------------|--------|
| 1 | Core CRM (contacts, properties, deposits, notifications) | ✅ Done |
| 2 | Outbox messaging (email/SMS via transactional outbox) | ✅ Done |
| 3 | Commercial Intelligence (commissions, dashboards, analytics) | ✅ Done |
| 4 | Client Portal (magic link auth, ROLE_PORTAL, contract view) | ✅ Done |
