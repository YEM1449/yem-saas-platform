# User Guide — CRM-HLM Platform

| Field | Value |
|---|---|
| **Version** | 1.0 |
| **Date** | 2026-02-28 |
| **Audience** | End users (Admin, Manager, Agent) |
| **Status** | Draft |

---

## 1. Quick Start (10 minutes)

### 1.1 Login

1. Open the application in your browser.
2. You will see the **Login** page.
3. Enter your **Tenant Key** (company identifier, provided by your administrator).
4. Enter your **Email** and **Password**.
5. Click **Login**.
6. On success, you are redirected to the main application shell.

> **Note**: If login fails, verify your tenant key, email and password. Contact your administrator if your account is locked.

### 1.2 Navigation Map

After login, the top navigation bar provides access to all modules:

| Nav Item | Route | Who can see it |
|---|---|---|
| **Properties** | `/app/properties` | All users |
| **Contacts** | `/app/contacts` | All users |
| **Prospects** | `/app/prospects` | All users |
| **Notifications** | `/app/notifications` | All users |
| **Messages** | `/app/messages` | All users |
| **Contracts** | `/app/contracts` | All users |
| **Projects** | `/app/projects` | All users |
| **Dashboard** | `/app/dashboard/commercial` | All users |
| **Users** | `/app/admin/users` | Admin only |
| **Logout** | — | All users |

### 1.3 Understanding Your Role

- **Admin**: Full access to everything — manage users, create/delete projects, manage all commercial data, view all dashboards.
- **Manager**: Can create and manage projects, properties, contacts, deposits, and contracts. Cannot delete resources or manage users.
- **Agent**: Can view properties, contacts, prospects. Can create draft contracts and send messages. Sees only their own deposits, contracts, and dashboard data.

---

## 2. Role-Based Guides

### 2.1 Admin Guide

#### User Management (`/app/admin/users`)

1. Navigate to **Users** in the top bar (visible only to Admins).
2. **List users**: The page shows all users in your tenant with their role and status.
3. **Create a user**: Click the create button, fill in email, name, and select a role (Admin, Manager, or Agent). The system generates a temporary password.
4. **Change a user's role**: Find the user and change their role. **Important**: This immediately invalidates the user's current session — they will need to log in again.
5. **Enable/Disable a user**: Toggle the enabled status. Disabling a user immediately invalidates their session.
6. **Reset password**: Click reset password to generate a new temporary password for the user.

#### Tenant & System Oversight

- As Admin, you can view all data across the tenant.
- You can archive projects (see Projects section).
- You can delete properties and remove contact interests.
- You have full access to the commercial dashboard, audit trail, and all reports.

#### Dashboards & KPIs

- Navigate to **Dashboard** → `/app/dashboard/commercial`.
- You see the full tenant summary: sales count, total revenue, average sale value, deposits, active reservations, active prospects.
- Use the filter bar to narrow by date range, project, or specific agent.
- Click into the **Sales drill-down** for transaction-level detail.
- Navigate to a specific project → `/app/projects/:id` to view project-specific KPIs.
- Access the **Audit Trail** via `GET /api/audit/commercial` (API only for now) to review all commercial events.

---

### 2.2 Manager Guide

#### Commercial Oversight

As a Manager, your main responsibilities are:

1. **Managing Projects** (`/app/projects`):
   - Create new projects with a name and details.
   - Update project information.
   - View project-level KPIs (property counts by status, deposits, sales).
   - *You cannot archive projects — ask an Admin.*

2. **Managing the Property Catalog** (`/app/properties`):
   - Create properties within active projects. Choose the type (Villa, Appartement, Duplex, Studio, T2, T3, Commerce, Lot, Terrain Vierge).
   - Set pricing, surface area, and other characteristics.
   - Update property details as needed.
   - *You cannot delete properties — ask an Admin.*

