# GDPR Rights — User Guide

This guide explains how to handle data subject requests under GDPR and Moroccan Law 09-08: exporting personal data, correcting it, and anonymizing a contact.

## Table of Contents

1. [What are Data Subject Rights](#what-are-data-subject-rights)
2. [Handling an Access Request (Data Export)](#handling-an-access-request-data-export)
3. [Handling a Rectification Request](#handling-a-rectification-request)
4. [Handling an Erasure Request](#handling-an-erasure-request)
5. [When Erasure is Blocked](#when-erasure-is-blocked)
6. [Consent Records](#consent-records)
7. [Privacy Notice](#privacy-notice)

---

## What are Data Subject Rights

Under GDPR (EU) and Moroccan Law 09-08, individuals have rights over their personal data:

| Right | What the person can request | Platform feature |
|-------|----------------------------|-----------------|
| Right of access | "Show me all the data you hold about me" | Data export |
| Right to rectification | "Correct this wrong information" | Rectify contact |
| Right to erasure | "Delete my personal data" | Anonymize contact |
| Right to transparency | "What data do you process and why?" | Privacy notice |

When a person makes a request, you have **30 days** to respond (GDPR standard).

---

## Handling an Access Request (Data Export)

**Required role:** Admin or Manager

1. Find the contact in the CRM.
2. Go to the contact's record.
3. Click **GDPR Actions → Export Data**.
4. A JSON file is downloaded containing all personal data held for this contact:
   - Personal fields (name, email, phone)
   - Consent metadata
   - Qualification preferences (budget, location)
   - Client details (notary, financing)
   - Deposits, contracts, property interests

You may need to format this data into a readable document before sending it to the data subject. The JSON export is the authoritative record of what is held.

**Note:** The export endpoint is rate-limited to prevent automated scraping.

---

## Handling a Rectification Request

**Required role:** Admin or Manager

When a contact says their data is incorrect (wrong email, misspelled name, outdated phone number):

1. Find the contact in the CRM.
2. Click **GDPR Actions → Rectify Data**.
3. Update the relevant fields.
4. Click **Save**.

The contact's record is updated immediately. The `updatedAt` timestamp provides an audit trail.

You can also update contacts via the standard **Edit Contact** flow — the GDPR Rectify action is a dedicated path with explicit documentation for compliance record-keeping.

---

## Handling an Erasure Request

**Required role:** Admin only

The platform implements erasure as **anonymization**, not hard deletion. This is necessary because:
- Financial records (deposits, contracts) must be retained for legal and accounting reasons.
- Deleting the contact row would break these records.
- Anonymization makes re-identification impossible, satisfying GDPR's "right to be forgotten".

To anonymize a contact:

1. Find the contact in the CRM.
2. Click **GDPR Actions → Anonymize Contact**.
3. Read the confirmation message carefully.
4. Click **Confirm**.

After anonymization:
- The contact's name becomes "ANONYMIZED"
- The email becomes `anonymized-{id}@deleted.invalid`
- Phone, first name, last name are cleared
- Prospect detail (preferences, budget) is cleared
- Client detail (notary, financing) is cleared
- `anonymized_at` timestamp is set — this is your proof of erasure

The contact row still exists in the database to preserve financial record integrity, but contains no personally identifiable information.

---

## When Erasure is Blocked

Erasure is **blocked** when the contact has a **SIGNED sale contract**.

Reason: The contract is a legally binding document. The buyer's identity cannot be erased while the contract is active — the other party (your agency) still has legal obligations under that contract.

**Error message:**
```
Cannot anonymize contact: active signed contracts exist
```

**Process to resolve:**
1. Review whether the contract can be legally cancelled (requires legal advice).
2. Once all signed contracts are cancelled, the erasure can proceed.
3. If the contract is already completed (SOLD, project delivered), you may be able to cancel it in the system for GDPR purposes — consult your legal advisor.

---

## Consent Records

When you create or update a contact, always record their consent:

| Field | When to set it |
|-------|---------------|
| Consent given | Tick when the person explicitly agrees to their data being processed |
| Consent date | Date of consent (usually today) |
| Consent method | How they gave consent: WEB_FORM, EMAIL, VERBAL, WRITTEN |
| Processing basis | Legal reason: CONSENT, CONTRACT, LEGAL_OBLIGATION, LEGITIMATE_INTEREST |

For most real estate contacts, the processing basis is **CONTRACT** (processing is necessary to fulfil a property purchase) or **CONSENT** (person opted in for marketing communications).

---

## Privacy Notice

The platform provides a privacy notice accessible at the portal login page and via:
```
GET /api/gdpr/privacy-notice
```

This notice explains what data is collected, why, and data subjects' rights. Review and update it with your legal team to ensure it reflects your agency's actual data processing practices.
