# YEM SaaS Documentation v2

This directory contains the **v2 documentation set**. It is intentionally separate from the existing docs so v1 remains intact.

## Scope of v2
v2 is designed to be:
- developer-ready (actionable commands, integration details, constraints),
- client-oriented (business outcomes, workflows, value mapping),
- architecture-accurate (aligned with current codebase behavior).

## Document Map
| File | Audience | Purpose |
|------|----------|---------|
| [00_OVERVIEW.v2.md](00_OVERVIEW.v2.md) | All | Product overview, architecture snapshot, module map |
| [business-specification.v2.md](business-specification.v2.md) | Product, Delivery, Clients | Business goals, scope, personas, KPIs, roadmap |
| [MODULES_AND_FEATURES.v2.md](MODULES_AND_FEATURES.v2.md) | Architects, Developers, Clients | Deep module breakdown (purpose, inputs, outputs, dependencies) |
| [api.v2.md](api.v2.md) | Frontend, Integrators, QA | Current API catalog with roles and behavior notes |
| [api-quickstart.v2.md](api-quickstart.v2.md) | Developers, QA | End-to-end runnable API flows (CRM + portal) |
| [SETUP_USAGE_TROUBLESHOOTING.v2.md](SETUP_USAGE_TROUBLESHOOTING.v2.md) | Developers, QA, Ops | Setup steps, usage loops, troubleshooting matrix |
| [CONTEXT_AND_CONFIGURATION.v2.md](CONTEXT_AND_CONFIGURATION.v2.md) | Developers, Ops | Runtime variables and context standardization |
| [08_ONBOARDING_COURSE.v2.md](08_ONBOARDING_COURSE.v2.md) | New engineers | 5-day onboarding curriculum with labs |
| [09_NEW_ENGINEER_CHECKLIST.v2.md](09_NEW_ENGINEER_CHECKLIST.v2.md) | Team leads, Mentors | Readiness gates and contribution sign-off |
| [SUMMARY_v1_to_v2.md](SUMMARY_v1_to_v2.md) | Stakeholders | Major improvements, new content, future gaps |

## Recommended Reading Order
### New developer
1. `00_OVERVIEW.v2.md`
2. `SETUP_USAGE_TROUBLESHOOTING.v2.md`
3. `api-quickstart.v2.md`
4. `08_ONBOARDING_COURSE.v2.md`
5. `09_NEW_ENGINEER_CHECKLIST.v2.md`

### Product/client stakeholder
1. `business-specification.v2.md`
2. `00_OVERVIEW.v2.md`
3. `SUMMARY_v1_to_v2.md`

### Integrator
1. `api.v2.md`
2. `api-quickstart.v2.md`
3. `SUMMARY_v1_to_v2.md`

## Source-of-Truth Alignment
This v2 set is aligned with:
- `context/PROJECT_CONTEXT.md`
- `context/ARCHITECTURE.md`
- `context/DOMAIN_RULES.md`
- `context/SECURITY_BASELINE.md`
- current backend controllers/services in `hlm-backend/src/main/java/com/yem/hlm/backend/`
