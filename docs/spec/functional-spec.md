# Functional Specification

This specification describes the implemented business behavior of the platform from a user and workflow perspective.

## 1. Workflow Overview

The platform supports four major operating loops:

1. Platform governance by `SUPER_ADMIN`
2. CRM administration by societe admins and managers
3. Sales execution from lead to delivery
4. Buyer self-service through the portal

## 2. Platform Governance Workflows

### Societe lifecycle

`SUPER_ADMIN` can:

- create a societe
- edit branding, legal, quota, and subscription data
- inspect compliance and commercial stats
- suspend and reactivate the societe
- inspect members and invite or manage them through platform routes

### Impersonation

`SUPER_ADMIN` can impersonate a member to investigate or support an issue.

Expected functional behavior:

- impersonation is explicit, not silent
- the impersonated session carries tracking information
- the UI must make the impersonated state obvious
- the session can be ended deliberately

## 3. Staff Authentication Workflows

### Standard login

1. User submits email and password.
2. The backend validates credentials, lockout status, and active memberships.
3. If the user has one active membership, the session is established directly.
4. If the user has several active memberships, the user must choose a societe before receiving the final session.

### Invitation activation

1. An admin or manager invites a user.
2. The user opens the activation link.
3. The user sets a password and accepts the required agreement state.
4. The backend activates the user and starts a normal authenticated session.

## 4. Buyer Portal Authentication Workflow

1. A buyer requests or receives a magic link using email plus societe key.
2. The platform sends a time-limited one-time link.
3. Verification consumes the token once.
4. The buyer receives a portal session limited to owned records.

## 5. CRM Administration Workflows

### Company member management

Authorized users can:

- list active members
- invite new members
- resend invitations
- modify member profile and role where permitted
- remove or deactivate members
- unblock locked accounts
- export or anonymize member data where the module allows it

Business constraints:

- only `SUPER_ADMIN` can assign `ADMIN`
- societe-level admins manage the rest of the member lifecycle

### Template management

Admins can:

- list available templates
- edit a template source
- preview generated output
- reset a template when necessary

## 6. CRM Master Data Workflows

### Projects, immeubles, tranches, and properties

Authorized staff can:

- create and maintain projects
- organize buildings with `immeuble`
- generate or manage tranches
- create or import property inventory
- update editorial property status
- attach media and documents

Business expectations:

- property references are unique inside a societe
- sold and reserved states are primarily driven by commercial workflows
- deleted property behavior is soft-delete oriented

### 2D plan de commercialisation

The project detail page exposes a 2D floor-stack view of all buildings, organized by tranche.

Expected functional behavior:

- a tab bar switches between "Aperçu" (KPIs and documents) and "Plan de commercialisation" (2D view)
- each tranche is browsable through a prev/next pager with dot indicators
- when a tranche has more than one building, a tab row selects the active building
- each building renders as a vertical stack of floor rows, highest floor at top, RDC at bottom
- each unit is a coloured card showing reference code, surface, and price
- a legend bar shows live counts per status and a computed absorption rate
- clicking a legend chip filters the floor stack to only units of that status
- clicking a unit card opens a detail panel showing price, prix/m², exposition, parkings, chambres, and a parcours juridique stepper
- the parcours juridique stepper highlights the current legal stage based on property status
- for available or reserved units, the panel offers a quick link to create a vente
- for any unit, the panel links to the full property detail page

Status colour rules:

| Display | Underlying property status | Visual |
| --- | --- | --- |
| Disponible | ACTIVE | solid green |
| Brouillon | DRAFT | warm beige diagonal hatch |
| Réservé | RESERVED | solid orange-red |
| Vendu | SOLD | solid dark charcoal |
| Retiré | WITHDRAWN, ARCHIVED | neutral grey diagonal hatch |

Absorption formula: `(SOLD + RESERVED) / (total − DRAFT) × 100`

### Contacts and interests

Authorized staff can:

- create a contact
- update a contact profile
- qualify a contact as a prospect
- convert a contact to a client
- track property interests
- read a unified timeline of sales and communication activity

Contact lifecycle:

```text
PROSPECT -> QUALIFIED_PROSPECT -> CLIENT -> ACTIVE_CLIENT -> COMPLETED_CLIENT
                                           \-> LOST
COMPLETED_CLIENT -> REFERRAL
LOST -> PROSPECT
```

## 7. Sales Pipeline Workflows

### Reservation

Purpose: create a short-lived property hold before financial commitment.

Flow:

1. User creates reservation for a contact and a property.
2. Property becomes unavailable for conflicting sales actions.
3. Reservation can be canceled, expire automatically, or be converted to a deposit.

### Deposit

Purpose: formalize financial intent on a property.

