# YEM SaaS Platform — Documentation

> **Start here.** All documentation for the YEM SaaS Platform (multi-tenant CRM for real estate teams).

---

## Quick Navigation

### Setup & Development
| Doc | Purpose |
|-----|---------|
| [00_OVERVIEW.md](00_OVERVIEW.md) | Mission, repo layout, component map |
| [01_ARCHITECTURE.md](01_ARCHITECTURE.md) | C4 architecture, packages, request flows |
| [05_DEV_GUIDE.md](05_DEV_GUIDE.md) | Local setup, commands, dev workflows |

### Reference
| Doc | Purpose |
|-----|---------|
| [api.md](api.md) | API endpoint catalog |
| [api-quickstart.md](api-quickstart.md) | API quick start with curl examples |
| [backend.md](backend.md) | Backend modules deep-dive |
| [frontend.md](frontend.md) | Frontend architecture and routing |
| [database.md](database.md) | Database schema and migration strategy |
| [security.md](security.md) | Security model (JWT, RBAC, multi-tenancy) |

### Operations
| Doc | Purpose |
|-----|---------|
| [07_RELEASE_AND_DEPLOY.md](07_RELEASE_AND_DEPLOY.md) | CI/CD workflows, release process |
| [runbooks/runbook_v2.md](runbooks/runbook_v2.md) | Production operational runbook |
| [contributing.md](contributing.md) | Contribution guidelines |

### Payments v2 (Current)
| Doc | Purpose |
|-----|---------|
| [v2/api.v2.md](v2/api.v2.md) | v2 API reference (preferred over api.md for payments) |
| [v2/api-quickstart.v2.md](v2/api-quickstart.v2.md) | v2 quick start |
| [v2/payment-v1-retirement-plan.v2.md](v2/payment-v1-retirement-plan.v2.md) | v1 deprecation runbook, migration plan |

### Specifications
| Doc | Purpose |
|-----|---------|
| [specs/Functional_Spec.md](specs/Functional_Spec.md) | Business rules, roles, workflows |
| [specs/Technical_Spec.md](specs/Technical_Spec.md) | Architecture, API conventions, data model |
| [specs/User_Guide.md](specs/User_Guide.md) | End-user persona-based guide |
| [specs/Implementation_Status.md](specs/Implementation_Status.md) | Feature implementation tracker |

### Onboarding
| Doc | Purpose |
|-----|---------|
| [08_ONBOARDING_COURSE.md](08_ONBOARDING_COURSE.md) | Day 0–5 learning course for new engineers |
| [09_NEW_ENGINEER_CHECKLIST.md](09_NEW_ENGINEER_CHECKLIST.md) | Six-gate readiness checklist |

### AI / LLM Context
| Doc | Purpose |
|-----|---------|
| [../context/PROJECT_CONTEXT.md](../context/PROJECT_CONTEXT.md) | Current LLM context pack |
| [../context/ARCHITECTURE.md](../context/ARCHITECTURE.md) | Architecture bullets for LLMs |
| [../context/DOMAIN_RULES.md](../context/DOMAIN_RULES.md) | Domain rules for LLMs |
| [../context/SECURITY_BASELINE.md](../context/SECURITY_BASELINE.md) | Security constraints for LLMs |

### Work Tracking
| Doc | Purpose |
|-----|---------|
| [_WORKLOG.md](_WORKLOG.md) | Chronological progress log |
| [_OPEN_POINTS.md](_OPEN_POINTS.md) | Open questions and decisions |
| [_TODO_NEXT.md](_TODO_NEXT.md) | Next action backlog |

### Archive
| Doc | Purpose |
|-----|---------|
| [archive/](archive/) | Superseded docs kept for historical reference |

---

## Canonical Commands

```bash
# Backend dev
cd hlm-backend && ./mvnw spring-boot:run

# Backend unit tests
cd hlm-backend && ./mvnw test

# Backend integration tests (Docker required)
cd hlm-backend && ./mvnw failsafe:integration-test failsafe:verify

# Frontend dev
cd hlm-frontend && npm ci && npm start

# Auth smoke test
TENANT_KEY=acme EMAIL=admin@acme.com PASSWORD='Admin123!' ./scripts/smoke-auth.sh
```

→ Full command reference: [../context/COMMANDS.md](../context/COMMANDS.md)
