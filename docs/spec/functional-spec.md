# Functional Specification

This specification reconstructs implemented behavior from controllers, services, entities, and integration tests.

## 1. Actors

| Actor | Description |
| --- | --- |
| `SUPER_ADMIN` | platform operator managing societes and privileged membership actions |
| `ADMIN` | company administrator |
| `MANAGER` | company operator with broad CRM access but limited administrative power |
| `AGENT` | company user focused on day-to-day sales execution |
| Portal buyer | contact using one-time magic-link access to the client portal |
| Scheduler/system | background jobs operating on reminders, expirations, retention, and outbox delivery |

## 2. Authentication and Session Behavior

### CRM login

Implemented behavior:

- login uses only `email` and `password`
- account lockout and rate limiting run before normal authentication success
- disabled users cannot obtain valid CRM sessions
- a user with one active membership receives a full JWT
- a user with multiple active memberships receives a partial token and societe list
- `POST /auth/switch-societe` exchanges that partial token for a full scoped JWT
- a platform `SUPER_ADMIN` with no memberships receives a platform token without `sid`

### Invitation activation

Implemented behavior:

- invited users validate a public invitation token
- activation requires password confirmation and CGU consent
- activation returns a normal login response

### Portal login

Implemented behavior:

- magic-link requests are accepted through `email + societeKey`
- responses avoid exposing whether the email exists
- verification consumes the token once and returns a portal JWT

## 3. Company and Membership Administration

### Societe administration

Implemented behavior:

- `SUPER_ADMIN` can create, view, update, suspend, reactivate, and inspect societes
- `SUPER_ADMIN` can list members of any societe and impersonate a member
- societes expose branding, compliance, quota, and subscription fields in the management UI/API

### Company member management

Implemented behavior:

- `ADMIN` and `SUPER_ADMIN` can invite, reinvite, edit, remove, unblock, and anonymize members
- `MANAGER` can list members and export user data, but cannot modify membership
- `AGENT` cannot list or manage members
- only `SUPER_ADMIN` can assign the `ADMIN` role
- last-admin protection prevents removing or demoting the last company `ADMIN`

## 4. CRM Master Data

### Projects

Implemented behavior:

- `ADMIN` and `MANAGER` can create and update projects
- `ADMIN` can archive a project
- all CRM roles can list and read projects
- project KPI views are available to `ADMIN` and `MANAGER`
- archived projects remain in the system and are not hard-deleted

### Properties

Implemented behavior:

- `ADMIN` and `MANAGER` can create, update, and import properties
- `ADMIN` can soft-delete properties
- all CRM roles can list and read properties
- create and update enforce property-type-specific required fields
- properties belong to an active project when created
- property references are unique inside a societe
- property lifecycle is driven partly by commercial workflows, not only by direct CRUD

### Contacts

Implemented behavior:

- `ADMIN` and `MANAGER` can create and update contacts
- all CRM roles can list and read contacts
- duplicate email within a societe is rejected
- contact status transitions are validated
- contacts support prospect qualification and client conversion workflows
- contacts have a unified timeline composed from audit, outbox, notification, and status events
- contacts persist privacy-related consent fields and anonymization metadata

## 5. Commercial Workflow

### Reservation workflow

Implemented behavior:

- `ADMIN` and `MANAGER` can create and cancel reservations
- all CRM roles can list and view reservations
- reservations require the property to be available
- active reservations expire automatically through a scheduler
- reservation conversion creates a formal deposit workflow

State model:

```text
ACTIVE -> EXPIRED
ACTIVE -> CANCELLED
ACTIVE -> CONVERTED_TO_DEPOSIT
```

### Deposit workflow

Implemented behavior:

- `ADMIN` and `MANAGER` can create, confirm, cancel, and report on deposits
- all CRM roles can read individual deposits and download reservation PDFs
- deposit creation locks the property row and prevents conflicting holds
- confirming a deposit promotes the property and buyer state
- canceling a non-confirmed deposit releases property state and may revert contact state
- pending deposits can expire automatically