3. **Working with Contacts & Prospects** (`/app/contacts`, `/app/prospects`):
   - Create contacts (prospects).
   - Update contact information and transition their status through the pipeline.
   - Register interests (link a prospect to properties they're interested in).
   - Convert qualified prospects to clients.

4. **Deposit / Reservation Management**:
   - Navigate to a prospect's detail page (`/app/prospects/:id`).
   - Create a deposit to reserve a property for a contact.
   - Confirm or cancel deposits as needed.
   - Download the **Reservation PDF** certificate for any deposit.

5. **Contract Management** (`/app/contracts`):
   - View all contracts in your tenant.
   - Sign contracts (moves property to SOLD, captures buyer data).
   - Cancel contracts if needed (reverts property status).
   - Download the **Contract PDF** for any contract.
   - Send quick Email/SMS messages to buyers directly from the contracts list.

6. **KPIs & Reporting**:
   - Access the commercial dashboard for full-tenant view.
   - Filter by date range, project, or agent to analyze team performance.

---

### 2.3 Agent Guide

#### Daily Workflow

As an Agent, your focus is on prospect interactions:

1. **Browse Properties** (`/app/properties`):
   - View available properties with status badges (Draft, Active, Reserved, Sold).
   - Filter by type and status to find available properties for your prospects.

2. **Manage Your Prospects** (`/app/prospects`):
   - View your assigned prospects.
   - Navigate to a prospect's detail page to see their interests and deposit history.

3. **Create Draft Contracts** (`/app/contracts`):
   - You can create draft contracts to initiate the sales process.
   - *Signing and canceling contracts requires a Manager or Admin.*

4. **Send Messages** (`/app/messages`):
   - Compose and send emails or SMS messages to contacts.
   - Use quick-action buttons on contracts and deposits to send pre-filled messages.
   - View your message history and status.

5. **View Your Dashboard** (`/app/dashboard/commercial`):
   - Your dashboard is automatically scoped to your own activity.
   - See your sales count, revenue, deposits, and conversion rates.

6. **Download PDFs**:
   - You can download Reservation PDFs and Contract PDFs **only for your own deposits/contracts**.
   - If you try to access another agent's document, you'll see a 404 error.

7. **Notifications** (`/app/notifications`):
   - Check notifications for deposit events (created, confirmed, cancelled, expired).
   - Mark notifications as read.

---

## 3. Step-by-Step Workflows

### 3.1 Projects & Properties

#### Create a Project
1. Go to **Projects** (`/app/projects`).
2. Click "Create Project" (Admin/Manager only).
3. Enter the project name (e.g., "Résidence Sunset").
4. The project is created with status **ACTIVE**.

#### Create a Property
1. Go to **Properties** (`/app/properties`).
2. Click "Create Property" (Admin/Manager only).
3. Select a **Project** (must be ACTIVE).
4. Choose **Property Type** (e.g., APPARTEMENT, VILLA, LOT).
5. Enter **Reference Code** (unique per tenant, e.g., "A-101").
6. Fill in **Price**, **Surface Area**, and type-specific fields.
7. The property is created with status **DRAFT**.
8. Update the status to **ACTIVE** when ready to market.

#### View Project KPIs
1. Go to **Projects** → click on a project name.
2. The **Project Detail** page (`/app/projects/:id`) shows KPI cards: total properties, properties by status, deposit count, sales count.

---

### 3.2 Contacts / Prospects Pipeline

#### Create a Contact
1. Go to **Contacts** (`/app/contacts`) — Admin/Manager only.
2. Click "Create Contact".
3. Fill in: name, email (unique per tenant), phone, and prospect details (budget, source).
4. The contact starts with status **PROSPECT**.

#### Advance a Prospect Through the Pipeline
1. Go to the contact's detail page (`/app/contacts/:id`).
2. Change the status following the allowed transitions:
   - PROSPECT → QUALIFIED_PROSPECT or LOST
   - QUALIFIED_PROSPECT → CLIENT or LOST or back to PROSPECT
   - CLIENT → ACTIVE_CLIENT → COMPLETED_CLIENT → REFERRAL
   - LOST → PROSPECT (re-activation)

#### Register Interest
1. On a prospect's detail page (`/app/prospects/:id`).
2. Link the prospect to specific properties they're interested in (Admin/Manager only).

#### Convert to Client
1. When a prospect is ready, use "Convert to Client" (Admin/Manager only).
2. Provide client details (company info, ICE, SIRET if applicable).

---

### 3.3 Deposits / Reservations

#### Create a Deposit (Reserve a Property)
1. Navigate to the prospect's detail page (`/app/prospects/:id`).
2. In the deposits section, click "Create Deposit".
3. Select the **Property** (must be ACTIVE) and enter the **deposit amount** and **due date**.
4. On creation:
   - The deposit status is set to **PENDING**.
   - The property status changes to **RESERVED**.
5. The property is now locked and cannot be reserved by another contact.

#### Confirm a Deposit
1. On the prospect's detail page, find the PENDING deposit.
2. Click "Confirm" (Admin/Manager only).
3. The deposit moves to **CONFIRMED**.

#### Cancel a Deposit
1. Find the deposit and click "Cancel" (Admin/Manager only).
2. The deposit moves to **CANCELLED**.
3. The property reverts to **ACTIVE** (available again).

#### Automatic Expiry
- Deposits past their due date are automatically expired by the system (hourly check).
- Expired deposits release the property back to ACTIVE status.

#### Download Reservation PDF
1. On the prospect's detail page, locate the deposit.
2. Click the **PDF download** button.
3. The "Attestation de Réservation" PDF is downloaded.
4. *Agents can only download PDFs for their own deposits.*

---

### 3.4 Contracts / Sales

#### Create a Contract
1. Go to **Contracts** (`/app/contracts`).
2. Click "Create Contract".
3. Select the **property**, **buyer contact**, and confirm the **agreed price**.
4. Optionally provide a **list price** (original price, for discount analytics).
5. The contract is created with status **DRAFT**.

#### Sign a Contract (Admin/Manager only)
1. Find the DRAFT contract in the list.
2. Click "Sign".
3. Effects:
   - Contract status → **SIGNED**.
   - Property status → **SOLD**.
   - An immutable **buyer snapshot** is captured (name, phone, email, address, ICE).
   - A `CONTRACT_SIGNED` audit event is recorded.

#### Cancel a Contract (Admin/Manager only)
1. Find the contract and click "Cancel".
2. Effects depend on whether an active deposit exists:
   - If a CONFIRMED deposit exists → property reverts to **RESERVED**.
   - If no active deposit → property reverts to **ACTIVE**.
   - A `CONTRACT_CANCELED` audit event is recorded.

#### Download Contract PDF
1. In the contracts list (`/app/contracts`), click the **PDF** button.
2. The bilingual contract PDF is downloaded containing: property details, prices, buyer info, agent info, and signature areas.
3. *Agents can only download PDFs for their own contracts.*

#### Quick Messaging from Contracts
- **Email** button: visible when the buyer has an email address. Sends a pre-filled email.
- **SMS** button: visible when the buyer has a phone number. Sends a pre-filled SMS.
- Both buttons link the message to the contract for traceability.

---

### 3.5 Dashboards / KPI Interpretation

#### Commercial Dashboard (`/app/dashboard/commercial`)

**Filter Bar**: Set the date range, optionally filter by project or agent.

**KPI Cards** (top of page):

| Card | What It Shows |
|---|---|
| Sales Count | Number of signed contracts in the period |
| Sales Total | Sum of agreed prices for signed contracts |
| Avg Sale Value | Average agreed price per sale |
| Deposits Count | Number of confirmed deposits in the period |
| Active Reservations | Current open reservations (snapshot, not period-filtered) |
| Avg Reservation Age | Average days since reservation for active reservations |
| Active Prospects | Current prospects + qualified prospects (tenant-wide) |

**Charts**:
- **Sales by Day**: Bar chart showing daily sales amount.
- **Deposits by Day**: Bar chart showing daily deposit amount.

**Tables**:
- **Top 10 Projects**: Projects ranked by sales amount.
- **Top 10 Agents**: Agents ranked by sales amount.

**Inventory**:
- Property counts by status (Draft, Active, Reserved, Sold, etc.).
- Property counts by type (Villa, Appartement, etc.).

**Conversion Metrics**:
- Deposit-to-Sale conversion rate.
- Average days from deposit to sale.

#### Sales Drill-Down (`/app/dashboard/commercial/sales`)
- Paginated table of individual sales transactions.
- Accessible from the dashboard.

> **Agent note**: Your dashboard is automatically filtered to show only your own data. You cannot see other agents' sales or deposits.

---

### 3.6 Messages (`/app/messages`)

#### Send a Message
1. Go to **Messages** (`/app/messages`).
2. Click "Compose" or use the inline compose form.
3. Select **Channel**: Email or SMS.
4. Enter the **Recipient** (email or phone) or select a **Contact** (auto-fills recipient).
5. Write the **Body**.
6. Click "Send".
7. The message is queued (status: PENDING) and dispatched asynchronously.

#### View Message History
- The messages list shows all sent/queued messages.
- Filter by: channel (Email/SMS), status (Pending/Sent/Failed), contact, date range.
- Track delivery status: PENDING → SENT or FAILED.

❌ **Not available yet**: Real email/SMS delivery is not connected. Messages are logged but not actually sent. This will be enabled when production email/SMS providers are configured.

---

### 3.7 Notifications (`/app/notifications`)

1. Go to **Notifications**.
2. View unread notifications (deposit events: created, pending, due soon, confirmed, cancelled, expired).
3. Click on a notification to mark it as read.

---

## 4. Best Practices (Real Estate CRM)

### 4.1 Avoid Double Booking

- The system **automatically prevents** double-booking: when a deposit is created, the property moves to RESERVED status.
- If you see error `PROPERTY_ALREADY_RESERVED`, it means another deposit already exists for that property.
- If you see error `PROPERTY_ALREADY_SOLD`, it means a signed contract already exists.
- Always check property status before promising availability to a prospect.

### 4.2 Handling Cancellations

- **Reservation cancellation**: Cancel the deposit → property returns to ACTIVE (available).
- **Sale cancellation**: Cancel the signed contract → property returns to RESERVED (if a confirmed deposit exists) or ACTIVE (no deposit).
- All cancellation events are recorded in the audit trail for compliance.
- Always communicate cancellations to the relevant parties promptly (use the messaging feature).

### 4.3 Keeping Data Clean

- **Reference codes**: Use a consistent naming convention (e.g., "A-101", "B-205") — they must be unique per tenant.
- **Contact emails**: Keep emails unique and up-to-date — duplicates are rejected.
- **Project organization**: Archive projects that are completed rather than deleting them. Archived projects retain their data but block new assignments.
- **Status discipline**: Follow the contact pipeline in order. The system enforces valid transitions (e.g., you can't jump from PROSPECT to ACTIVE_CLIENT).

---

## 5. Troubleshooting

### 5.1 Common Errors

| Error | HTTP Status | What It Means | What To Do |
|---|---|---|---|
| `UNAUTHORIZED` | 401 | Your session has expired or your token is invalid | Log out and log in again |
| `FORBIDDEN` | 403 | Your role doesn't permit this action | Contact your Admin to check your permissions |
| `NOT_FOUND` | 404 | The resource doesn't exist or belongs to another tenant | Verify the ID; if you're an Agent, you may not have access to other agents' data |
| `VALIDATION_ERROR` | 400 | Input data is invalid | Check required fields, format (email, phone), and field length limits |
| `PROPERTY_ALREADY_RESERVED` | 409 | Another deposit already exists for this property | Check the property status; cancel the existing deposit first |
| `PROPERTY_ALREADY_SOLD` | 409 | A signed contract already exists for this property | The property is sold — cancel the existing contract first if needed |
| `DEPOSIT_ALREADY_EXISTS` | 409 | A deposit already exists for this contact+property combination | Check existing deposits for the contact |
| `ARCHIVED_PROJECT` | 400 | You're trying to assign a property to an archived project | Use an ACTIVE project, or ask Admin to reactivate the project |
| `INVALID_STATUS_TRANSITION` | 409 | The status change you requested is not allowed | Follow the pipeline order (see Contact Status Machine in Functional Spec) |
| `USER_EMAIL_EXISTS` | 409 | A user with this email already exists in the tenant | Use a different email address |

### 5.2 "Why Can't I See KPIs?"

- **Project KPIs** (`/app/projects/:id`): Requires **ADMIN** or **MANAGER** role. If you're an Agent, you won't see KPI cards.
- **Commercial Dashboard** (`/app/dashboard/commercial`): Available to all roles, but **Agents** only see their own data. If your dashboard looks empty, you may have no sales/deposits in the selected period. Try adjusting the date range.
- **Audit Trail**: Available to **ADMIN** and **MANAGER** only via the API (`GET /api/audit/commercial`). Agents receive a 403 error.

### 5.3 "My Session Was Suddenly Logged Out"

- If an Admin changed your role or disabled your account, your JWT is immediately invalidated.
- Log in again. If you can't log in, contact your administrator — your account may have been disabled.

---

## 6. Features Not Yet Available

The following features are planned in the CDC but **not yet implemented**:

| Feature | Description | Status |
|---|---|---|
| Appels de Fonds (Call for Funds) PDF | Payment schedule document generation | ❌ Planned |
| Land Prospecting Module | Terrain database, COS/CES, acquisition pipeline | ❌ Planned |
| Administrative Workflow | Authorization tracking, document archiving | ❌ Planned |
| Construction Tracking | Gantt planning, phases, site journal | ❌ Planned |
| Stock Management | QR/NFC, transfers, inventory | ❌ Planned |
| Purchases & Suppliers | Purchase orders, invoice matching | ❌ Planned |
| Finance Module | Budget vs actual, margins, accounting export | ❌ Planned |
| After-Sales (SAV) | Client tickets, intervention tracking | ❌ Planned |
| CSV Import/Export | Bulk data import for properties and contacts | ❌ Planned |
| Real Email/SMS Delivery | Production SMTP + Twilio integration | ❌ Planned (infrastructure) |

---

## 7. Glossary (FR / EN)

| French | English | Definition |
|---|---|---|
| Promoteur | Developer/Promoter | Real estate development company (= Tenant) |
| Société | Company/Tenant | An organization with isolated data in the system |
| Projet | Project | A real estate development project containing properties |
| Bien / Lot | Property / Unit | An individual real estate unit (apartment, villa, lot) |
| Prospect | Prospect | A potential buyer in the early pipeline stages |
| Client | Client | A converted prospect who has committed to a purchase |
| Acompte / Dépôt | Deposit | A financial commitment to reserve a property |
| Réservation | Reservation | The act of reserving a property via a deposit |
| Contrat de Vente | Sales Contract | The formal agreement for the sale of a property |
| Acte de Vente | Deed of Sale | The final legal document (not yet in system) |
| Appel de Fonds | Call for Funds | A payment request sent to the buyer during construction |
| Attestation de Réservation | Reservation Certificate | The PDF document generated for a deposit |
| Pipeline Commercial | Sales Pipeline | The stages a prospect goes through to become a client |
| Tableau de Bord | Dashboard | The KPI overview screen |
| Chiffre d'Affaires | Revenue / Sales Amount | Total value of signed contracts |
| Taux de Conversion | Conversion Rate | Percentage of deposits that lead to signed contracts |
| ICE | ICE (Tax ID - Morocco) | Moroccan company tax identifier |
| SIRET | SIRET (Company ID - France) | French company registration number |
| COS | COS (Floor Area Ratio) | Coefficient d'Occupation du Sol |
| CES | CES (Building Coverage Ratio) | Coefficient d'Emprise au Sol |
| Gros Œuvre | Structural Work | Main construction phase (foundation, walls, roof) |
| Second Œuvre | Secondary Work | Interior finishing (plumbing, electrical, flooring) |
| SAV | After-Sales Service | Post-delivery support and maintenance |
| RGPD / GDPR | GDPR | General Data Protection Regulation |
