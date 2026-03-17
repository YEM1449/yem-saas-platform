# Module 12 — Property Lifecycle

## Learning Objectives

- List all `PropertyStatus` values and describe the typical flow
- Explain how automatic status transitions work (deposit, contract)
- Describe the soft-delete pattern and why hard delete is avoided

---

## PropertyStatus Values

```
DRAFT → ACTIVE → RESERVED → SOLD
                      ↓
                  WITHDRAWN → ARCHIVED
         (from ACTIVE too)
```

| Status | Meaning |
|--------|---------|
| `DRAFT` | Being prepared; not yet visible to prospects |
| `ACTIVE` | On the market; available for deposit/reservation |
| `RESERVED` | Has an active deposit or reservation |
| `SOLD` | Sale contract signed |
| `WITHDRAWN` | Removed from market without being sold |
| `ARCHIVED` | Historical; no longer actively managed |

---

## Automatic Transitions

| Trigger | Property transition |
|---------|-------------------|
| Deposit created | → RESERVED |
| Reservation created | → RESERVED |
| Deposit cancelled | → ACTIVE (if no other active reservation) |
| Reservation cancelled/expired | → ACTIVE (if no active deposit) |
| Contract signed | → SOLD |

These transitions are managed in the respective service classes (`DepositService`, `ReservationService`, `SaleContractService`).

---

## Manual Transitions

ADMIN and MANAGER can manually change status (e.g., to WITHDRAWN):

```
ACTIVE → WITHDRAWN (manually removed from market)
SOLD | WITHDRAWN → ARCHIVED (historical archive)
```

---

## Pessimistic Locking for Reservations

When creating a reservation or deposit, a race condition could occur:
- Thread A checks: property is ACTIVE.
- Thread B checks: property is ACTIVE.
- Thread A saves reservation → property is now RESERVED.
- Thread B saves reservation → double-reservation!

`ReservationService.create()` prevents this with a pessimistic write lock:

```java
Property property = propertyRepo.findByTenantIdAndIdForUpdate(tenantId, propertyId)
    // SELECT ... FOR UPDATE
    .orElseThrow(PropertyNotFoundException::new);

if (property.getStatus() != PropertyStatus.ACTIVE) {
    throw new PropertyNotAvailableException();
}
```

`FOR UPDATE` locks the property row for the duration of the transaction.

---

## Soft Delete

`DELETE /api/properties/{id}` (ADMIN only) does NOT remove the row:

```java
public void softDelete(UUID propertyId) {
    property.setDeletedAt(LocalDateTime.now());
    property.setStatus(PropertyStatus.DELETED);
    propertyRepo.save(property);
}
```

All list queries filter:
```java
findByTenantIdAndDeletedAtIsNull(tenantId, pageable)
```

Why soft delete instead of hard delete? Properties are referenced by deposits, contracts, media, and contact interests. Hard deletion would violate FK constraints or cascade-delete financial records.

---

## Source Files

| File | Purpose |
|------|---------|
| `property/domain/PropertyStatus.java` | Status enum |
| `property/service/PropertyService.java` | Status transitions, soft delete |
| `property/repo/PropertyRepository.java` | `deleted_at IS NULL` filter |
| `deposit/service/DepositService.java` | Sets property to RESERVED |
| `contract/service/SaleContractService.java` | Sets property to SOLD on sign |
| `reservation/service/ReservationService.java` | Pessimistic lock + RESERVED |

---

## Exercise

1. Open `PropertyRepository.java` and find all query methods.
2. Verify every list query includes `deleted_at IS NULL` or `DeletedAtIsNull`.
3. Open `SaleContractService.java` and find the line that sets property status to SOLD.
4. Write a test: create a property, create a reservation, cancel the reservation, verify the property status reverts to ACTIVE.
