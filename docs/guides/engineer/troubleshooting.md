# Troubleshooting Guide

Use this guide when something breaks and you need a fast path to diagnosis.

## 1. Authentication Problems

| Symptom | Likely cause | First checks |
| --- | --- | --- |
| login succeeds but app behaves unauthenticated | cookie not set or not sent | inspect response cookies, secure flag, origin, proxy setup |
| multi-societe user cannot finish login | selection step or partial token flow broken | inspect `/auth/login` payload and `/auth/switch-societe` call |
| portal verify fails | expired or already used magic link | inspect portal token lifecycle and timestamps |

## 2. Scope And Authorization Problems

| Symptom | Likely cause | First checks |
| --- | --- | --- |
| records from the wrong societe appear | scoping bug | inspect service and repository predicates, then RLS |
| unexpected 403 or 404 | role mismatch or ownership guard | inspect controller annotations and service ownership rules |
| superadmin action behaves like normal CRM user | missing platform-vs-societe distinction | inspect token role and route family |

## 3. Database And Migration Problems

| Symptom | Likely cause | First checks |
| --- | --- | --- |
| backend fails on startup after schema change | Liquibase/entity mismatch | inspect recent changesets and entity fields |
| tests fail with FK or transactional surprises | wrong test design | look for class-level `@Transactional` or missing fixture data |
| data invisible unexpectedly | RLS context missing | inspect transaction order and `RlsContextAspect` behavior |

## 4. Frontend Problems

| Symptom | Likely cause | First checks |
| --- | --- | --- |
| route redirects unexpectedly | guard or session validation issue | inspect route config and auth services |
| translated labels missing | untranslated key | inspect translation files and templates |
| portal and CRM feel mixed | wrong shell or service assumptions | inspect route family and session service used |

## 5. Test Problems

| Symptom | Likely cause | First checks |
| --- | --- | --- |
| backend IT fails only in CI | Docker/Testcontainers environment | verify Docker availability and test assumptions |
| Playwright request calls return HTML | wrong API base | verify CI/static server vs backend endpoint |
| flaky auth tests | cookie/session timing | inspect exact login flow and waits |

## 6. Infrastructure Problems

| Symptom | Likely cause | First checks |
| --- | --- | --- |
| uploads fail in production | storage misconfiguration | verify endpoint, bucket, credentials, provider behavior |
| mail or SMS silently missing | no-op provider active or delivery failure | inspect configured provider and outbox state |
| cache inconsistency across nodes | Redis not enabled in multi-instance environment | inspect cache manager and deployment topology |

## 7. Good Diagnostic Entry Points

- backend logs
- browser devtools network tab
- `docker compose logs`
- controller/service class matching the broken route
- `docs/context/api-map.md`
