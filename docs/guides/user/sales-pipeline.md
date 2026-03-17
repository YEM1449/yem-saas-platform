# Sales Pipeline — User Guide

This guide covers the full sales workflow: creating reservations, deposits, and sale contracts, understanding state machines, and managing payment schedules.

## Table of Contents

1. [Sales Workflow Overview](#sales-workflow-overview)
2. [Reservations](#reservations)
3. [Deposits](#deposits)
4. [Sale Contracts](#sale-contracts)
5. [Payment Schedules](#payment-schedules)
6. [Deposit Report](#deposit-report)

---

## Sales Workflow Overview

A typical sale progresses through these stages:

```
1. Contact created (prospect)
2. Property reservation (optional lightweight hold)
3. Deposit / acompte (financial commitment)
4. Deposit confirmed → contact becomes client
5. Sale contract created (DRAFT)
6. Contract signed → property marked SOLD
7. Payment schedule items issued and tracked
```

Not all stages are required. A contract can be created without a prior reservation or deposit in some configurations.

---

## Reservations

A reservation is a lightweight, no-commitment hold on a property for a specific contact. It reserves the property for a configurable period (default 7 days) without requiring a financial deposit.

### Creating a Reservation

**Required role:** Admin or Manager

1. Go to **Reservations**.
2. Click **New Reservation**.
3. Select the **Contact** (the interested buyer).
4. Select the **Property** (must be ACTIVE status).
5. Set the **expiry date** (default: today + 7 days).
6. Click **Save**.

The property status changes to **RESERVED**. Only one active reservation is allowed per property.

### Reservation Statuses

| Status | Meaning |
|--------|---------|
| ACTIVE | Property is held for this contact |
| EXPIRED | Expiry date passed without conversion |
| CANCELLED | Manually cancelled |
| CONVERTED_TO_DEPOSIT | Converted to a formal deposit |

Expired reservations are processed automatically by the system each hour.

### Converting a Reservation to a Deposit

1. Open the reservation.
2. Click **Convert to Deposit**.
3. Enter the deposit amount.
4. Click **Confirm**.

The reservation status changes to CONVERTED_TO_DEPOSIT and a new deposit is created in PENDING status.

### Cancelling a Reservation

1. Open the reservation.
2. Click **Cancel**.
3. Confirm.

The property returns to ACTIVE status.

---

## Deposits

A deposit (acompte) is a formal financial commitment from a buyer. It links a contact to a property with a committed amount.

### Creating a Deposit

**Required role:** Admin or Manager

1. Go to **Deposits**.
2. Click **New Deposit**.
3. Select the **Contact**.
4. Select the **Property** (must be ACTIVE or RESERVED).
5. Enter the **amount** and **deposit date**.
6. Click **Save**.

The deposit is created in **PENDING** status. The property status changes to **RESERVED** if not already.

**Note:** You cannot create a deposit on a property that already has an ACTIVE reservation for a different contact (HTTP 409 error). Either cancel the existing reservation first, or convert it to a deposit from the reservation screen.

### Deposit Statuses

| Status | Meaning |
|--------|---------|
| PENDING | Created but not yet confirmed |
| CONFIRMED | Deposit confirmed — contact becomes a client |
| CANCELLED | Deposit was cancelled |
| EXPIRED | Deposit expired without confirmation |

### Confirming a Deposit

**Required role:** Admin or Manager

1. Open the deposit.
2. Click **Confirm**.
3. Confirm the action.

The deposit status changes to CONFIRMED. The linked contact's type changes to CLIENT.

### Cancelling a Deposit

1. Open the deposit.
2. Click **Cancel**.
3. Confirm.

The deposit status changes to CANCELLED and the property returns to ACTIVE status.

### Downloading the Reservation PDF

1. Open a deposit.
2. Click **Download Reservation Document** to generate a PDF reservation slip.

---

## Sale Contracts

A sale contract formalises the property sale, links the buyer and the property, and captures the agreed and list prices.

### Creating a Contract

**Required role:** Admin, Manager, or Agent (for their own sales)

1. Go to **Contracts**.
2. Click **New Contract**.
3. Select:
   - **Project** and **Property**
   - **Buyer contact**
   - **Linked deposit** (the confirmed deposit)
4. Enter:
   - **Agreed price** (the final negotiated price)
   - **List price** (the original asking price — used for discount analytics)
5. Click **Save**.

The contract is created in **DRAFT** status.

**Note:** If the agreed deposit amount in the contract does not match the confirmed deposit amount, the system returns an error (Contract Deposit Mismatch).

### Contract Statuses

| Status | Meaning |
|--------|---------|
| DRAFT | Contract created, not yet signed |
| SIGNED | Contract signed — property marked SOLD |
| CANCELED | Contract cancelled |

### Signing a Contract

**Required role:** Admin or Manager

1. Open the contract.
2. Click **Sign Contract**.
3. Confirm.

The contract status changes to SIGNED. The property status changes to SOLD.

### Cancelling a Contract

**Required role:** Admin or Manager

1. Open the contract.
2. Click **Cancel**.
3. Confirm.

**Note:** Cancelling a SIGNED contract (business rescission) reverts the property to ACTIVE.

### Downloading the Contract PDF

1. Open a contract.
2. Click **Download Contract Document** to generate a PDF.

---

## Payment Schedules

After a contract is signed, you can set up a payment schedule (appels de fonds) to track installment payments.

### Creating Schedule Items

**Required role:** Admin or Manager

1. Open the contract.
2. Click the **Payment Schedule** tab.
3. Click **Add Installment**.
4. Enter:
   - **Label** (e.g., "Acompte 1 — 10% signature")
   - **Amount** due
   - **Due date**
   - **Sequence** (display order)
5. Click **Save**.

### Payment Schedule Item Statuses

| Status | Meaning |
|--------|---------|
| DRAFT | Created, not yet issued to the buyer |
| ISSUED | Officially issued |
| SENT | Notification sent to the buyer via email |
| OVERDUE | Past due date without full payment |
| PAID | Fully paid |
| CANCELED | Cancelled |

### Issuing an Installment

1. Open the payment schedule item.
2. Click **Issue**.

Status changes to ISSUED. This is the official issuance date.

### Sending a Call for Funds

1. Open the payment schedule item.
2. Click **Send** to dispatch an email notification to the buyer.

Status changes to SENT.

### Recording a Payment

1. Open the payment schedule item.
2. Click **Record Payment**.
3. Enter the amount received and the payment date.
4. Click **Save**.

When the total recorded payments equal or exceed the item amount, the status automatically changes to PAID.

---

## Deposit Report

**Required role:** Admin or Manager

Go to **Deposits** and click **View Report** to see a summary of deposits grouped by agent, with totals for each status (PENDING, CONFIRMED, CANCELLED, EXPIRED).

This report is useful for monthly team performance reviews and pipeline health checks.