State model:

```text
PENDING -> CONFIRMED
PENDING -> CANCELLED
PENDING -> EXPIRED
```

### Contract workflow

Implemented behavior:

- all CRM roles can create draft contracts
- `AGENT` creation is scoped to self
- `ADMIN` and `MANAGER` can sign and cancel contracts
- all CRM roles can list and read contracts
- `AGENT` visibility is restricted to own contracts
- signing a contract captures buyer snapshot data and marks the property sold
- canceling a signed contract reverts the property to `RESERVED` or `ACTIVE` depending on deposit state

State model:

```text
DRAFT -> SIGNED
DRAFT -> CANCELED
SIGNED -> CANCELED
```

## 6. Collections and Commissions

### Payment schedule

Implemented behavior:

- payment schedule items are created under a contract
- only `ADMIN` and `MANAGER` can modify schedule items
- schedule items can be issued, sent, canceled, and paid partially
- all CRM roles can read schedule items and PDFs
- reminders and overdue transitions are automated

State model:

```text
DRAFT -> ISSUED
ISSUED -> SENT
SENT -> OVERDUE
ISSUED|SENT|OVERDUE -> CANCELED
ISSUED|SENT|OVERDUE -> PAID
```

### Commissions

Implemented behavior:

- `AGENT` can read own commissions
- `ADMIN` and `MANAGER` can query commission results by agent and period
- only `ADMIN` can manage commission rules
- project-level rules override societe-level rules
- when no rule exists, commission calculation falls back to zero-valued results

## 7. Messaging, Notifications, Documents, and Tasks

### Messages

Implemented behavior:

- all CRM roles can compose outbound messages
- send requests queue outbox records rather than synchronously sending
- list views support filtering by channel, status, contact, and date range

### Notifications

Implemented behavior:

- all CRM roles can list their notifications and mark them as read
- business services create notifications for deposit and payment events

### Documents

Implemented behavior:

- all CRM roles can upload, list, and download generic documents for supported entity types
- only `ADMIN` and `MANAGER` can delete documents

### Tasks

Implemented behavior:

- all CRM roles can create and update tasks
- only `ADMIN` can delete tasks
- task listing defaults to the current user unless filters are supplied
- tasks may be linked to a contact or property

## 8. Dashboards and Reporting

Implemented behavior:

- commercial summary and sales dashboards exist under `/api/dashboard/commercial`
- receivables dashboard exists under `/api/dashboard/receivables`
- cash dashboard exists under `/api/dashboard/commercial/cash`
- `AGENT` scope is server-enforced to self for agent-sensitive views
- dashboard refresh events are available via SSE

## 9. Buyer Portal

Implemented behavior:

- buyers can obtain a one-time link using `email + societeKey`
- portal users can view only their own contracts
- portal users can view payment schedules only for owned contracts
- portal users can view property details only where contract ownership authorizes it
- portal users can fetch lightweight tenant branding for the shell

## 10. Privacy and Compliance

Implemented behavior:

- contacts support export, rectification view, privacy notice, and anonymization
- users support export and anonymization through company-member management
- signed contracts block contact anonymization
- draft contract snapshots can be anonymized when user/contact data is erased

## 11. Confirmed Functional Gaps

These are functional inconsistencies or incomplete behaviors confirmed from code:

- The backend supports multi-societe login selection, but the current Angular login flow does not implement the `requiresSocieteSelection` branch.
- Societe suspension exists as administrative data but was not found as an enforced access-control rule.
- Societe quotas for contacts, properties, and projects exist in the data model but were not found enforced in the corresponding services.
- A legacy `/api/admin/users` backend surface still exists even though the active frontend uses `/api/mon-espace/utilisateurs`.

## 12. Needs Clarification

- Should `convert-to-client` keep its current backward-compatible name even though it now creates a deposit-style reservation flow?
- Should societe suspension block login, all API access, or only selected write operations?
- Should resource quotas be enforced immediately for contacts, properties, and projects, or are they informational today?
