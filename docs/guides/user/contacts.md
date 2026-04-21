# Contacts Guide

Contacts are the entry point to most commercial workflows.

## 1. What A Contact Represents

A contact can be:

- an early prospect
- a qualified prospect
- a client in progress
- a completed client
- a referral source

## 2. Creating A Contact

Authorized users can create a contact with the core identity information:

- first name
- last name
- phone and or email
- notes and basic context

Good practice:

- capture enough information to enable follow-up
- avoid duplicate emails inside the same societe

## 3. Qualification Workflow

Use the prospect conversion flow when the lead becomes actionable.

Typical enrichment:

- budget range
- acquisition source
- notes about the project or property need

## 4. Contact Status Model

```text
PROSPECT -> QUALIFIED_PROSPECT -> CLIENT -> ACTIVE_CLIENT -> COMPLETED_CLIENT
                                           \-> LOST
COMPLETED_CLIENT -> REFERRAL
```

Not every transition should be done manually. Some sales events move the contact forward automatically.

## 5. Property Interests

Use interests to connect a contact to the properties they are considering.

Benefits:

- better sales follow-up
- easier matching between leads and inventory
- better context in the contact timeline

## 6. Timeline

The contact timeline brings together:

- workflow events
- notifications
- outbound communication traces
- status changes

Use it when you need the full history before calling or updating the client.

## 7. GDPR Considerations

Contacts also carry privacy data such as:

- consent state
- processing basis
- retention settings
- anonymization state

See [gdpr-rights.md](gdpr-rights.md) for the privacy workflow.
