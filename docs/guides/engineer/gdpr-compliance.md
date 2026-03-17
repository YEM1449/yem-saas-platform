# GDPR Compliance Guide — Engineer Guide

This guide covers the GDPR and Moroccan Law 09-08 compliance features implemented in the platform: consent tracking, data export, rectification, erasure (anonymization), and data retention.

## Table of Contents

1. [Regulatory Context](#regulatory-context)
2. [Consent Tracking](#consent-tracking)
3. [Data Export (Right of Access)](#data-export-right-of-access)
4. [Rectification (Right to Correct)](#rectification-right-to-correct)
5. [Erasure / Anonymization (Right to Be Forgotten)](#erasure--anonymization-right-to-be-forgotten)
6. [Privacy Notice](#privacy-notice)
7. [Data Retention Scheduler](#data-retention-scheduler)
8. [Erasure Block Guard](#erasure-block-guard)
9. [GDPR API Reference](#gdpr-api-reference)
10. [Testing GDPR Features](#testing-gdpr-features)

---

## Regulatory Context

The platform implements data subject rights under:
- **GDPR (EU)** — Articles 15 (access), 16 (rectification), 17 (erasure), 13/14 (transparency)
- **Moroccan Law 09-08** — Right of access, correction, and opposition for personal data

All GDPR operations require `ROLE_ADMIN` (for erasure) or `ROLE_ADMIN` / `ROLE_MANAGER` (for export and rectification). No external subject portal for self-service requests is implemented; staff process requests on behalf of data subjects.

---

## Consent Tracking

The `contact` table stores consent metadata:

| Column | Type | Description |
|--------|------|-------------|
| `consent_given` | `boolean` | Whether the data subject gave consent |
| `consent_date` | `timestamp` | When consent was obtained |
| `consent_method` | `varchar` | How consent was obtained |
| `processing_basis` | `varchar` | Legal basis for processing |

### `ConsentMethod` enum

| Value | Meaning |
|-------|---------|
| `WEB_FORM` | Online consent form |
| `EMAIL` | Email confirmation |
| `VERBAL` | Verbal consent (documented by staff) |
| `WRITTEN` | Signed paper consent |

### `ProcessingBasis` enum

| Value | Meaning |
|-------|---------|
| `CONSENT` | Data subject gave explicit consent |
| `CONTRACT` | Processing is necessary for a contract |
| `LEGAL_OBLIGATION` | Processing required by law |
| `LEGITIMATE_INTEREST` | Legitimate interest of the controller |

### Setting consent when creating a contact

Include consent fields in `CreateContactRequest`:
```json
{
  "fullName": "Ahmed Benali",
  "email": "ahmed@example.com",
  "consentGiven": true,
  "consentDate": "2026-03-17T10:00:00",
  "consentMethod": "WEB_FORM",
  "processingBasis": "CONSENT"
}
```

---

## Data Export (Right of Access)

### Endpoint

```
GET /api/gdpr/contacts/{contactId}/export
Authorization: Bearer {ADMIN or MANAGER token}
```

### What is included

The `DataExportBuilder` assembles a `DataExportResponse` containing:
- Contact personal fields (name, email, phone, address)
- Consent metadata
- Prospect detail (budget, preferences, source)
- Client detail (kind, financing, notary)
- All deposits linked to the contact
- All sale contracts linked to the contact as buyer
- All contact interests (properties they expressed interest in)

### Rate limiting

The GDPR export endpoint is protected by the general `RateLimiterService` with key `gdpr-export:{tenantId}:{contactId}`. This prevents abuse (e.g., scraping all contacts via repeated export calls). Rate limit configuration is shared with `RATE_LIMIT_LOGIN_*` defaults; adjust separately if needed.

### Response format

```json
{
  "contactId": "...",
  "exportedAt": "2026-03-17T10:00:00Z",
  "contact": {
    "fullName": "Ahmed Benali",
    "email": "ahmed@example.com",
    "phone": "+212600000000",
    "consentGiven": true,
    "consentDate": "2026-01-15T09:00:00",
    "consentMethod": "WEB_FORM",
    "processingBasis": "CONSENT"
  },
  "prospectDetail": { ... },
  "clientDetail": { ... },
  "deposits": [ ... ],
  "contracts": [ ... ],
  "interests": [ ... ]
}
```

---

## Rectification (Right to Correct)

### Endpoint

```
PUT /api/gdpr/contacts/{contactId}/rectify
Authorization: Bearer {ADMIN or MANAGER token}
```

### Request body

```json
{
  "fullName": "Ahmed Benali",
  "email": "ahmed.new@example.com",
  "phone": "+212611111111"
}
```

Only the fields provided are updated. Omitted fields are unchanged. Rectification does not change consent metadata or financial records.

### Response

Returns the updated `RectifyContactResponse` with the corrected fields and an `updatedAt` timestamp.

---

## Erasure / Anonymization (Right to Be Forgotten)

### Why Anonymization Instead of Hard Delete

Hard deletion would violate foreign key constraints on:
- `deposit.contact_id`
- `sale_contract.buyer_contact_id`
- `contact_interest.contact_id`
- `portal_token.contact_id`

Financial and legal records must be retained even after an erasure request. Anonymization satisfies GDPR Article 17 by making re-identification impossible while preserving structural integrity.

### Erasure Process

`DELETE /api/gdpr/contacts/{contactId}/anonymize` triggers `GdprService.anonymizeContact()`:

1. Check for `SIGNED` sale contracts. If found, throw `GdprErasureBlockedException` (HTTP 409).
2. Call `AnonymizationService.anonymize(contact)`:
   - `full_name` → `"ANONYMIZED"`
   - `first_name`, `last_name` → `null`
   - `email` → `"anonymized-{contactId}@deleted.invalid"`
   - `phone` → `null`
   - `anonymized_at` → `now()`
3. Clear `ProspectDetail` fields (budget, preferences, source, notes).
4. Clear `ClientDetail` fields (notary, financing details).

### After Anonymization

- The `contact` row still exists (preserving FK integrity).
- The `anonymized_at` column provides a verifiable audit record.
- Financial records (deposits, contracts) reference the anonymized contact row by ID.
- The portal token table removes the contact's email but retains the token rows for FK integrity.

---

## Privacy Notice

### Endpoint

```
GET /api/gdpr/privacy-notice
```

No authentication required. Returns the privacy notice text.

### Implementation

`PrivacyNoticeLoader` reads the privacy notice from a classpath resource (`classpath:gdpr/privacy-notice.txt` or similar). Update the resource file to change the notice text without code changes.

---

## Data Retention Scheduler

`DataRetentionScheduler` runs daily at `DATA_RETENTION_CRON` (default `0 0 2 * * *`, i.e., 02:00).

Default retention period: `GDPR_RETENTION_DAYS` (default 1825 days = 5 years).

The scheduler identifies contacts where:
```
last_activity_date < now() - retention_days
AND anonymized_at IS NULL
AND (no SIGNED contracts exist)
```

These contacts are flagged for review. Automated anonymization or manual review workflow depends on your operational process.

### Configuration

```bash
GDPR_RETENTION_DAYS=1825              # 5 years
DATA_RETENTION_CRON=0 0 2 * * *      # 02:00 daily
```

---

## Erasure Block Guard

Erasure is blocked when a `SIGNED` sale contract exists for the contact. This is a legal requirement: the buyer's identity cannot be erased while a contract is legally binding.

```java
// GdprService.anonymizeContact()
boolean hasSignedContracts = contractRepo
    .existsByTenantIdAndBuyerContactIdAndStatus(
        tenantId, contactId, SaleContractStatus.SIGNED);

if (hasSignedContracts) {
    throw new GdprErasureBlockedException(contactId);
}
```

The block returns:
```json
{
  "code": "GDPR_ERASURE_BLOCKED",
  "message": "Cannot anonymize contact: active signed contracts exist",
  "status": 409
}
```

To perform erasure of a contact with signed contracts, the contracts must first be cancelled (requires ADMIN or MANAGER) — after legal and contractual obligations are fulfilled.

---

## GDPR API Reference

| Method | Path | Role | Description |
|--------|------|------|-------------|
| `GET` | `/api/gdpr/contacts/{id}/export` | ADMIN, MANAGER | Export all personal data |
| `PUT` | `/api/gdpr/contacts/{id}/rectify` | ADMIN, MANAGER | Correct personal data |
| `DELETE` | `/api/gdpr/contacts/{id}/anonymize` | ADMIN | Anonymize contact |
| `GET` | `/api/gdpr/privacy-notice` | Public | Return privacy notice text |

---

## Testing GDPR Features

### Test anonymization

```java
@Test
void anonymize_noSignedContracts_succeeds() throws Exception {
    // Create contact (admin)
    String contactId = createContact(adminBearer);

    // Anonymize
    mockMvc.perform(delete("/api/gdpr/contacts/" + contactId + "/anonymize")
            .header("Authorization", "Bearer " + adminBearer))
        .andExpect(status().isNoContent());

    // Verify PII is zeroed
    Contact c = contactRepo.findById(UUID.fromString(contactId)).orElseThrow();
    assertThat(c.getFullName()).isEqualTo("ANONYMIZED");
    assertThat(c.getEmail()).startsWith("anonymized-");
    assertThat(c.getAnonymizedAt()).isNotNull();
}

@Test
void anonymize_withSignedContract_returns409() throws Exception {
    String contactId = createContactWithSignedContract(adminBearer);

    mockMvc.perform(delete("/api/gdpr/contacts/" + contactId + "/anonymize")
            .header("Authorization", "Bearer " + adminBearer))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("GDPR_ERASURE_BLOCKED"));
}
```

### Test data export

```java
@Test
void exportData_returns200WithContactFields() throws Exception {
    String contactId = createContact(adminBearer);

    mockMvc.perform(get("/api/gdpr/contacts/" + contactId + "/export")
            .header("Authorization", "Bearer " + adminBearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contact.email").isNotEmpty())
        .andExpect(jsonPath("$.exportedAt").isNotEmpty());
}
```
