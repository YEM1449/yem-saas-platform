# Module 11 — Contact State Machine

## Learning Objectives

- List all `ContactStatus` values and valid transitions
- Explain how invalid transitions are blocked in the service layer
- Describe the relationship between `ContactType` and `ContactStatus`

---

## ContactStatus Values

```
PROSPECT → QUALIFIED_PROSPECT → CLIENT → ACTIVE_CLIENT → COMPLETED_CLIENT → REFERRAL
                                                              ↑ LOST (from many)
```

| Status | Meaning |
|--------|---------|
| `PROSPECT` | Initial lead |
| `QUALIFIED_PROSPECT` | Budget and preferences confirmed |
| `CLIENT` | Formal commitment made |
| `ACTIVE_CLIENT` | Transaction in progress |
| `COMPLETED_CLIENT` | Transaction fully completed |
| `REFERRAL` | Completed client referring others |
| `LOST` | Dropped from the funnel (recoverable) |

---

## Transition Rules

Defined in `ContactStatus.canTransitionTo(ContactStatus target)`:

| From | Allowed targets |
|------|----------------|
| PROSPECT | QUALIFIED_PROSPECT, LOST |
| QUALIFIED_PROSPECT | PROSPECT, CLIENT, LOST |
| CLIENT | ACTIVE_CLIENT, COMPLETED_CLIENT, LOST |
| ACTIVE_CLIENT | COMPLETED_CLIENT, LOST |
| COMPLETED_CLIENT | REFERRAL |
| REFERRAL | _(none — terminal)_ |
| LOST | PROSPECT (re-engagement) |

---

## Enforcement in the Service

`ContactService.updateStatus()` calls `canTransitionTo()` before persisting:

```java
public void updateStatus(UUID contactId, ContactStatus newStatus) {
    UUID tenantId = TenantContext.getTenantId();
    Contact contact = contactRepo.findByTenantIdAndId(tenantId, contactId)
        .orElseThrow(ContactNotFoundException::new);

    if (!contact.getStatus().canTransitionTo(newStatus)) {
        throw new InvalidStatusTransitionException(contact.getStatus(), newStatus);
    }

    contact.setStatus(newStatus);
    contactRepo.save(contact);
}
```

`InvalidStatusTransitionException` maps to HTTP 422 / `INVALID_STATUS_TRANSITION`.

---

## ContactType vs ContactStatus

These are separate concepts:

| Field | Type | Meaning |
|-------|------|---------|
| `contactType` | `ContactType` enum | Business classification: PROSPECT / TEMP_CLIENT / CLIENT |
| `status` | `ContactStatus` enum | Workflow state in the sales funnel |

`ContactType` changes automatically:
- When a deposit is created → contact type → `TEMP_CLIENT`
- When a deposit is confirmed → contact type → `CLIENT`

`ContactStatus` is managed manually by agents and managers to reflect qualification progress.

---

## Source Files

| File | Purpose |
|------|---------|
| `contact/domain/ContactStatus.java` | Enum with transition rules |
| `contact/domain/ContactType.java` | Type classification enum |
| `contact/service/ContactService.java` | `updateStatus()` with validation |
| `contact/service/InvalidStatusTransitionException.java` | Thrown on invalid transitions |

---

## Exercise

1. Open `ContactStatus.java` and read the `ALLOWED_TRANSITIONS` map.
2. Trace what happens when `canTransitionTo(REFERRAL)` is called from `ACTIVE_CLIENT`.
3. Confirm `REFERRAL` has an empty `EnumSet` as its allowed transitions.
4. Write a unit test that calls `contactService.updateStatus(...)` with an invalid transition and asserts `InvalidStatusTransitionException` is thrown.
