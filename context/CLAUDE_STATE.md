# CLAUDE_STATE.md — Single Source of Truth

_Last updated: 2026-03-04_

## Current Branch
`Epic/sec-improvement`

## Current Phase
**ALL PHASES COMPLETE** (2026-03-04)

## Stack Detected
| Layer | Tech |
|-------|------|
| Backend | Spring Boot 3.5.8, Java 21, Maven (mvnw) |
| ORM/DB | Spring Data JPA, PostgreSQL, Liquibase |
| Cache | Caffeine via Spring Cache |
| Security | Spring Security, JWT (OAuth2 Resource Server), Bucket4j rate limiting |
| Frontend | Angular 19.2, TypeScript 5.7, standalone components |
| Testing | JUnit 5, Mockito, Testcontainers (PostgreSQL), Spring Security Test |
| CI | GitHub Actions (6 workflows) |
| Security Scanning | Snyk (OSS + Code), CodeQL, Dependency Review, Secret Scan |
| Logging | Logstash Logback Encoder (JSON structured logs) |

## Canonical Commands
See context/COMMANDS.md

## Phase Log
| Phase | Status | Notes |
|-------|--------|-------|
| 0 - Recon | DONE | Repo map, stack, commands documented |
| 1 - Code Cleanup | DONE | CI workflows clean; app code sweep deferred |
| 2 - Docs Cleanup | DONE | README, 00_OVERVIEW, 01_ARCHITECTURE, 05_DEV_GUIDE, 07_RELEASE_AND_DEPLOY |
| 3 - Specs | DONE | Functional_Spec v1.1, Technical_Spec v1.1 (Phase 3+4 added) |
| 4 - Context Files | DONE | PROJECT_CONTEXT, ARCHITECTURE, DOMAIN_RULES, SECURITY_BASELINE, CONVENTIONS |
| 5 - Onboarding | DONE | 08_ONBOARDING_COURSE, 09_NEW_ENGINEER_CHECKLIST |
| 6 - CI + Snyk | DONE | failsafe:verify added, weekly Snyk scan added |
| 7 - Final Verification | DONE | 46 tests green, all YAML valid |

## Last Test Run
- `./mvnw -B -ntp test` → 46 tests, 0 failures (2026-03-04)
- All 6 workflow YAMLs valid

## Open Points
See docs/_OPEN_POINTS.md (OP-001 and OP-004 resolved; OP-002, OP-003, OP-005, OP-006, OP-007, OP-008 remain)
