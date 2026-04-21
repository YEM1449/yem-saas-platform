# Data Model

This document summarizes the live data model as implemented in JPA entities and Liquibase migrations.

## 1. Modeling Principles

- the business runtime is societe-centric
- every tenant-scoped aggregate carries `societe_id`
- schema evolution is additive through Liquibase changesets
- some migration history still contains `tenant` terminology, but the runtime model uses `societe`

## 2. Identity And Company Aggregates

### `app_user`

Global identity record.

Key concerns:

- email and password hash
- enablement and lockout state
- `platform_role` for `SUPER_ADMIN`
- `token_version` for revocation
- user profile data such as name, phone, language, and position
- GDPR flags and anonymization metadata

### `societe`

Company aggregate managed by `SUPER_ADMIN`.

Major field groups:

- legal identity: commercial name, RC, IF, TVA, CNSS, ICE
- contact and location: address, city, region, phones, contact email, website
- compliance: DPO fields, declaration numbers, retention defaults, legal basis
- licensing: approvals, guarantees, insurance, activity type, intervention zones
- branding: logo, colors, language, currency, legal mentions
- commercial governance: quotas, plan, trial flag, revenue and sales targets
- lifecycle: active flag, suspension reason, subscription dates

### `app_user_societe`

Membership bridge between users and societes.

Important behaviors:

- composite key on `(user_id, societe_id)`
- stores societe role: `ADMIN`, `MANAGER`, or `AGENT`
- supports active / inactive lifecycle
- powers multi-societe login selection

## 3. CRM Catalog And Inventory

### `project`

Represents a real-estate program or development.

Highlights:

- unique name per societe
- KPI-bearing entity used by dashboards
- archival instead of hard delete

### `immeuble`

Structured building under a project.

Use cases:

- group units by building
- improve dashboard slicing
- support more professional inventory modeling than a raw text building name

### `tranche`

Delivery or rollout phase under a project.

Use cases:

- bulk generation workflows
- phased delivery reporting
- tranche-level KPI views

### `property`

Represents a unit or lot in the inventory.

Key dimensions:

- classification: type, status, reference code, title
- commercial data: price, estimated value, commission rate, currency
- location: address, city, region, geo coordinates
- legal metadata: title deed, cadastral reference, owner, legal status
- type-specific fields: surface, land area, bedrooms, bathrooms, floor, zoning, serviceability
- hierarchy: project, optional immeuble, optional tranche
- editorial lifecycle: listed for sale, deleted at, reserved at, sold at

Main status values:

- `DRAFT`
- `ACTIVE`
- `RESERVED`
- `SOLD`
- `WITHDRAWN`
- `ARCHIVED`

## 4. Customer And Lead Management

### `contact`

Unified person or organization record used across prospecting and sales.

Important field groups:

- identity: first name, last name, full name, phone, email
- classification: `contact_type`, `status`, qualification flags
- business notes and ownership metadata
- privacy: consent, processing basis, retention override, anonymized timestamp

Key statuses:

- `PROSPECT`
- `QUALIFIED_PROSPECT`
- `CLIENT`
- `ACTIVE_CLIENT`
- `COMPLETED_CLIENT`
- `REFERRAL`
- `LOST`

### `prospect_detail`

One-to-one enrichment record for qualified prospects.

Typical data:

- budget range
- lead source
- notes

### `client_detail`

One-to-one enrichment record for formal client information.

Typical data:

- client kind
- company identity values
- fiscal identifiers

### `contact_interest`

Join table linking contacts to properties of interest.

Purpose:

- capture expressed buyer interest
- drive sales follow-up
- support contact-centric and property-centric relationship lookups

## 5. Sales Pipeline Aggregates

### `property_reservation`

Short-lived non-financial hold on a property.

Core fields:

- contact
- property
- reserved by
- reservation price
- reservation date and expiry date
- status

Main statuses:

- `ACTIVE`
- `EXPIRED`
- `CANCELLED`
- `CONVERTED_TO_DEPOSIT`

### `deposit`

Financial reservation record.

Core fields:

- contact
- property
- agent
- amount and currency
- due date and confirmation dates
- reference
- status

Main statuses:

- `PENDING`
- `CONFIRMED`
- `CANCELLED`
- `EXPIRED`

### `vente`

Commercial sale pipeline record.

Purpose:

- manage progression from compromis to livraison
- track financing, notary, deadlines, probability, and contract readiness
- host vente-specific documents and milestones

Important fields:

- `vente_ref`
- property and contact links
- agent link
- legal and financing dates
- probability and expected closing date
- contract generation and signing state

Main statuses:

- `COMPROMIS`
- `FINANCEMENT`
- `ACTE_NOTARIE`
- `LIVRE`
- `ANNULE`

### `sale_contract`

Formal contract entity used by the contract and payment modules.

Purpose:

- represent the official contract lifecycle
- expose PDFs and schedule management
- preserve buyer snapshot data

Main statuses:

- `DRAFT`
- `SIGNED`
- `CANCELED`

## 6. Collections, Payments, And Commissions

### `payment_schedule_item`

Represents one planned payment milestone under a contract.

Responsibilities:

- due dates and amounts
- issue/send/cancel lifecycle
- payment aggregation
- overdue status

### `schedule_payment`

Actual payment registration against a schedule item.

Purpose:

- support partial payment
- preserve payer, amount, and payment date history

### `schedule_item_reminder`

Reminder tracking table for collection follow-up.

Purpose:

- avoid duplicate reminders
- record reminder execution and channel usage

### `commission_rule`

Commission configuration at societe or project level.

Purpose:

- define fixed or percentage rules
- override societe defaults per project

## 7. Collaboration, Communication, And Files

### `task`

Follow-up work item.

Key traits:

- linked to assignee and creator
- optional contact or property association
- due date, priority, and status-driven workflow

Main statuses:

- `OPEN`
- `IN_PROGRESS`
- `DONE`
- `CANCELED`

### `notification`

In-app event delivered to a user.

Typical events:

- overdue reminders
- operational workflow updates
- task and collection-related alerts

### `outbound_message`

Queued outbound email or SMS record.

Purpose:

- decouple user action from provider delivery
- support retries, failure tracking, and operational reporting

### `document`

Generic attachment linked to supported business entities.

Supported entity families:

- contact
- property
- reservation
- deposit
- contract
- project
- vente

### `property_media`

Media record for property visuals and downloadable assets.

## 8. Portal Authentication And Audit

### `portal_token`

Stores hashed one-time portal access links.

Important traits:

- raw token is never stored
- TTL-based cleanup
- one-time use enforcement

### `commercial_audit_event`

Append-only operational history for workflow events.

Purpose:

- investigation
- timeline reconstruction
- user-facing audit visibility

## 9. Data Isolation Coverage

RLS and service-level scoping cover the main tenant-scoped tables, including:

- contacts and contact extensions
- projects, immeubles, tranches, and properties
- reservations, deposits, ventes, contracts, and payments
- tasks, documents, notifications, and media

## 10. Modeling Nuances To Keep In Mind

- `vente` and `sale_contract` coexist because one focuses on pipeline progression while the other focuses on formal contract and schedule operations
- some indexes still contain legacy `tenant` naming even though the runtime semantics are societe-based
- privacy and compliance data is embedded in both company and contact/user aggregates, not isolated into a standalone compliance module
