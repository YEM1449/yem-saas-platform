# GDPR / Law 09-08 Compliance Guide

> **Legal disclaimer** — This document describes the technical implementation of data-protection controls.
> It must be reviewed and validated by qualified legal counsel before relying on it for regulatory compliance.
> Placeholders (`[NOM DE LA SOCIÉTÉ]`, `[EMAIL DPO]`) in the privacy notice must be replaced before production.

---

## Table of Contents

1. [Regulatory Scope](#1-regulatory-scope)
2. [Data Inventory](#2-data-inventory)
3. [Consent Management (Art. 6-7 RGPD / Loi 09-08 Art. 4)](#3-consent-management)
4. [Data Subject Rights API](#4-data-subject-rights-api)
5. [Anonymization Rules](#5-anonymization-rules)
6. [Automated Data Retention](#6-automated-data-retention)
7. [Privacy Notice (Art. 13 RGPD / Loi 09-08 Art. 5)](#7-privacy-notice)
8. [RBAC Enforcement](#8-rbac-enforcement)
9. [Audit Trail](#9-audit-trail)
10. [Configuration Reference](#10-configuration-reference)
11. [Integration Tests](#11-integration-tests)
12. [Operational Runbook](#12-operational-runbook)

---

## 1. Regulatory Scope

The platform operates under two legal frameworks simultaneously:

| Framework | Jurisdiction | Key Articles Implemented |
|---|---|---|
| **RGPD** (EU 2016/679) | EU / France | Art. 5(1)(e) storage limitation, Art. 6-7 lawfulness/consent, Art. 13 transparency, Art. 15 access, Art. 16 rectification, Art. 17 erasure, Art. 20 portability, Art. 21 objection |
| **Loi 09-08** | Morocco | Art. 4 lawfulness, Art. 5 notice, Art. 7 data-subject rights, Art. 15-22 obligations of controller |

Both frameworks apply when the tenant's operations span France and Morocco (common in cross-border real-estate).

---

## 2. Data Inventory

### Personal Data Collected on Contact

| Field | Column | Category | Retention |
|---|---|---|---|
| Full name | `full_name`, `first_name`, `last_name` | Identity | 5 years from last interaction |
| Email | `email` | Contact | 5 years |
| Phone | `phone` | Contact | 5 years |
| National ID | `national_id` | Sensitive ID | 5 years |
| Address | `address` | Location | 5 years |
| Notes | `notes` | Free text | 5 years |
| Consent flag | `consent_given` | Legal | Stored with contact |
| Consent date | `consent_date` | Legal | Stored with contact |
| Consent method | `consent_method` | Legal | Stored with contact |
| Processing basis | `processing_basis` | Legal | Stored with contact |

### Data in Related Entities

| Entity | Personal Data | Notes |
|---|---|---|
| `SaleContract` | `buyer_display_name`, `buyer_email`, `buyer_phone`, `buyer_ice`, `buyer_address` | Snapshot at contract creation; SIGNED contracts kept for legal archive (10 years) |
| `ProspectDetail` | `notes`, `source` | Anonymized alongside contact |
| `ClientDetail` | `company_name`, `ice`, `siret` | Anonymized alongside contact |
| `Deposit` | amount, currency only | Not PII — preserved |
| `OutboundMessage` | `to` field (email/phone) | Preserved for audit; included in data export |

---

## 3. Consent Management

### Enums

```
ConsentMethod  : CRM_ENTRY | PORTAL | PAPER | EMAIL
ProcessingBasis: CONTRACT  | CONSENT | LEGITIMATE_INTEREST
```

### Contact Fields (changeset 029)

```sql
consent_given     BOOLEAN      NOT NULL DEFAULT FALSE
consent_date      TIMESTAMP    NULL
consent_method    VARCHAR(100) NULL
processing_basis  VARCHAR(100) NULL
data_retention_days INT        NULL     -- per-contact override; NULL = tenant default
anonymized_at     TIMESTAMP    NULL     -- set when PII is erased
```

### API — Set Consent on Create

```json
POST /api/contacts
{
  "firstName": "Jean",
  "lastName":  "Dupont",
  "email":     "jean@example.com",
  "consentGiven":     true,
  "consentMethod":    "CRM_ENTRY",
  "processingBasis":  "CONTRACT"
}
```

When `consentGiven: true`, `ContactService.create()` records `consentDate = Instant.now()`.

### API — Update Consent

```json
PUT /api/contacts/{id}
{
  "consentGiven":    true,
  "consentMethod":   "PORTAL",
  "processingBasis": "CONSENT"
}
```

`consentDate` is updated **only** when transitioning from `false → true` (first grant).
Subsequent `true → true` updates preserve the original consent date.

---

## 4. Data Subject Rights API

All endpoints are under `/api/gdpr`. Tenant scoping is enforced via the JWT `tid` claim — no cross-tenant access is possible.

### 4.1 Data Export (Art. 15 / Art. 20 — Access & Portability)

```
GET /api/gdpr/contacts/{id}/export
Authorization: Bearer <ADMIN or MANAGER token>
```

Returns a JSON document containing all personal data held for the contact:

```json
{
  "contactId":       "...",
  "fullName":        "Jean Dupont",
  "email":           "jean@example.com",
  "phone":           "+212600000000",
  "status":          "CLIENT",
  "type":            "INDIVIDUAL",
  "consentGiven":    true,
  "consentDate":     "2026-01-15T10:00:00Z",
  "consentMethod":   "CRM_ENTRY",
  "processingBasis": "CONTRACT",
  "interests":       [...],
  "deposits":        [...],
  "contracts":       [...],
  "auditEvents":     [...],
  "outboundMessages":[...],
  "exportedAt":      "2026-03-15T12:00:00Z",
  "exportedByUserId":"..."
}
```

**Error**: `404 GDPR_EXPORT_NOT_FOUND` if the contact does not exist in the caller's tenant.

### 4.2 Anonymization / Right to Erasure (Art. 17)

```
DELETE /api/gdpr/contacts/{id}/anonymize
Authorization: Bearer <ADMIN token only>
```

**Success (200)**: Contact PII is zeroed, `deleted = true`, `anonymized_at = NOW()`.

**Blocked (409 GDPR_ERASURE_BLOCKED)**: Contact has at least one `SIGNED` contract.
The response body includes the list of blocking contract IDs so the operator can inform the data subject of the legal archive obligation.

```json
{
  "errorCode": "GDPR_ERASURE_BLOCKED",
  "message":   "Contact cannot be erased: 1 signed contract(s) require legal archiving.",
  "blockingContractIds": ["<uuid>"]
}
```

**Idempotent**: Calling anonymize on an already-anonymized contact returns 200 (no-op).

### 4.3 Rectification View (Art. 16)

```
GET /api/gdpr/contacts/{id}/rectify
Authorization: Bearer <ADMIN or MANAGER token>
```

Returns the mutable PII fields that operators can correct:

```json
{
  "contactId":       "...",
  "fullName":        "Jean Dupont",
  "email":           "jean@example.com",
  "phone":           "+212600000000",
  "consentGiven":    true,
  "consentMethod":   "CRM_ENTRY",
  "processingBasis": "CONTRACT"
}
```

To apply corrections, use the standard `PUT /api/contacts/{id}` endpoint.

### 4.4 Privacy Notice (Art. 13 / Loi 09-08 Art. 5)

```
GET /api/gdpr/privacy-notice
Authorization: Bearer <any authenticated role>
```

```json
{
  "version":     "1.0",
  "lastUpdated": "2026-03",
  "text":        "NOTICE D'INFORMATION SUR LE TRAITEMENT..."
}
```

The notice is loaded at startup from `classpath:gdpr/privacy-notice.txt`.
If the resource is absent a built-in French default is used (fail-safe).

---

## 5. Anonymization Rules

`AnonymizationService.anonymize(Contact contact, UUID actorUserId)` applies the following transformations atomically within a single transaction:

### Contact Fields

| Field | Before | After |
|---|---|---|
| `full_name` | "Jean Dupont" | "ANONYMIZED" |
| `first_name` | "Jean" | "ANONYMIZED" |
| `last_name` | "Dupont" | "" |
| `email` | "jean@example.com" | "anon-{UUID}@anonymized.invalid" |
| `phone` | "+212600000000" | `NULL` |
| `national_id` | "BE123456" | `NULL` |
| `address` | "12 rue…" | `NULL` |
| `notes` | "Intéressé par…" | `NULL` |
| `consent_given` | `true` | `false` |
| `consent_date` | "2026-01-15…" | `NULL` |
| `deleted` | `false` | `true` |
| `anonymized_at` | `NULL` | `NOW()` |

### Contract Snapshots (DRAFT / CANCELED only)

| Field | After |
|---|---|
| `buyer_display_name` | "ANONYMIZED" |
| `buyer_email` | `NULL` |
| `buyer_phone` | `NULL` |
| `buyer_ice` | `NULL` |
| `buyer_address` | `NULL` |

> SIGNED contracts are **not modified** — they must be retained for legal and accounting obligations.

### ProspectDetail / ClientDetail

| Entity | Fields Zeroed |
|---|---|
| `ProspectDetail` | `notes`, `source` |
| `ClientDetail` | `company_name`, `ice`, `siret` |

### Blocking Condition

Erasure is blocked when `SaleContractRepository.findSignedContractsByContact(tenantId, contactId)` returns a non-empty list. The service throws `GdprErasureBlockedException` with the list of blocking contract IDs.

---

## 6. Automated Data Retention

`DataRetentionScheduler` runs daily at 02:00 (configurable via `DATA_RETENTION_CRON`).

### Algorithm

```
FOR each tenant T:
  cutoff = NOW() - defaultRetentionDays          # default 1825 days (5 years)
  candidates = Contact WHERE tenant=T
                         AND deleted=true
                         AND anonymizedAt IS NULL
                         AND updatedAt < cutoff
  FOR each candidate C:
    TRY anonymize(C, SYSTEM_ACTOR)
    CATCH GdprErasureBlockedException → WARN log, skip
  LOG "[RETENTION] Tenant {T}: anonymized {n}, skipped {m}"
```

### System Actor

Automated anonymizations are attributed to `UUID(0L, 0L)` in the audit log to distinguish them from operator-initiated requests.

### Per-Contact Override

Set `data_retention_days` on the contact entity to override the tenant-wide default for that specific contact.
`NULL` means "use tenant default" (currently tenant-global, per-tenant config planned).

### Disabling the Scheduler

The scheduler bean is conditional on `spring.task.scheduling.enabled=true`.
It is suppressed in the `test` Spring profile so integration tests are not affected.

### Configuration

| Env Variable | YAML Key | Default | Description |
|---|---|---|---|
| `GDPR_RETENTION_DAYS` | `app.gdpr.default-retention-days` | `1825` | Days after `updatedAt` before a soft-deleted contact is anonymized |
| `DATA_RETENTION_CRON` | `app.gdpr.retention-cron` | `0 0 2 * * *` | Cron expression for the daily sweep (server local time) |

---

## 7. Privacy Notice

The notice file is at `hlm-backend/src/main/resources/gdpr/privacy-notice.txt`.

### Required Substitutions Before Production

| Placeholder | Replace With |
|---|---|
| `[NOM DE LA SOCIÉTÉ]` | Legal entity name of the tenant/operator |
| `[EMAIL DPO]` | DPO contact email address |

### Update Process

1. Edit `privacy-notice.txt`.
2. Bump the `version` string in `PrivacyNoticeLoader.getNotice()`.
3. Update `lastUpdated` to the current month (`YYYY-MM`).
4. Restart the backend — the file is loaded once at startup via `@PostConstruct`.
5. Users will see the new notice on their next page load (sessionStorage dismiss key is per-session).

---

## 8. RBAC Enforcement

| Endpoint | Required Role |
|---|---|
| `GET /api/gdpr/contacts/{id}/export` | ADMIN, MANAGER |
| `DELETE /api/gdpr/contacts/{id}/anonymize` | ADMIN only |
| `GET /api/gdpr/contacts/{id}/rectify` | ADMIN, MANAGER |
| `GET /api/gdpr/privacy-notice` | ADMIN, MANAGER, AGENT |

Enforcement is via Spring Security `@PreAuthorize` annotations on `GdprController`.
Unauthenticated requests → **401**. Insufficient role → **403**.

---

## 9. Audit Trail

Every successful anonymization writes a `CONTACT_ANONYMIZED` event to `commercial_audit` via `CommercialAuditService`:

```
correlationType : "CONTACT"
correlationId   : <contactId>
eventType       : CONTACT_ANONYMIZED
actorUserId     : <userId> | UUID(0,0) for scheduled runs
tenantId        : <tenantId>
```

The event is included in future data-export responses (`auditEvents[]` array), providing a complete chain of custody.

---

## 10. Configuration Reference

Complete list of GDPR-related environment variables:

```env
# Data retention
GDPR_RETENTION_DAYS=1825       # 5 years; override per deployment
DATA_RETENTION_CRON=0 0 2 * * * # server local time

# Scheduling (set false in test profile)
# spring.task.scheduling.enabled=true
```

These map to `application.yml`:

```yaml
app:
  gdpr:
    default-retention-days: ${GDPR_RETENTION_DAYS:1825}
    retention-cron: ${DATA_RETENTION_CRON:0 0 2 * * *}
```

---

## 11. Integration Tests

`GdprIT` (9 tests, Maven Failsafe) covers the full API surface:

| Test | Scenario | Expected |
|---|---|---|
| `export_existingContact_returns200WithPersonalData` | Valid contact in caller's tenant | 200 + full JSON export |
| `export_contactInDifferentTenant_returns404` | Random UUID not in tenant | 404 GDPR_EXPORT_NOT_FOUND |
| `anonymize_contactWithNoContracts_returns200AndZeroesPii` | Clean contact | 200; PII zeroed in DB |
| `anonymize_contactWithSignedContract_returns409ErasureBlocked` | SIGNED contract exists | 409 GDPR_ERASURE_BLOCKED |
| `anonymize_contactWithDraftContract_returns200AndZeroesBuyerSnapshot` | DRAFT contract only | 200; buyer snapshot zeroed |
| `privacyNotice_returns200WithText` | Any role | 200 + version + text |
| `anonymize_asAgent_returns403` | AGENT role | 403 |
| `anonymize_calledTwice_isIdempotent` | Double anonymize | Both calls return 200 |
| `rectify_asManager_returns200WithMutableFields` | MANAGER role | 200 + mutable fields |

`DataRetentionSchedulerTest` (6 unit tests, Maven Surefire) covers the scheduler in isolation with Mockito mocks.

Run integration tests:
```bash
cd hlm-backend && ./mvnw failsafe:integration-test
```

Run unit tests:
```bash
cd hlm-backend && ./mvnw test
```

---

## 12. Operational Runbook

### Responding to a Right-to-Erasure Request

1. Identify the contact UUID via the CRM UI or `GET /api/contacts?email=...`.
2. Call `GET /api/gdpr/contacts/{id}/export` → save response as proof of what was held.
3. Call `DELETE /api/gdpr/contacts/{id}/anonymize`.
   - **200**: PII erased. Confirm to the data subject in writing.
   - **409 GDPR_ERASURE_BLOCKED**: Inform the data subject that erasure is partially blocked due to signed contracts subject to legal archiving obligations (RGPD Art. 17(3)(b)). Provide the retention end date (contract date + 10 years) and confirm that no further marketing processing will occur.

### Responding to a Right-of-Access Request

1. Call `GET /api/gdpr/contacts/{id}/export`.
2. Return the JSON payload to the data subject (consider converting to PDF or human-readable format).
3. Respond within 30 days of the request (RGPD Art. 12).

### Responding to a Rectification Request

1. Call `GET /api/gdpr/contacts/{id}/rectify` to see current mutable fields.
2. Apply corrections via `PUT /api/contacts/{id}` with updated values.
3. Confirm corrections to the data subject.

### Checking Retention Scheduler Logs

```bash
# Grep for retention sweep output
docker logs hlm-backend 2>&1 | grep '\[RETENTION\]'
```

Expected output:
```
[RETENTION] Starting daily data retention sweep
[RETENTION] Tenant 11111111-...: anonymized 3 contacts, skipped 1 (active contracts)
[RETENTION] Daily data retention sweep complete
```

### Updating the Privacy Notice

See [Section 7 — Privacy Notice](#7-privacy-notice).
After updating, test the Angular banner by clearing `gdpr_notice_dismissed` from sessionStorage in browser DevTools and refreshing the Contacts page.
