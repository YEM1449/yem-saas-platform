# Client Portal — User Guide

This guide explains the client portal: how to invite buyers, what buyers see, and how the magic link login works.

## Table of Contents

1. [What is the Client Portal](#what-is-the-client-portal)
2. [How Buyers Access the Portal](#how-buyers-access-the-portal)
3. [What Buyers Can See](#what-buyers-can-see)
4. [Sending a Portal Invitation](#sending-a-portal-invitation)
5. [Portal Login Troubleshooting](#portal-login-troubleshooting)

---

## What is the Client Portal

The client portal is a separate, read-only web interface for property buyers. It allows clients to:
- View their own sale contracts
- Check their payment schedule
- Track which installments have been issued and paid

Portal users are your buyers (contacts with a SIGNED contract). They cannot access the main CRM, cannot see other clients' data, and cannot make any changes.

---

## How Buyers Access the Portal

Buyers access the portal via a **magic link** (passwordless login):

1. The buyer navigates to the portal URL (e.g., `https://crm.your-agency.com/portal/login`).
2. They enter their **email address**.
3. The system sends a magic link to that email.
4. The buyer clicks the link and is logged in automatically.
5. The magic link expires after **48 hours** and is single-use.

There are no passwords for portal users. Each login session starts with a fresh magic link.

---

## What Buyers Can See

Once logged in, a buyer sees only data related to their own contacts record:

### Contracts

- List of their sale contracts
- Contract details: property reference, project, agreed price, agent name, status
- PDF download of the signed contract

### Payment Schedule

- All installments on their contract
- For each installment:
  - Label (e.g., "Acompte 2 — 20% at foundation completion")
  - Amount due
  - Due date
  - Status: DRAFT, ISSUED, SENT, OVERDUE, PAID, CANCELED
  - Amount already paid

---

## Sending a Portal Invitation

You do not need to "send" an invitation manually. The portal is self-service:

1. Tell your client the portal URL.
2. Have them enter their email address (the same email they gave when they registered as a contact).
3. They receive the magic link.

**Important:** The buyer's email in the portal must exactly match the email on their contact record in the CRM. If the email does not match, the system sends an email that tells the user their account was not found — for privacy reasons, the system does not confirm or deny whether the email exists.

### Rate limiting

For security, the magic link endpoint allows a maximum of **3 requests per hour per IP address**. This prevents abuse. If a client is having trouble receiving the link, ask them to wait an hour before trying again.

---

## Portal Login Troubleshooting

### "I did not receive the magic link email"

1. Ask the client to check their spam/junk folder.
2. Verify the email address on their contact record in the CRM is correct.
3. Confirm that `EMAIL_HOST` is configured (system administrator task).

### "The magic link is expired or already used"

Magic links expire after 48 hours and are single-use. Ask the client to request a new link from the portal login page.

### "I can log in but see no contracts"

The contact must have a sale contract with their contact ID as the buyer. Check:
1. Open the contact in the CRM.
2. Check the **Timeline** tab for any contracts.
3. If no contract exists, the contact cannot see anything in the portal.

### "The portal shows the wrong contracts"

Each portal session is strictly scoped to the logged-in contact's ID. It is not possible to see another contact's contracts from the portal. If a buyer claims to see another person's contracts, contact your system administrator immediately — this would indicate a serious access control issue.
