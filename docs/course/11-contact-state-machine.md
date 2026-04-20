# Module 11: Contact State Machine

## Why This Matters

Contacts are the bridge between lead management and sales execution.

## Learning Goals

- understand the contact lifecycle
- understand which transitions are manual and which are workflow-driven
- connect contacts to interests, ventes, and GDPR rules

## Contact Lifecycle

```text
PROSPECT -> QUALIFIED_PROSPECT -> CLIENT -> ACTIVE_CLIENT -> COMPLETED_CLIENT
                                           \-> LOST
COMPLETED_CLIENT -> REFERRAL
LOST -> PROSPECT
```

## Files To Study

- [../../hlm-backend/src/main/java/com/yem/hlm/backend/contact/domain/ContactStatus.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/contact/domain/ContactStatus.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/contact/api/ContactController.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/contact/api/ContactController.java)
- [../guides/user/contacts.md](../guides/user/contacts.md)

## Important Insights

- not all states are supposed to be changed manually
- sales workflows can push a contact into a more advanced lifecycle stage
- contact quality affects pipeline clarity and reporting quality

## Exercise

Trace how a contact moves from `QUALIFIED_PROSPECT` to `ACTIVE_CLIENT` in the broader commercial flow.
