# Module 14: GDPR And Privacy By Design

## Why This Matters

Privacy is not a sidecar in this product. It is embedded in business entities and workflows.

## Learning Goals

- identify where personal data lives
- understand export, anonymization, and retention flows
- understand why legal constraints can block destructive actions

## Files To Study

- [../../hlm-backend/src/main/java/com/yem/hlm/backend/gdpr/api/GdprController.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/gdpr/api/GdprController.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/gdpr/scheduler/DataRetentionScheduler.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/gdpr/scheduler/DataRetentionScheduler.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/contact/domain/Contact.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/contact/domain/Contact.java)
- [../guides/engineer/gdpr-compliance.md](../guides/engineer/gdpr-compliance.md)

## Privacy Design Ideas

- consent fields belong in the contact model
- company defaults belong in the societe model
- retention is operationalized through schedulers
- anonymization must preserve legal and workflow integrity

## Exercise

List the privacy-related fields visible on `Contact` and explain what product questions each one answers.
