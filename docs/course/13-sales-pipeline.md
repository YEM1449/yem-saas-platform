# Module 13 — Sales Pipeline

## Learning Objectives

- Trace the full sales flow from reservation to signed contract
- Identify the business rules that gate each step
- Explain payment schedule item states and how PAID is triggered

---

## Pipeline Overview

```
[Optional] Reservation (ACTIVE → CONVERTED_TO_DEPOSIT)
         ↓
    Deposit (PENDING → CONFIRMED)
         ↓
    SaleContract (DRAFT → SIGNED)
         ↓
    PaymentScheduleItem (DRAFT → ISSUED → SENT → PAID)
```

Each stage has preconditions and automatic side effects.

---

## Reservation → Deposit

**Precondition:** Property must be ACTIVE. Only one ACTIVE reservation per property.

**Conversion:** `PUT /api/reservations/{id}/convert`
- Creates a new `Deposit` in PENDING status.
- Sets reservation status to `CONVERTED_TO_DEPOSIT`.
- Property remains RESERVED.

**Or** a deposit can be created directly without a prior reservation:
- `DepositService.create()` checks if an ACTIVE reservation exists for the property.
- If yes → `PropertyAlreadyReservedException` (HTTP 409).
- If no → deposit created, property → RESERVED.

---

## Deposit → Confirmed

`PUT /api/deposits/{id}/confirm`
- Deposit status → CONFIRMED
- Contact type → CLIENT
- Property remains RESERVED (awaiting contract)

---

## Deposit → Contract

`POST /api/contracts`
- Requires: `depositId` (must be CONFIRMED)
- Validates: `agreedPrice` deposit amount matches deposit amount → `ContractDepositMismatchException` (HTTP 422)
- Validates: Property not already SOLD → `PropertyAlreadySoldException` (HTTP 409)
- Creates contract in DRAFT status

---

## Contract → Signed

`PUT /api/contracts/{id}/sign`
- Contract status → SIGNED
- Property status → SOLD
- Commission calculated and persisted (if a commission rule exists)

---

## Payment Schedule

After contract signing, the payment schedule is set up manually:

1. `POST /api/contracts/{id}/schedule` — create a `PaymentScheduleItem` (DRAFT)
2. `PUT /api/contracts/{contractId}/schedule/{itemId}/issue` → ISSUED
3. `PUT /api/contracts/{contractId}/schedule/{itemId}/send` → SENT (email dispatched)
4. `POST /api/contracts/{contractId}/schedule/{itemId}/payments` — record a payment
5. When `sum(payments) >= item.amount` → item → PAID (automatic)

---

## Overdue Items

A daily scheduler (`payments/ReminderService`, cron 06:00) scans for items where:
```
status IN (ISSUED, SENT) AND due_date < today
```
Sets them to OVERDUE.

---

## Business Rules Summary

| Rule | Enforcement |
|------|------------|
| One ACTIVE reservation per property | `ReservationService.create()` + pessimistic lock |
| Deposit blocked by ACTIVE reservation | `DepositService.create()` check |
| Contract on non-SOLD property only | `SaleContractService.create()` check |
| Deposit amount must match contract | `SaleContractService.create()` validation |
| GDPR erasure blocked by SIGNED contract | `GdprService.anonymizeContact()` check |

---

## Source Files

| File | Purpose |
|------|---------|
| `reservation/service/ReservationService.java` | Reservation lifecycle |
| `deposit/service/DepositService.java` | Deposit lifecycle, property transition |
| `contract/service/SaleContractService.java` | Contract creation, signing |
| `payments/service/PaymentScheduleService.java` | Schedule item CRUD |
| `payments/service/CallForFundsWorkflowService.java` | Issue, send, PAID transition |
| `payments/service/ReminderService.java` | Overdue scanner |

---

## Exercise

1. Open `DepositService.java` and find the check for `ACTIVE` reservations.
2. Open `SaleContractService.java` and find the deposit amount mismatch check.
3. Create a full end-to-end test:
   - Create a property (ACTIVE)
   - Create a reservation
   - Convert reservation to deposit
   - Confirm the deposit
   - Create a contract
   - Sign the contract
   - Assert property status = SOLD
4. Verify the commission is persisted after signing (if commission rules are set).
