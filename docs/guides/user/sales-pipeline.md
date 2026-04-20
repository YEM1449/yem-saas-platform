# Sales Pipeline Guide

This guide explains how the platform supports a deal from reservation to delivery.

## 1. Pipeline Stages

The commercial workflow typically moves through:

1. contact qualification
2. reservation
3. deposit
4. vente progression
5. contract and payment schedule
6. buyer portal follow-up

## 2. Reservations

Use a reservation to create a lightweight hold on a property.

What it does:

- links the buyer and the property
- blocks conflicting progress for the same property
- sets up later conversion to a deposit or vente

## 3. Deposits

Use a deposit when the commitment becomes financial.

What it does:

- formalizes the hold
- supports confirmation or cancellation
- can generate reservation PDF documentation

## 4. Ventes

The `Ventes` area is the operational deal pipeline.

Use it to:

- create the sale record
- follow financing and legal milestones
- record notes and expected closing signals
- attach documents
- manage echeances
- invite the buyer to the portal

Main vente statuses:

```text
COMPROMIS -> FINANCEMENT -> ACTE_NOTARIE -> LIVRE
      \--------------------------------------> ANNULE
```

## 5. Contracts

The `Contracts` area handles the formal contract record and the payment schedule layer.

Use it to:

- create and read contract records
- sign or cancel the formal contract when authorized
- manage payment schedule items and collections

## 6. Echeances And Payments

Use the payment schedule to:

- create planned calls for funds
- issue and send payment items
- record payments
- follow overdue situations

## 7. Buyer Portal Invitation

From the vente flow, invite the buyer to the portal when they should start self-service access.

The invitation sends a one-time access link tied to:

- the buyer email
- the active societe
- the buyer’s owned record set

## 8. Practical Advice

- keep contact data clean before advancing the sale
- treat `Ventes` as the operational cockpit
- treat `Contracts` as the formal agreement and payment layer
- use tasks and notifications to keep follow-up disciplined
