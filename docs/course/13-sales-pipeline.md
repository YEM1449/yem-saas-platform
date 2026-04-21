# Module 13: Sales Pipeline

## Why This Matters

The platform exists to move deals forward in a controlled, traceable way.

## Learning Goals

- understand how reservations, deposits, ventes, contracts, and payments fit together
- understand where each workflow stage lives in code and UI
- understand why `vente` and `contract` are both needed

## Commercial Sequence

```text
contact
  -> reservation
  -> deposit
  -> vente
  -> contract
  -> payment schedule
  -> portal follow-up
```

## Files To Study

- [../../hlm-backend/src/main/java/com/yem/hlm/backend/reservation/api/ReservationController.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/reservation/api/ReservationController.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/deposit/api/DepositController.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/deposit/api/DepositController.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/vente/api/VenteController.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/vente/api/VenteController.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/contract/api/ContractController.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/contract/api/ContractController.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/payments/api/PaymentScheduleController.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/payments/api/PaymentScheduleController.java)

## Key Insight

`vente` is the operational pipeline.
`contract` is the formal agreement and payment structure.
They overlap in business meaning but not in technical responsibility.

## Exercise

Map one real deal from contact creation to buyer portal invitation, naming the module that owns each stage.
