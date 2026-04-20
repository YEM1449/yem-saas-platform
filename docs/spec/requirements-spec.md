# Requirements Specification

This specification defines the business and technical requirements for the current YEM SaaS Platform.

## 1. Product Purpose

The platform must provide a secure, multi-societe real-estate CRM for Moroccan operators while also offering:

- platform-level governance for the SaaS owner
- guided sales execution for internal teams
- controlled self-service visibility for buyers

## 2. Primary Personas

| Persona | Goal |
| --- | --- |
| `SUPER_ADMIN` | create, govern, and support societes on the platform |
| Societe `ADMIN` | manage the company workspace, users, templates, and advanced operations |
| Societe `MANAGER` | supervise pipeline execution and operational performance |
| Societe `AGENT` | execute assigned deals, follow up leads, and progress ventes |
| Buyer / portal user | consult contracts, payment information, and related property details |
| New engineer | understand and safely extend the platform |
| Student / trainee | learn modern SaaS, security, and domain-driven implementation patterns from a real project |

## 3. Functional Requirement Groups

### A. Platform governance

The system must:

- allow `SUPER_ADMIN` to create, update, suspend, reactivate, and inspect societes
- store branding, quota, legal, and compliance data per societe
- support member inspection and impersonation for support scenarios

### B. Authentication and identity

The system must:

- authenticate staff users with email and password
- support invitation-based activation for newly invited staff members
- support multi-societe users through an explicit societe selection step
- authenticate buyers through one-time magic links
- revoke staff sessions when user security state changes

### C. CRM master data

The system must:

- manage projects, immeubles, tranches, and properties
- manage contacts across prospect and client stages
- capture contact-property interests
- support documents and media linked to business entities

### D. Sales execution

The system must:

- manage reservations, deposits, ventes, and contracts
- support financing and legal milestones in the sale lifecycle
- manage payment schedules and payments
- invite buyers into the portal from sales workflows

### E. Operational productivity

The system must:

- support user tasks and due-date follow-up
- provide notifications and outbound message history
- surface audit trails and workflow timelines

### F. Reporting and analytics

The system must:

- provide commercial dashboards
- provide receivables and cash views
- expose KPI slices by project and tranche where implemented
- support agent-scoped and management-scoped visibility rules

### G. Privacy and compliance

The system must:

- store consent and processing basis information
- support export, rectification, and anonymization flows
- enforce retention rules through scheduled jobs
- preserve legally necessary data where business rules require it

### H. Learning and onboarding

The repository must:

- provide source-of-truth architecture and specification documents
- provide role-based user guides
- provide engineer onboarding guides
- provide course material suitable for newcomers and students

## 4. Role Requirements

### `SUPER_ADMIN`

- can manage societes and their metadata
- can inspect societe members
- can impersonate users
- cannot use platform powers accidentally inside the normal CRM without deliberate impersonation or route targeting

### `ADMIN`

- can manage staff members inside the societe
- can manage templates and advanced operational data
- can perform destructive actions permitted by the product, such as delete/disable flows where supported

### `MANAGER`

- can execute most operational CRM workflows
- cannot assign `ADMIN`
- cannot access platform-level governance routes

### `AGENT`

- can read most operational data needed to execute sales
- can work with ventes and related milestones where the product explicitly allows it
- cannot access admin-only management flows

### Buyer

- can access only owned portal data
- cannot access CRM routes or other buyers’ records

## 5. Data Requirements

The system must:

- scope tenant-owned data by `societe_id`
- preserve auditable timestamps on business-critical entities
- support optimistic locking on mutable aggregates where concurrent edits matter
- preserve attachments and media with metadata sufficient for download and governance

## 6. Security Requirements

The system must:

- isolate societes at the application and database level
- use secure cookie-based final sessions for browser flows
- support staff token revocation before expiry
- protect login and magic-link endpoints against abuse
- keep buyer and staff authentication surfaces separate
- apply secure headers and transport-aware cookie settings

## 7. Non-Functional Requirements

### Availability and operability

- the stack must run locally through Docker Compose
- backend health and readiness must be externally verifiable
- production deployment must support reverse proxy TLS termination

### Maintainability

- backend code must remain modular by business domain
- frontend code must remain organized by surface and feature area
- schema changes must remain traceable through Liquibase
- documentation must stay aligned with code, not drift into “historical only” ambiguity

### Testability

- backend unit and integration tests must be automatable in CI
- frontend unit tests and Playwright E2E tests must cover critical flows
- documentation must explain current test conventions and pitfalls

### Compliance

- the platform must support Moroccan business context and local privacy obligations
- privacy operations must be actionable by authorized users

## 8. Explicit Out-Of-Scope Assumptions

- the buyer portal is not a full CRM; it is a constrained self-service surface
- public anonymous browsing of inventory is not part of the current product contract
- separate database-per-societe deployment is not the target architecture
