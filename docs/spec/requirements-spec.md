# Requirements Specification

This document formalizes requirements that are directly supported or strongly implied by the current codebase.

## 1. Functional Requirements

### FR-1 Authentication and Session

- `FR-1.1` The system shall authenticate CRM users using email and password.
- `FR-1.2` The system shall support multi-societe users by requiring explicit societe selection before issuing a fully scoped CRM token.
- `FR-1.3` The system shall support public invitation activation without an existing CRM session.
- `FR-1.4` The system shall support buyer portal access through one-time magic links.

### FR-2 Authorization and Scope

- `FR-2.1` The system shall distinguish platform-level `SUPER_ADMIN` access from company-scoped CRM access.
- `FR-2.2` The system shall scope CRM business data to the active societe.
- `FR-2.3` The system shall restrict portal users to their own contract-related data.
- `FR-2.4` The system shall prevent company `ADMIN` users from assigning the `ADMIN` role.

### FR-3 Company Administration

- `FR-3.1` `SUPER_ADMIN` shall be able to create, update, suspend, reactivate, and inspect societes.
- `FR-3.2` `SUPER_ADMIN` shall be able to impersonate a member of a societe.
- `FR-3.3` The system shall expose societe compliance and usage statistics.

### FR-4 Membership Management

- `FR-4.1` `ADMIN` and `SUPER_ADMIN` shall be able to invite members.
- `FR-4.2` `ADMIN` and `SUPER_ADMIN` shall be able to edit, remove, unblock, and anonymize members.
- `FR-4.3` `MANAGER` shall have read-only visibility over company members.
- `FR-4.4` The system shall prevent removing or demoting the last company `ADMIN`.

### FR-5 CRM Data Management

- `FR-5.1` The system shall manage projects, properties, contacts, reservations, deposits, contracts, tasks, documents, notifications, and messages.
- `FR-5.2` Property creation and update shall validate type-dependent required fields.
- `FR-5.3` Property deletion shall be soft deletion.
- `FR-5.4` Project deletion shall be archival, not physical deletion.
- `FR-5.5` Contact operations shall support prospect qualification, interest management, and client/deposit conversion workflows.

### FR-6 Commercial Workflow

- `FR-6.1` The system shall support property reservations with expiry.
- `FR-6.2` The system shall support deposit creation, confirmation, cancellation, and expiry.
- `FR-6.3` The system shall prevent conflicting holds on the same property.
- `FR-6.4` The system shall support draft sales contracts, signature, cancellation, and PDF generation.
- `FR-6.5` The system shall capture buyer snapshot data when a contract is signed.

### FR-7 Collections and Reporting

- `FR-7.1` The system shall support payment schedule items tied to contracts.
- `FR-7.2` The system shall support partial payment recording.
- `FR-7.3` The system shall support commission reporting and rule management.
- `FR-7.4` The system shall expose commercial, receivables, cash, property, audit, and deposit reporting views.

### FR-8 Communication and Automation

- `FR-8.1` The system shall queue outbound email and SMS messages asynchronously.
- `FR-8.2` The system shall generate in-app notifications for selected business events.
- `FR-8.3` The system shall run scheduled processes for reminders, expirations, portal token cleanup, and data retention.

### FR-9 Privacy and Compliance

- `FR-9.1` The system shall support contact export, rectification, privacy notice display, and anonymization.
- `FR-9.2` The system shall support user data export and anonymization.
- `FR-9.3` The system shall block contact anonymization when signed contracts still require legal identity retention.

## 2. Non-Functional Requirements

### NFR-1 Security

- `NFR-1.1` Authentication shall use signed JWTs.
- `NFR-1.2` CRM sessions shall be revocable before token expiry.
- `NFR-1.3` Login shall be protected by rate limits and account lockout.
- `NFR-1.4` Platform, CRM, and portal route spaces shall be separated by explicit authorization rules.

### NFR-2 Isolation and Data Integrity

- `NFR-2.1` Business data access shall be scoped to the active societe.
- `NFR-2.2` High-value workflows shall protect against conflicting concurrent updates.
- `NFR-2.3` The system shall preserve historical and legal integrity through soft delete, archival, or immutable snapshots rather than broad hard deletes.

### NFR-3 Operability

- `NFR-3.1` The application shall expose health endpoints.
- `NFR-3.2` Configuration shall be driven by environment variables.
- `NFR-3.3` The application shall support optional Redis, SMTP, SMS, and object-storage integrations.
- `NFR-3.4` The codebase shall be runnable through Docker Compose and through split local development.

### NFR-4 Maintainability

- `NFR-4.1` Schema evolution shall be managed through sequential Liquibase changesets.
- `NFR-4.2` Mutable administrative entities shall support optimistic concurrency protection.
- `NFR-4.3` Business modules shall remain separated into controller/service/repository layers.

### NFR-5 Observability

- `NFR-5.1` Requests shall have correlation IDs for diagnostics.
- `NFR-5.2` The platform shall support trace export when OTEL is enabled.
- `NFR-5.3` Background delivery and reminder workflows shall be diagnosable through logs and persisted state.

## 3. Confirmed Requirement Gaps and Ambiguities

### Needs clarification

- `RC-1` Whether societe suspension is intended to block login or API usage.
- `RC-2` Whether contact, property, and project quotas are informational or meant to be enforced now.
- `RC-3` Whether the legacy `/api/admin/users` and dead `/tenants` path should remain part of the supported contract.
- `RC-4` Whether the Angular client should implement multi-societe selection immediately or whether multi-membership is not yet a supported frontend scenario.

### Confirmed mismatches

- `RM-1` Backend auth DTOs support `requiresSocieteSelection`, but the current frontend login model does not.
- `RM-2` Security configuration still contains a public bootstrap route with no active controller behind it.
