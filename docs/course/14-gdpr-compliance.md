# Module 14 — GDPR Compliance

## Learning Objectives

- Explain why anonymization (not hard delete) is used for erasure
- Describe the data export payload and what it includes
- Identify the erasure block condition and how to clear it

---

## The GDPR Erasure Challenge

A naive implementation would `DELETE FROM contact WHERE id = ?`. But contacts are referenced by:
- `deposit.contact_id`
- `sale_contract.buyer_contact_id`
- `contact_interest.contact_id`
- `portal_token.contact_id`

Deleting the row would violate FK constraints (or cascade-delete financial records, which is worse). Financial records must be retained for legal and accounting reasons.

---

## Anonymization

The platform satisfies GDPR Article 17 (right to erasure) through anonymization:

```java
contact.setFullName("ANONYMIZED");
contact.setFirstName(null);
contact.setLastName(null);
contact.setEmail("anonymized-" + contact.getId() + "@deleted.invalid");
contact.setPhone(null);
contact.setAnonymizedAt(LocalDateTime.now());
```

After anonymization:
- The contact row still exists (FK integrity preserved).
- No PII remains on the row.
- `anonymized_at` provides a verifiable erasure record.
- Deposits, contracts, and interests still reference the contact ID — but the ID no longer resolves to a person.

---

## Erasure Block Guard

If the contact has a `SIGNED` sale contract, erasure is blocked:

```java
if (contractRepo.existsByTenantIdAndBuyerContactIdAndStatus(
        tenantId, contactId, SaleContractStatus.SIGNED)) {
    throw new GdprErasureBlockedException(contactId);
}
```

Response: HTTP 409 / `GDPR_ERASURE_BLOCKED`.

The contract is a legally binding document. The buyer's identity is part of the contract. Erasure while the contract is active would be legally problematic.

---

## Data Export

`DataExportBuilder.build(Contact contact)` assembles a `DataExportResponse` record:

```
DataExportResponse:
  contactId
  exportedAt
  contact (personal fields + consent metadata)
  prospectDetail (budget, preferences, source)
  clientDetail (kind, notary, financing)
  deposits[] (depositRef, amount, status, date)
  contracts[] (property, agreedPrice, status, signedAt)
  interests[] (propertyId, interestStatus)
```

---

## Consent Fields

| Column | Type | Description |
|--------|------|-------------|
| `consent_given` | boolean | Explicit consent obtained |
| `consent_date` | timestamp | Date of consent |
| `consent_method` | varchar | WEB_FORM / EMAIL / VERBAL / WRITTEN |
| `processing_basis` | varchar | CONSENT / CONTRACT / LEGAL_OBLIGATION / LEGITIMATE_INTEREST |

---

## Data Retention Scheduler

`DataRetentionScheduler` runs nightly (02:00) and flags contacts whose `last_activity_date` exceeds the `GDPR_RETENTION_DAYS` threshold (default 1825 days = 5 years) and have no signed contracts.

---

## Source Files

| File | Purpose |
|------|---------|
| `gdpr/service/GdprService.java` | Orchestrates export, rectify, anonymize |
| `gdpr/service/AnonymizationService.java` | PII zeroing |
| `gdpr/service/DataExportBuilder.java` | Assembles export payload |
| `gdpr/service/GdprErasureBlockedException.java` | Thrown when signed contract exists |
| `gdpr/scheduler/DataRetentionScheduler.java` | Nightly retention sweep |
| `contact/domain/Contact.java` | Consent fields |

---

## Exercise

1. Open `AnonymizationService.java` and list every field that is zeroed.
2. Open `GdprService.java` and find the check for signed contracts.
3. Write two integration tests:
   - `anonymize_noSignedContracts_returns204()` — verify `anonymized_at` is set.
   - `anonymize_withSignedContract_returns409()` — verify `GDPR_ERASURE_BLOCKED` code.
4. Export your test contact's data and verify the JSON includes deposit and contract entries.