Flow:

1. User creates a deposit.
2. The system guards against conflicting holds.
3. Deposit can be confirmed, canceled, or expire if pending too long.
4. Reservation PDF can be generated for the deposit.

### Vente

Purpose: manage the deal through commercial and legal milestones.

Flow:

1. User creates a vente directly or from a reservation context.
2. User advances the sale across defined stages.
3. Financing information, deadlines, and notes are captured.
4. Echeances and documents are attached as the deal matures.
5. Buyer portal invitation can be issued from the vente.

Vente lifecycle:

```text
COMPROMIS -> FINANCEMENT -> ACTE_NOTARIE -> LIVRE
      \--------------------------------------> ANNULE
```

### Contract

Purpose: manage the formal legal contract record and linked payment schedule.

Flow:

1. User creates a draft contract.
2. Authorized user signs or cancels it.
3. Signed contracts may affect property and downstream payment behavior.
4. PDF download is available.

Contract lifecycle:

```text
DRAFT -> SIGNED
DRAFT -> CANCELED
SIGNED -> CANCELED
```

### Payment schedule and collection

Authorized users can:

- create schedule items
- update or delete schedule items
- issue and send payment calls
- cancel schedule items
- register payments
- run reminders
- read cash and receivables dashboards

## 8. Productivity And Communication Workflows

### Tasks

Users can:

- create tasks
- update tasks
- list their own tasks by default
- filter by assignee or status
- view tasks linked to a contact or property

### Notifications

Users can:

- list notifications
- mark notifications as read
- use notifications as a lightweight awareness layer for operational events

### Outbound messages

Users can:

- create outbound messages
- review outbound message status and history
- rely on asynchronous delivery instead of blocking business transactions

### Audit

Authorized users can:

- review commercial workflow history
- inspect operational events for troubleshooting and governance

## 9. 3D Building Visualiser Workflows

### Interactive viewer

Authorized staff can open a 3D view of any project that has an uploaded GLB model.

Expected functional behavior:

- the scene loads the building model progressively with a visible progress bar
- every mesh representing a lot is coloured according to the current commercial status of the mapped property
- hovering a mesh displays an infobulle with lot reference, status, typology, surface, and price
- clicking a mesh on a DISPONIBLE lot opens the vente creation flow pre-filled with the lot reference
- the viewer polls for status changes every 30 seconds and repaints affected meshes without rebuilding the scene
- a colour legend strip is always visible and can be toggled
- Tab and Shift+Tab cycle focus across lots for keyboard-only users

Status colour rules:

| Display status | Underlying property status | Colour |
| --- | --- | --- |
| DISPONIBLE | DRAFT, ACTIVE | blue |
| RESERVÉ | RESERVED | amber |
| VENDU | SOLD | green |
| LIVRÉ | WITHDRAWN, ARCHIVED | grey |

### Dashboard 3D tab

The commercial dashboard includes a 3D tab embedding the viewer with additional controls.

Expected functional behavior:

- a KPI overlay panel shows aggregated counts and revenue figures for the project
- the overlay can be shown or hidden without leaving the view
- a status filter narrows the displayed information to one category
- an admin can export the current view as a PDF

### Portal 3D view

Buyers with an active vente on a project can view the building in read-only mode through the portal.

Expected functional behavior:

- no click action triggers a commercial operation
- the buyer sees the same colour coding as staff
- access is denied with a 404 if the buyer has no vente linked to the project

## 10. Dashboard And Reporting Workflows

The platform exposes:

- home dashboard
- commercial dashboard summary and sales views
- dashboard cockpit analytics
- receivables dashboard
- cash dashboard
- project, tranche, and property KPI slices where implemented

Functional expectations:

- managers and admins can supervise broader performance
- agents see appropriately scoped data
- dashboards are fast enough to serve as operational decision tools, not just reporting exports

## 11. Buyer Portal Workflows

Buyers can:

- log in through a magic link
- list their ventes
- list their contracts
- view payment schedules
- view property details related to owned records
- download or upload vente-linked documents where the portal flow allows it

Functional limits:

- no access to CRM administration
- no access to other buyers’ data
- no editing of broad business records

## 12. Privacy And Compliance Workflows

Authorized users can:

- export contact data
- inspect rectification-oriented views
- anonymize eligible data
- read the privacy notice
- trigger or rely on retention automation

Business caveat:

- some records cannot be erased without breaking legal or contractual integrity, so anonymization follows explicit blocking rules

## 13. Functional Integrity Rules

- all tenant-scoped business actions must operate within the active societe
- user-facing lifecycle transitions must obey status rules
- buyer portal visibility must be ownership-checked
- asynchronous workflows must remain traceable through audit, notification, or outbox history
