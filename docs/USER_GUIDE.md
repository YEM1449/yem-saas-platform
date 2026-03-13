# YEM SaaS Platform — User Guide

> **Audience**: End users, operators, and client administrators
> **Version**: v2 (2026-03)
> **Platform**: Real-estate CRM & Buyer Portal

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [System Overview](#2-system-overview)
3. [User Roles and Permissions](#3-user-roles-and-permissions)
4. [Getting Started](#4-getting-started)
5. [Main Features](#5-main-features)
   - 5.1 [Contact & Prospect Management](#51-contact--prospect-management)
   - 5.2 [Property & Project Management](#52-property--project-management)
   - 5.3 [Deposit & Reservation Workflow](#53-deposit--reservation-workflow)
   - 5.4 [Contract & Sales Lifecycle](#54-contract--sales-lifecycle)
   - 5.5 [Payment Schedule & Call-for-Funds](#55-payment-schedule--call-for-funds)
   - 5.6 [Messaging & Notifications](#56-messaging--notifications)
   - 5.7 [Dashboards & Analytics](#57-dashboards--analytics)
   - 5.8 [Commission Management](#58-commission-management)
   - 5.9 [Buyer Portal](#59-buyer-portal)
   - 5.10 [User Administration](#510-user-administration)
6. [Common Workflows](#6-common-workflows)
7. [Configuration Guide](#7-configuration-guide)
8. [Reporting and Monitoring](#8-reporting-and-monitoring)
9. [Error Handling](#9-error-handling)
10. [Best Practices](#10-best-practices)
11. [FAQ](#11-faq)
12. [Appendix: Suggested Diagrams & Missing Documentation](#12-appendix-suggested-diagrams--missing-documentation)

---

## 1. Introduction

### 1.1 Purpose of the Solution

The **YEM SaaS Platform** is a multi-tenant CRM system purpose-built for **real-estate promotion teams**. It provides a single source of truth for every property transaction — from first contact with a prospect through to signed contracts and post-sale payment collection.

It also exposes a **secure, self-service Buyer Portal** so clients can independently view their contracts and upcoming payment obligations — without requiring calls to the sales team.

### 1.2 Target Users

| Audience | How They Use the Platform |
|---|---|
| **Sales Agents** | Create and manage their own prospects, deposits, contracts, and payment schedules |
| **Sales Managers** | Oversee the full team pipeline, confirm reservations, sign contracts, track KPIs |
| **Platform Administrators** | Configure the system, manage users, define commission rules, access all data |
| **Buyers / Clients** | View their own contracts, payment schedules, and property details via the Buyer Portal |

### 1.3 Key Capabilities

- **Lead-to-cash lifecycle** — End-to-end management from first lead contact to payment collection
- **Inventory management** — Real-time property availability, status, and portfolio organisation by project
- **Reservation system** — Deposit-based property locking with PDF reservation certificates
- **Contract management** — Digital contract lifecycle with PDF generation and legal signing workflow
- **Payment tracking** — Call-for-funds scheduling, payment recording, and overdue tracking
- **Sales analytics** — Commercial KPIs, receivables aging, cash-flow dashboards
- **Buyer self-service portal** — Magic-link authenticated view of contracts and payment schedules
- **Automated messaging** — Email and SMS dispatch queue with automatic retry
- **Commission tracking** — Automated commission calculation per signed contract

---

## 2. System Overview

### 2.1 High-Level Architecture (Simple View)

The platform has two main access points:

```
┌─────────────────────────────────────────────────────────────┐
│                    YEM SaaS Platform                        │
│                                                             │
│  ┌─────────────────────┐    ┌───────────────────────────┐  │
│  │   CRM Web App       │    │   Buyer Portal            │  │
│  │   (Sales Team)      │    │   (Clients / Buyers)      │  │
│  │                     │    │                           │  │
│  │  Admin / Manager /  │    │  Email magic-link login   │  │
│  │  Agent login        │    │  Read-only contract view  │  │
│  └─────────────────────┘    └───────────────────────────┘  │
│              │                           │                  │
│              └──────────┬────────────────┘                  │
│                         │                                   │
│              ┌──────────▼──────────┐                        │
│              │   Secure API Layer  │                        │
│              │   (JWT Auth, RBAC)  │                        │
│              └──────────┬──────────┘                        │
│                         │                                   │
│  ┌──────────────────────▼───────────────────────────────┐  │
│  │               Core Business Services                 │  │
│  │  Contacts │ Properties │ Contracts │ Payments │ KPIs │  │
│  └──────────────────────┬───────────────────────────────┘  │
│                         │                                   │
│              ┌──────────▼──────────┐                        │
│              │   Database          │                        │
│              │   (Tenant-isolated) │                        │
│              └─────────────────────┘                        │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 Main System Components

| Component | Description |
|---|---|
| **CRM Application** | The primary web interface used by the sales team (Admin, Manager, Agent) |
| **Buyer Portal** | A separate, simplified view for property buyers to see their contracts and payments |
| **API Layer** | Secure REST API that powers both applications; also available for integration |
| **Outbox / Messaging** | Asynchronous email and SMS dispatch queue with automatic retry |
| **Document Generator** | Produces PDF documents for contracts, reservation certificates, and call-for-funds notices |
| **Scheduler** | Background jobs for overdue payment detection and automatic reminders |

### 2.3 Key Terminology

| Term | Meaning |
|---|---|
| **Tenant** | A company/organisation using the platform (full data isolation between tenants) |
| **Contact** | A person in the system — may be a prospect, lead, or signed buyer |
| **Prospect** | A contact who has expressed interest but has not yet signed a contract |
| **Property** | A real-estate unit available for sale (apartment, villa, lot, etc.) |
| **Project** | A collection of properties grouped together (e.g. a residential development) |
| **Deposit** | A monetary reservation that locks a property for a specific buyer |
| **Contract** | The legal sale agreement between tenant/agent and buyer |
| **Schedule Item** | A single installment in a payment plan (also called a "call-for-funds") |
| **Call-for-Funds** | A formal payment request sent to a buyer for a specific installment amount |
| **Magic Link** | A one-time email link that allows a buyer to access the portal without a password |
| **RBAC** | Role-Based Access Control — the permission system that controls what each user can do |

---

## 3. User Roles and Permissions

### 3.1 Role Descriptions

#### Administrator (`ROLE_ADMIN`)
The highest privilege level. Administrators have unrestricted access to all data and configuration.

**Typical responsibilities**:
- Create and manage user accounts (agents, managers)
- Define commission rules and fee structures
- Access all dashboards across all agents
- Sign or cancel any contract
- Perform any create, update, or delete operation

#### Manager (`ROLE_MANAGER`)
Operational oversight role. Managers can see and modify all team data but cannot delete records or manage users.

**Typical responsibilities**:
- Review and confirm deposits submitted by agents
- Sign contracts after review
- Monitor team pipeline and commercial KPIs
- Create and issue payment schedules
- Oversee buyer portal access

#### Agent (`ROLE_AGENT`)
Individual contributor. Agents can create and manage their own client relationships and deals.

**Typical responsibilities**:
- Manage own prospect pipeline
- Create contracts and deposits (own assignments)
- Record payments on own contracts
- View own commission statements
- View own portion of dashboards (not team-wide)

#### Buyer / Portal User (`ROLE_PORTAL`)
Read-only access for external buyers/clients. No CRM access — portal only.

**Typical responsibilities**:
- View own contracts and property details
- Download contract PDF
- Track payment schedule status

### 3.2 Permission Matrix

| Action | Admin | Manager | Agent | Buyer (Portal) |
|---|:---:|:---:|:---:|:---:|
| Create contacts | ✓ | ✓ | ✗ | ✗ |
| Edit contacts | ✓ | ✓ | ✗ | ✗ |
| Delete contacts | ✓ | ✗ | ✗ | ✗ |
| Create properties | ✓ | ✓ | ✗ | ✗ |
| Edit properties | ✓ | ✓ | ✗ | ✗ |
| Delete (archive) properties | ✓ | ✗ | ✗ | ✗ |
| Create deposits | ✓ | ✓ | ✗ | ✗ |
| Confirm / cancel deposits | ✓ | ✓ | ✗ | ✗ |
| Create contracts | ✓ | ✓ | ✓ | ✗ |
| Sign contracts | ✓ | ✓ | ✗ | ✗ |
| Cancel contracts | ✓ | ✓ | ✗ | ✗ |
| Create payment schedule items | ✓ | ✓ | ✗ | ✗ |
| Issue / send payment items | ✓ | ✓ | ✗ | ✗ |
| Record payments | ✓ | ✓ | ✓ | ✗ |
| View all contracts (team-wide) | ✓ | ✓ | Own only | Own only |
| View commercial dashboard | ✓ | ✓ | Own data only | ✗ |
| View receivables dashboard | ✓ | ✓ | Own data only | ✗ |
| Manage user accounts | ✓ | ✗ | ✗ | ✗ |
| Define commission rules | ✓ | ✗ | ✗ | ✗ |
| View own commissions | ✓ | ✓ | ✓ | ✗ |
| Download contract PDF | ✓ | ✓ | Own only | Own only |
| Access buyer portal | ✗ | ✗ | ✗ | ✓ |

---

## 4. Getting Started

### 4.1 Accessing the System

#### CRM Application (Sales Team)
Navigate to the platform URL provided by your administrator. You will land on the login page.

> **Note**: Your administrator will provide the application URL specific to your organisation.

#### Buyer Portal (Clients)
Buyers access the portal at `[platform-url]/portal/login`. No pre-registration is required — access is granted via a magic link emailed by your sales team.

### 4.2 CRM Login (Sales Team)

1. Open the platform URL in your browser
2. Enter your **email address** and **password**
3. Click **Sign In**
4. On successful login, you will be taken to your dashboard

> **Password forgotten?** Contact your Platform Administrator to reset your password. There is no self-service password reset for CRM users.

### 4.3 Buyer Portal Login (Magic Link)

Buyers do not use a password. Access is granted through a **one-time magic link**:

1. Your sales representative will send you an email with a **"View your contract"** link
2. Click the link — it is valid for **48 hours** and can only be used **once**
3. After verification, you will receive a session lasting **2 hours**
4. To access the portal again after your session expires, ask your sales representative to resend the magic link

### 4.4 First-Time Setup (Administrators)

After initial deployment, an Administrator should complete the following:

1. **Verify login** — Use the administrator credentials provided during setup
2. **Create user accounts** — Navigate to **Admin → Users** and create accounts for your team
3. **Assign roles** — Set each user's role: Admin, Manager, or Agent
4. **Create commission rules** — Navigate to **Commission Rules** and define your fee structure
5. **Create projects** — Navigate to **Projects** and create your property development projects
6. **Import properties** — Use the CSV import on the Properties screen, or create them manually
7. **Test buyer portal** — Create a test contact and send a magic link to verify the portal works end-to-end

---

## 5. Main Features

### 5.1 Contact & Prospect Management

**What it does**: Manages the full lifecycle of a lead — from first contact through to signed buyer.

**When to use**: When a new lead is identified, when following up with prospects, or when managing ongoing client relationships.

#### Contact Status Lifecycle

```
NEW PROSPECT
     │
     ▼
QUALIFIED PROSPECT ──────► LOST
     │
     ▼
TEMP_CLIENT (deposit pending)
     │
     ▼
CLIENT
     │
     ▼
ACTIVE CLIENT
     │
     ▼
COMPLETED CLIENT
```

Additionally, contacts may be marked **REFERRAL** at any point to indicate they were referred by another party.

#### Creating a Contact

1. Navigate to **Contacts** in the main menu
2. Click **New Contact**
3. Fill in the required fields:
   - First name, last name
   - Email address
   - Phone number
   - Optional: address, notes
4. Click **Save**
5. The contact is created with status **NEW PROSPECT**

#### Qualifying a Prospect

1. Open the contact's profile
2. Click **Change Status**
3. Select **QUALIFIED PROSPECT**
4. Optionally record the properties they are interested in (see below)

#### Recording Property Interest

1. Open the contact's profile
2. Navigate to the **Interests** tab
3. Click **Add Interest**
4. Select the property from the dropdown
5. Click **Save**

This links the contact to the property and helps track demand per listing.

#### Viewing the Activity Timeline

Every contact has a timeline showing all interactions:
- Contracts created or signed
- Deposits created, confirmed, or cancelled
- Messages sent
- Status changes

Navigate to the **Timeline** tab on any contact profile to view this history.

#### Bulk Import

1. Navigate to **Contacts**
2. Click **Import CSV**
3. Download the template if needed
4. Upload a CSV with the required columns
5. Confirm the import

---

### 5.2 Property & Project Management

**What it does**: Manages the real-estate inventory — individual properties and the projects (developments) they belong to.

**When to use**: When adding new properties to the platform, updating property status, or monitoring project-level KPIs.

#### Property Status Lifecycle

| Status | Meaning |
|---|---|
| **DRAFT** | Being prepared — not yet visible or available to buyers |
| **ACTIVE** | On market — available for reservations |
| **RESERVED** | Locked by an active deposit |
| **SOLD** | Contract signed, transaction complete |
| **WITHDRAWN** | Temporarily removed from market |
| **ARCHIVED** | Historical record, no longer active |

> **Important**: Status transitions are automatic when deposits are created/confirmed and contracts are signed. You cannot manually set a property to RESERVED or SOLD.

#### Creating a Property

1. Navigate to **Properties**
2. Click **New Property**
3. Fill in the fields:
   - Reference code (unique identifier)
   - Type (apartment, villa, lot, etc.)
   - Floor, surface area, price
   - Description and notes
   - Assign to a project (optional but recommended)
4. Click **Save**
5. The property is created in **DRAFT** status. Change it to **ACTIVE** when ready to accept reservations.

#### Bulk Property Import

1. Navigate to **Properties**
2. Click **Import CSV**
3. Download the column template
4. Upload a completed CSV file
5. Review the import summary and confirm

#### Managing Projects

A **Project** is a logical grouping of properties (e.g. a residential development, a commercial campus).

To create a project:
1. Navigate to **Projects**
2. Click **New Project**
3. Enter the project name and description
4. Click **Save**

To view project KPIs:
1. Open the project
2. The **KPIs tab** shows:
   - Total properties by status
   - Total deposit amounts (confirmed, pending)
   - Number of signed sales
   - Revenue summary

---

### 5.3 Deposit & Reservation Workflow

**What it does**: Allows sales teams to formally reserve a property for a buyer before the contract is signed.

**When to use**: When a buyer wants to lock in a property while contract details are finalised.

#### Deposit Status Lifecycle

```
PENDING (property becomes RESERVED)
     │
     ├──► CONFIRMED (buyer commitment locked)
     │
     ├──► CANCELLED (buyer withdraws → property → ACTIVE)
     │
     └──► EXPIRED (7 days elapsed → property → ACTIVE)
```

#### Creating a Deposit

**Required role**: Manager or Admin

1. Navigate to the contact's profile (the buyer)
2. Click **New Deposit** (or navigate to the property and select the buyer)
3. Fill in:
   - Contact (buyer)
   - Property
   - Deposit amount
   - Expiry date (default: 7 days)
4. Click **Save**
5. The property automatically changes to **RESERVED**
6. The contact automatically changes to **TEMP_CLIENT**

> **Business rule**: Only one active deposit may exist per property at a time. If the property already has an active deposit, you must cancel it first.

#### Confirming a Deposit

1. Open the deposit record
2. Click **Confirm Deposit**
3. The deposit moves to **CONFIRMED** status

#### Cancelling a Deposit

1. Open the deposit record
2. Click **Cancel Deposit**
3. The deposit is cancelled; the property reverts to **ACTIVE**

#### Downloading the Reservation Certificate (PDF)

1. Open the deposit record
2. Click **Download Reservation Certificate**
3. A PDF document is generated and downloaded

#### Deposit Report

1. Navigate to **Deposits → Reports**
2. Apply filters: status, agent, contact, property, date range
3. View or export the filtered report

---

### 5.4 Contract & Sales Lifecycle

**What it does**: Manages the legal sale agreement between your company and the buyer.

**When to use**: After a deposit is confirmed and both parties are ready to proceed to contract.

#### Contract Status Lifecycle

```
DRAFT
  │
  ├──► SIGNED (property → SOLD, commission calculated)
  │
  └──► CANCELED (property reverts to RESERVED or ACTIVE)
```

> **Important**: Only **SIGNED** contracts count in sales KPIs and dashboards. Draft contracts are excluded from revenue reporting.

#### Creating a Contract

**Required role**: Admin, Manager, or Agent

1. Navigate to **Contracts**
2. Click **New Contract**
3. Fill in:
   - Buyer (contact)
   - Property
   - Project
   - Agreed sale price
   - Assigned agent (auto-filled if created by an agent)
4. Click **Save**
5. The contract is created in **DRAFT** status

#### Signing a Contract

**Required role**: Manager or Admin

1. Open the contract
2. Review all details carefully
3. Click **Sign Contract**
4. Confirm the action
5. The contract moves to **SIGNED**
6. The property automatically moves to **SOLD**
7. The agent's commission is automatically calculated

#### Cancelling a Contract

**Required role**: Manager or Admin

1. Open the contract
2. Click **Cancel Contract**
3. Provide a reason
4. Confirm

> **Note**: Cancelling a SIGNED contract reverts the property status and removes the commission record. This action should be taken carefully.

#### Downloading the Contract PDF

1. Open the contract
2. Click **Download Contract PDF**
3. The document is generated and downloaded for printing or filing

---

### 5.5 Payment Schedule & Call-for-Funds

**What it does**: Manages the post-sale payment plan — scheduling installments, issuing formal requests to buyers, and recording payments received.

**When to use**: After a contract is signed, to set up and manage the buyer's payment obligations.

#### Schedule Item Status Lifecycle

```
DRAFT (created, editable)
  │
  ▼
ISSUED (finalized, ready to send)
  │
  ▼
SENT (call-for-funds email/SMS dispatched to buyer)
  │
  ├──► PAID (full payment recorded — terminal state)
  │
  ├──► OVERDUE (due date passed, balance still outstanding)
  │
  └──► CANCELED (from any non-PAID state)
```

#### Creating a Payment Schedule

1. Navigate to the contract
2. Go to the **Payment Schedule** tab
3. Click **Add Schedule Item**
4. Enter:
   - Tranche name (e.g. "First Installment", "Completion Payment")
   - Amount due
   - Due date
5. Click **Save**
6. The item is created in **DRAFT** status and can be edited

Repeat to add multiple installments for the same contract.

#### Issuing a Payment Item (DRAFT → ISSUED)

1. Open the schedule item
2. Click **Issue**
3. The item is now locked from editing and ready to be sent

#### Sending a Call-for-Funds to the Buyer (ISSUED → SENT)

1. Open the issued schedule item
2. Click **Send to Buyer**
3. The system queues an email (and/or SMS) to the buyer
4. The item moves to **SENT** status

#### Recording a Payment

1. Open the schedule item
2. Click **Record Payment**
3. Enter:
   - Amount received
   - Payment date
   - Reference (optional)
4. Click **Save**
5. When the full amount is received, the item automatically moves to **PAID**

#### Downloading the Call-for-Funds PDF

1. Open the schedule item
2. Click **Download Call-for-Funds PDF**
3. The document is generated for internal records or manual delivery

#### Triggering Manual Overdue Reminders

1. Navigate to **Payment Schedule**
2. Click **Run Reminder Check**
3. The system evaluates all overdue items and sends reminder notifications

> **Note**: Automatic reminders are sent by the system on a schedule. Use manual trigger only if you need to send reminders immediately outside the normal cycle.

---

### 5.6 Messaging & Notifications

The platform has two distinct communication channels:

#### 5.6.1 Outbound Messages (Email / SMS to Buyers)

Used to send transactional messages to external contacts (buyers) — such as call-for-funds notices, reminders, or custom communications.

**Sending a message**:
1. Navigate to **Messages**
2. Click **New Message**
3. Choose channel: **Email** or **SMS**
4. Select recipient (contact)
5. Enter subject (email only) and message body
6. Click **Send**
7. The message is queued; the system dispatches it asynchronously

**Message states**:

| State | Meaning |
|---|---|
| **PENDING** | Queued, waiting for dispatch |
| **SENT** | Successfully delivered to recipient |
| **FAILED** | All retry attempts exhausted; manual follow-up needed |

**Retry logic**: The system automatically retries failed messages after 1 minute, then 5 minutes, then 30 minutes before marking as FAILED.

**Viewing message history**:
1. Navigate to **Messages**
2. Filter by: channel (Email/SMS), status, contact, date range
3. Review delivery status for each message

#### 5.6.2 In-App Notifications (CRM Bell)

Internal alerts displayed within the CRM application (visible to logged-in CRM users only — not sent externally).

**Viewing notifications**:
1. Click the **bell icon** in the top navigation bar
2. View the list of unread notifications
3. Click a notification to mark it as read and navigate to the related record

---

### 5.7 Dashboards & Analytics

#### 5.7.1 Commercial Dashboard

**Who can access**: Admin (all data), Manager (all data), Agent (own data only)

**How to access**: Navigate to **Dashboard → Commercial**

**Filters available**:
- Date range (start date / end date)
- Project
- Agent (Admin/Manager can filter by agent; Agents see only own data)

**Metrics displayed**:

| Metric | Description |
|---|---|
| **Total Revenue** | Sum of agreed prices on SIGNED contracts in the period |
| **Contract Count** | Number of SIGNED contracts in the period |
| **Average Deal Size** | Revenue ÷ Contract Count |
| **Average Discount %** | Average discount offered vs. list price |
| **Max Discount %** | Highest single discount given |
| **Discount by Agent** | Breakdown of average discount per agent |
| **Inventory Summary** | Properties by status: DRAFT / ACTIVE / RESERVED / SOLD / WITHDRAWN |
| **Deposit Summary** | Pending and confirmed deposits — count and total value |
| **Prospect Funnel** | Contact counts by source and qualification status |
| **Daily Signing Trend** | Chart showing contract signings day-by-day |

**Drill-down to signed contracts**:
1. Click the **View Contracts** link on the Commercial Dashboard
2. A paginated table of all SIGNED contracts is displayed
3. Filter and export as needed

#### 5.7.2 Cash Flow Dashboard

**How to access**: Navigate to **Dashboard → Cash Flow**

**Filters available**: Custom date range, project

**Metrics displayed**:
- Expected receipts for the current month
- Actual receipts received
- Month-over-month payment trend
- Expected vs. actual comparison chart

#### 5.7.3 Receivables Dashboard

**How to access**: Navigate to **Dashboard → Receivables**

**Filters available**: Project, agent

**Metrics displayed**:

| Metric | Description |
|---|---|
| **Current** | Amounts due within the current period (not yet overdue) |
| **30–60 Days Overdue** | Balances overdue between 30 and 60 days |
| **60–90 Days Overdue** | Balances overdue between 60 and 90 days |
| **90+ Days Overdue** | Balances overdue more than 90 days |
| **Total Issued** | Sum of all call-for-funds amounts created |
| **Total Received** | Sum of all payments recorded |

**Interpreting the aging buckets**:
- **Current** items are on track — no action needed
- **30–60 days** may warrant a follow-up call or reminder message
- **60–90 days** should be escalated with a formal reminder
- **90+ days** should be escalated to management for collection action

---

### 5.8 Commission Management

**What it does**: Tracks and reports agent commissions earned from signed contracts.

#### Viewing Your Commissions (All roles)

1. Navigate to **Commissions**
2. View the list of commissions attributed to your contracts
3. Filter by date range or contract

#### Viewing All Commissions (Admin / Manager only)

1. Navigate to **Commissions → All**
2. Filter by agent or date range
3. Review total commission liability

#### Defining Commission Rules (Admin only)

Commission rules define how agent fees are calculated when a contract is signed.

**Formula**: `Commission = (Agreed Price × Rate%) + Fixed Amount`

To create a rule:
1. Navigate to **Admin → Commission Rules**
2. Click **New Rule**
3. Enter:
   - Rate (percentage, e.g. `2.5` for 2.5%)
   - Fixed amount (optional flat fee added to the percentage)
   - Scope: tenant-wide default OR specific to a project/property
4. Click **Save**

**Rule priority**: A property-specific rule takes precedence over a project rule, which takes precedence over the tenant-wide default.

> **Note**: Commissions are automatically calculated when a contract is signed. They are not retroactively updated if rules change.

---

### 5.9 Buyer Portal

**What it does**: Gives buyers a read-only, self-service view of their contracts, payment schedules, and property details — accessible via a secure, password-free magic link.

#### Sending a Magic Link to a Buyer

**Required role**: Manager or Admin

1. Navigate to the buyer's contact record
2. Click **Send Portal Access**
3. Enter the buyer's email address (pre-filled from contact)
4. Click **Send**
5. The buyer receives an email with a link valid for **48 hours** (one-time use)

#### What the Buyer Sees

After clicking the magic link and logging in, the buyer can:

| Section | What They See |
|---|---|
| **My Contracts** | List of their signed contracts with property details |
| **Contract Detail** | Full contract information, agreed price, signing date |
| **Download Contract** | PDF version of the signed contract |
| **Payment Schedule** | All installments — amounts due, due dates, and payment status |
| **Company Info** | Your organisation's name and logo |

**Security guarantee**: Buyers can only see their own data. Viewing another buyer's information is technically impossible through the portal.

#### Portal Session Management

- Each magic link is **single-use** — it is invalidated after the first click
- Sessions last **2 hours** from the time of verification
- To get a new link after session expiry, contact your sales representative

---

### 5.10 User Administration

**Required role**: Admin only

#### Creating a New User

1. Navigate to **Admin → Users**
2. Click **New User**
3. Enter:
   - First and last name
   - Email address (used as login)
   - Initial password (the user should change this on first login)
   - Role: Admin, Manager, or Agent
4. Click **Create**

#### Changing a User's Role

1. Navigate to **Admin → Users**
2. Click on the user
3. Click **Change Role**
4. Select the new role
5. Click **Save**

> **Warning**: Changing a user from Admin to Agent removes their ability to see team-wide data.

#### Disabling a User Account

When a team member leaves or should lose access:

1. Navigate to **Admin → Users**
2. Click on the user
3. Click **Disable Account**
4. Confirm the action

The user will immediately lose the ability to log in. Their historical data (contracts, deposits) is preserved.

#### Resetting a User's Password

1. Navigate to **Admin → Users**
2. Click on the user
3. Click **Reset Password**
4. Enter and confirm the new password
5. Click **Save**
6. Communicate the new password to the user via a secure channel

---

## 6. Common Workflows

### Workflow 1: Prospect to Reservation

**Actors**: Sales Agent (or Manager)

1. Create a new **Contact** with the prospect's details
2. Set contact status to **QUALIFIED PROSPECT** after qualification conversation
3. Record the prospect's **Interest** in a specific property
4. Create a **Deposit** linking the contact to the property
5. The property automatically moves to **RESERVED**
6. The contact automatically becomes **TEMP_CLIENT**
7. Manager reviews and clicks **Confirm Deposit**
8. Send the buyer the **Reservation Certificate PDF**

---

### Workflow 2: Reservation to Signed Contract

**Actors**: Sales Agent creates; Sales Manager signs

1. Sales Agent creates a **Contract** (DRAFT) with the agreed price
2. Sales Agent reviews all contract details and assigns to correct project/property
3. Sales Manager opens the contract, verifies details, and clicks **Sign Contract**
4. The contract moves to **SIGNED**
5. The property automatically moves to **SOLD**
6. Commission is automatically calculated and recorded for the agent
7. Both parties download the **Contract PDF** for records

---

### Workflow 3: Setting Up a Payment Plan

**Actors**: Sales Manager (or Admin)

1. Open the signed contract
2. Navigate to **Payment Schedule**
3. Add each installment:
   - "First Payment" — amount, due date
   - "Second Payment" — amount, due date
   - "Completion Payment" — amount, due date
4. Review the total scheduled amount matches the agreed contract price
5. For each item, click **Issue** when ready to finalise it
6. Click **Send** to dispatch the call-for-funds notification to the buyer

---

### Workflow 4: Recording a Buyer Payment

**Actors**: Sales Agent, Manager, or Admin

1. Open the contract
2. Navigate to **Payment Schedule**
3. Find the relevant schedule item (should be in SENT or OVERDUE status)
4. Click **Record Payment**
5. Enter: amount received, date, payment reference
6. Click **Save**
7. If the full amount is now received, the item automatically moves to **PAID**
8. Check the **Receivables Dashboard** to confirm balances are updated

---

### Workflow 5: Buyer Accesses the Portal

**Actors**: Manager sends link; Buyer accesses portal

1. Manager navigates to the buyer's contact profile
2. Manager clicks **Send Portal Access**
3. Buyer receives an email with a magic link (valid 48 hours)
4. Buyer clicks the link → session is established (2 hours)
5. Buyer navigates to **My Contracts** and views their purchase
6. Buyer downloads the **Contract PDF**
7. Buyer checks the **Payment Schedule** to see upcoming payments

---

### Workflow 6: Monitoring Team Performance (KPI Review)

**Actors**: Sales Manager or Admin

1. Navigate to **Dashboard → Commercial**
2. Set the date range (e.g. current month or quarter)
3. Review:
   - Total revenue and contract count
   - Discount metrics (are agents offering too many discounts?)
   - Prospect funnel (where are leads dropping off?)
   - Inventory status (how many properties are still available?)
4. Click **View Contracts** to drill down into signed deals
5. Switch to **Dashboard → Receivables** to identify overdue balances
6. Filter by agent to identify who has the most overdue receivables
7. Take action: send reminders or escalate as needed

---

### Workflow 7: Sending a Bulk Reminder for Overdue Payments

**Actors**: Manager or Admin

1. Navigate to **Dashboard → Receivables**
2. Identify items in the **60–90 days** or **90+ days** aging buckets
3. For each overdue item, navigate to the schedule item
4. Click **Send Reminder** (or use **Run Reminder Check** for all at once)
5. The system queues reminder emails/SMS to the respective buyers
6. Check **Messages** to verify dispatch status

---

## 7. Configuration Guide

### 7.1 User Management

| Parameter | Where to Configure | Impact |
|---|---|---|
| User role | Admin → Users → Change Role | Controls what the user can see and do |
| Account enabled/disabled | Admin → Users → Enable/Disable | Grants or revokes login access |
| User password | Admin → Users → Reset Password | Allows login with new credentials |

### 7.2 Commission Rules

| Parameter | Where to Configure | Impact |
|---|---|---|
| Commission rate (%) | Admin → Commission Rules | Percentage applied to agreed price |
| Fixed fee | Admin → Commission Rules | Flat fee added to percentage-based commission |
| Rule scope | Admin → Commission Rules | Property-specific rules override project rules, which override tenant defaults |

### 7.3 System-Level Configuration (Administrator / DevOps)

These parameters are configured in the server environment and typically managed by your technical team or platform provider. They are listed here for reference.

| Parameter | Environment Variable | Description |
|---|---|---|
| Email server host | `MAIL_HOST` | SMTP hostname for outgoing email |
| Email server port | `MAIL_PORT` | SMTP port (typically 587 or 465) |
| Email username | `MAIL_USERNAME` | SMTP authentication username |
| Email password | `MAIL_PASSWORD` | SMTP authentication password |
| SMS provider URL | `SMS_API_URL` | Endpoint for SMS gateway |
| SMS API key | `SMS_API_KEY` | Credentials for SMS provider |
| Message batch size | `OUTBOX_BATCH_SIZE` | How many messages are dispatched per scheduler tick |
| Max message retries | `OUTBOX_MAX_RETRIES` | Number of retry attempts before marking as FAILED |
| Polling interval | `OUTBOX_POLL_INTERVAL_MS` | How often (ms) the scheduler checks for pending messages |
| JWT secret | `JWT_SECRET` | Token signing key — must be kept confidential |
| JWT expiry | `JWT_TTL_SECONDS` | CRM session duration in seconds |
| Database URL | `DB_URL` | Database connection string |

> **Security notice**: Never expose `JWT_SECRET`, `DB_URL`, `MAIL_PASSWORD`, or `SMS_API_KEY` in client-facing documentation, code repositories, or shared files.

---

## 8. Reporting and Monitoring

### 8.1 Available Reports

| Report | Location | Purpose | Access |
|---|---|---|---|
| **Commercial Dashboard** | Dashboard → Commercial | Sales KPIs, revenue, trends | Admin, Manager, Agent (own) |
| **Sales Drill-Down** | Dashboard → Commercial → View Contracts | Paginated list of SIGNED contracts | Admin, Manager, Agent (own) |
| **Cash Flow Dashboard** | Dashboard → Cash Flow | Expected vs. actual payment receipts | Admin, Manager, Agent (own) |
| **Receivables Aging** | Dashboard → Receivables | Outstanding balances by aging bucket | Admin, Manager, Agent (own) |
| **Deposit Report** | Deposits → Report | Deposits filtered by status, agent, date | Admin, Manager |
| **Commission Report** | Commissions | Commissions per agent and contract | Admin, Manager (all); Agent (own) |
| **Message History** | Messages | Outbound email/SMS delivery status | Admin, Manager |
| **Contact Timeline** | Contact → Timeline | Full activity history for a contact | Admin, Manager |

### 8.2 Interpreting Key Metrics

#### Revenue vs. Contract Count
- A high contract count with lower revenue may indicate smaller properties or higher discounts
- A sudden drop in signings warrants a review of the prospect funnel

#### Receivables Aging
- **Current**: Healthy — buyers are within their payment window
- **30–60 days**: Soft alert — consider sending a reminder
- **60–90 days**: Action required — send formal reminder, escalate if no response
- **90+ days**: Escalate to management — formal collection process may be needed

#### Prospect Funnel
- Monitor the ratio of **Qualified Prospects** to **Signed Contracts**
- A large funnel drop-off at the deposit stage may indicate pricing or property issues
- A large drop-off at the contract stage may indicate issues with contract terms or follow-up speed

### 8.3 Operational Monitoring

Administrators should routinely check:

1. **Failed messages** in the Messages screen (FAILED status) — indicates email/SMS provider issues
2. **Overdue schedule items** on the Receivables Dashboard — indicates payment follow-up needed
3. **Expired deposits** — properties may have reverted to ACTIVE without sales team awareness

---

## 9. Error Handling

### 9.1 Common Errors and Solutions

| Error / Situation | Likely Cause | Recommended Action |
|---|---|---|
| "Property already reserved" when creating deposit | Another active deposit exists on this property | Cancel or wait for expiry of the existing deposit before creating a new one |
| Cannot sign contract | Contract is not in DRAFT status, or you lack Manager/Admin role | Verify contract status and your user role; contact your administrator if needed |
| Property won't move to SOLD | Contract is still in DRAFT — only SIGNED contracts trigger status change | Sign the contract via the Sign Contract button |
| Commission not calculated after contract signing | Contract may not have been fully signed (still DRAFT) | Verify the contract status is SIGNED; commissions are calculated only on SIGNED contracts |
| Payment item stuck in OVERDUE | Balance not fully cleared | Record the remaining payment amount to bring the balance to zero |
| Message status shows FAILED | Email/SMS provider unreachable or credentials invalid | Contact your technical team to verify provider configuration; manually follow up with the buyer |
| Magic link says "expired" or "already used" | Link was clicked more than once or 48-hour window has passed | Ask the sales team to send a new magic link |
| "Access denied" on a screen | Your role does not have permission for this action | Contact your Administrator to verify your user role |
| Deposit expired automatically | The 7-day reservation window elapsed without confirmation | Create a new deposit if the buyer is still interested |
| Cannot delete a payment item | Item is in PAID status (terminal state) | PAID items cannot be deleted; contact your administrator if this is an error |
| Login fails despite correct password | Account may be disabled | Contact your Administrator to verify account status |

### 9.2 Getting Help

- Contact your **Platform Administrator** for account issues, role changes, or access problems
- Contact your **technical team / support** for system errors, failed messages, or integration issues
- For data corrections (e.g. wrong contract amount), only an **Admin** can make changes to signed records

---

## 10. Best Practices

### Data Quality
- Always create contacts with accurate email addresses — the buyer portal and messaging depend on them
- Use consistent naming conventions for properties (e.g. `PROJ-A-101`, `PROJ-A-102`)
- Link properties to projects before making them ACTIVE — this ensures correct KPI grouping

### Reservation Workflow
- Confirm deposits promptly — unconfirmed deposits block other buyers for up to 7 days
- Never leave a property in RESERVED status with an expired deposit — run the expiry process to free it
- Download and share the Reservation Certificate with the buyer immediately upon deposit confirmation

### Contract Management
- Review all contract details carefully before signing — signing triggers irreversible state transitions
- Always assign the correct agent to the contract at creation time — commissions depend on this
- Download and archive the Contract PDF immediately after signing for legal records

### Payment Tracking
- Create the full payment schedule immediately after contract signing, while the details are fresh
- Issue items only when you are certain the details are correct — issued items cannot be edited
- Record payments promptly when received to keep the Receivables Dashboard accurate

### Communication
- Use the outbound messaging system for all buyer communications so there is a delivery audit trail
- Monitor the Messages screen daily for FAILED items and follow up manually if needed
- Send buyers their portal magic link as soon as the contract is signed — this builds trust

### Dashboards
- Review the Receivables Dashboard weekly to catch aging balances early
- Use the Commercial Dashboard filters to review individual agent performance regularly
- Export and share the KPI summary with management on a regular cadence (weekly or monthly)

### Security
- Never share your login credentials with colleagues — each user should have their own account
- Disable user accounts immediately when a team member leaves the organisation
- Buyers' magic links should be sent only to verified email addresses

---

## 11. FAQ

**Q: Can a buyer reserve more than one property?**
A: Yes. A buyer (contact) can have deposits or contracts on multiple properties. Each property can only have one active deposit at a time, but a single buyer is not restricted from reserving multiple properties.

---

**Q: What happens if I accidentally sign the wrong contract?**
A: A Manager or Admin can cancel a SIGNED contract. This reverts the property status and removes the commission record. Contact your Administrator immediately if this occurs.

---

**Q: Can an Agent see another agent's contracts?**
A: No. Agents only see contracts, deposits, and payments assigned to them. Managers and Admins can see all team data.

---

**Q: What happens when a deposit expires?**
A: After 7 days without confirmation, the deposit automatically moves to EXPIRED status and the property reverts to ACTIVE — making it available for new reservations.

---

**Q: Can the buyer edit anything in the portal?**
A: No. The Buyer Portal is strictly read-only. Buyers can view contracts, payment schedules, and property details, and download PDFs, but cannot modify any data.

---

**Q: What if an email fails to deliver to the buyer?**
A: The system will retry automatically after 1 minute, 5 minutes, and 30 minutes. If all retries fail, the message is marked FAILED. Check the Messages screen and follow up manually with the buyer.

---

**Q: How is commission calculated?**
A: Commission = `(Agreed Contract Price × Rate%) + Fixed Amount`. The rate and fixed amount are defined by the Administrator in the Commission Rules. A property-specific rule overrides the project default, which overrides the tenant-wide default.

---

**Q: Can I change a payment schedule item after it has been issued?**
A: No. Items in ISSUED, SENT, OVERDUE, or PAID status cannot be edited. Only DRAFT items can be modified. Cancel the item and create a new one if a correction is needed.

---

**Q: How long does a buyer's portal session last?**
A: Sessions last 2 hours from the time the magic link is verified. After expiry, the buyer needs to request a new link from their sales representative.

---

**Q: Where do I see which properties are available for reservation?**
A: Navigate to **Properties** and filter by status **ACTIVE**. These are the properties available for new reservations.

---

**Q: Can I export data from the platform?**
A: CSV export is available for contacts and properties via the import/export screens. Dashboard data can be reviewed on-screen. For advanced data exports, contact your administrator or technical team.

---

**Q: What is the difference between "Outbound Messages" and "Notifications"?**
A: **Outbound Messages** are sent externally to buyers via email or SMS. **Notifications** are internal alerts visible only to logged-in CRM users in the bell icon menu.

---

**Q: What happens to contracts and payments if a user account is disabled?**
A: All historical data is preserved. Contracts, deposits, and payments previously created by the user remain in the system and are still visible to Admins and Managers.

---

## 12. Appendix: Suggested Diagrams & Missing Documentation

### 12.1 Suggested Diagrams to Enhance This Guide

The following diagrams would significantly improve reader comprehension and are recommended for future additions:

| Diagram | Description |
|---|---|
| **Property State Machine** | Visual flowchart of all property states and the triggers that move between them |
| **Contact Lifecycle Diagram** | Funnel/flowchart from NEW_PROSPECT through CLIENT to COMPLETED_CLIENT |
| **Deposit Lifecycle Diagram** | State machine for PENDING → CONFIRMED/CANCELLED/EXPIRED with property impact |
| **Contract Lifecycle Diagram** | DRAFT → SIGNED/CANCELED with associated side effects (commission, property) |
| **Payment Schedule Item Lifecycle** | DRAFT → ISSUED → SENT → PAID/OVERDUE/CANCELED |
| **User Role Hierarchy** | Visual showing ADMIN > MANAGER > AGENT with permission overlaps |
| **Buyer Portal User Journey** | End-to-end flow from magic-link request to portal access |
| **System Component Diagram** | Showing CRM App, Buyer Portal, API, Outbox, DB, and their relationships |
| **Commercial Dashboard Mockup** | Annotated screenshot or wireframe of the main KPI screen |
| **Payment Aging Ladder** | Visual representation of the 4 aging buckets and their escalation actions |

### 12.2 Missing Documentation Identified

The following documentation is absent from the current repository and should be added:

| Missing Document | Priority | Notes |
|---|---|---|
| **Onboarding Quick-Start card** | High | A 1-page PDF for new sales agents covering their first day: login, create a contact, create a contract |
| **Admin Setup Runbook** | High | Step-by-step guide for a fresh tenant onboarding (users, projects, properties, commission rules) |
| **Buyer Portal User Guide (standalone)** | High | Separate, client-ready document to send to buyers explaining the portal |
| **Email / SMS Template Catalogue** | Medium | List of all system-generated message templates with field substitutions documented |
| **CSV Import Format Reference** | Medium | Column names, data types, validation rules, and example files for contact and property imports |
| **API Integration Guide** | Medium | For clients who want to integrate with the REST API — authentication, rate limits, error codes |
| **Commission Rules Cookbook** | Low | Example commission rule configurations for common scenarios |
| **Release Notes / Changelog** | Low | Version history so operators know what changed between deployments |

---

*This User Guide was generated from analysis of the YEM SaaS Platform source code, API definitions, configuration files, and existing documentation as of March 2026.*
