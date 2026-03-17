# Contacts — User Guide

This guide covers creating and managing contacts (prospects and clients), tracking their status through the sales workflow, and recording their property interests.

## Table of Contents

1. [Contact Types and Statuses](#contact-types-and-statuses)
2. [Creating a Contact](#creating-a-contact)
3. [Converting a Contact to Prospect](#converting-a-contact-to-prospect)
4. [Converting a Contact to Client](#converting-a-contact-to-client)
5. [Updating Contact Status](#updating-contact-status)
6. [Recording Property Interests](#recording-property-interests)
7. [Contact Timeline](#contact-timeline)
8. [Finding Contacts](#finding-contacts)

---

## Contact Types and Statuses

Contacts move through two type classifications and a detailed status workflow.

### Contact Type

| Type | When it applies |
|------|----------------|
| **Prospect** | Person who has not yet committed financially |
| **Temp Client** | Prospect with a pending (unconfirmed) deposit — temporary for up to 7 days |
| **Client** | Person with a confirmed deposit or signed contract |

### Contact Status

The status tracks where the contact is in the sales funnel:

| Status | Meaning |
|--------|---------|
| PROSPECT | Initial lead, qualification not yet done |
| QUALIFIED_PROSPECT | Lead evaluated; budget and preferences confirmed |
| CLIENT | Active buyer with a confirmed commitment |
| ACTIVE_CLIENT | Buyer with an ongoing transaction |
| COMPLETED_CLIENT | Transaction completed successfully |
| REFERRAL | Completed client who has referred others |
| LOST | Contact dropped out of the funnel |

### Allowed Status Transitions

```
PROSPECT → QUALIFIED_PROSPECT or LOST
QUALIFIED_PROSPECT → PROSPECT, CLIENT, or LOST
CLIENT → ACTIVE_CLIENT, COMPLETED_CLIENT, or LOST
ACTIVE_CLIENT → COMPLETED_CLIENT or LOST
COMPLETED_CLIENT → REFERRAL
LOST → PROSPECT (re-engage)
```

---

## Creating a Contact

1. Go to **Contacts** in the sidebar.
2. Click **New Contact**.
3. Fill in:
   - **Full name** (required)
   - **Email** (required, must be unique within your agency)
   - **Phone** (optional)
   - **Consent given** — Check this if the contact has given GDPR consent for data processing
   - **Consent method** — How they gave consent (web form, email, verbal, written)
4. Click **Save**.

The contact is created with status **PROSPECT** by default.

---

## Converting a Contact to Prospect

Once you qualify a lead, convert them to a Prospect with detailed preferences:

1. Open the contact's record.
2. Click **Convert to Prospect**.
3. Fill in qualification details:
   - **Budget** — Maximum budget in local currency
   - **Preferred surface area** — Minimum m²
   - **Location preferences** — Preferred neighbourhoods or cities
   - **Source** — How they found you (referral, website, exhibition, etc.)
   - **Notes** — Any other qualification notes
4. Click **Save**.

The contact status advances to **QUALIFIED_PROSPECT**.

---

## Converting a Contact to Client

When a contact has made a formal commitment (deposit confirmed), convert them to a Client:

1. Open the contact's record.
2. Click **Convert to Client**.
3. Fill in client details:
   - **Client kind** — Individual, company, joint purchase, etc.
   - **Financing type** — Cash, mortgage, etc.
   - **Notary** — Assigned notary name and details
4. Click **Save**.

The contact type changes to **CLIENT**.

---

## Updating Contact Status

1. Open the contact's record.
2. Click the status dropdown or **Update Status** button.
3. Select the new status.
4. Add a note (optional).
5. Click **Save**.

Invalid transitions (e.g., REFERRAL → PROSPECT) are blocked by the system with an error message.

---

## Recording Property Interests

Track which properties a contact has expressed interest in:

1. Open the contact's record.
2. Click the **Interests** tab.
3. Click **Add Interest**.
4. Select the property.
5. Set the interest status: **ACTIVE**, **CONVERTED**, or **DROPPED**.
6. Click **Save**.

Property interests feed the dashboard's "prospects by source" funnel and help agents prioritise follow-up.

---

## Contact Timeline

The contact timeline shows a chronological history of all activity:

- Status changes
- Deposits created and confirmed
- Reservations created and status updates
- Contracts created and signed
- Property interests added

Open a contact's record and click the **Timeline** tab to view the full history.

---

## Finding Contacts

Use the search and filter bar at the top of the Contacts list:

| Filter | Options |
|--------|---------|
| Search | Name or email (partial match) |
| Status | PROSPECT, QUALIFIED_PROSPECT, CLIENT, etc. |
| Type | PROSPECT, CLIENT |
| Date range | Creation date |

Contacts from other tenants (agencies) are never visible, even with admin access.
