# Dashboard — User Guide

This guide explains the commercial dashboard KPIs, how to filter the view, and how to interpret the data.

## Table of Contents

1. [Dashboard Access by Role](#dashboard-access-by-role)
2. [KPI Cards](#kpi-cards)
3. [Discount Analytics](#discount-analytics)
4. [Prospect Source Funnel](#prospect-source-funnel)
5. [Receivables Dashboard](#receivables-dashboard)
6. [Filtering the Dashboard](#filtering-the-dashboard)
7. [Data Freshness](#data-freshness)

---

## Dashboard Access by Role

| Role | What they see |
|------|--------------|
| Admin | Full tenant-wide data; can filter by agent or project |
| Manager | Full tenant-wide data; can filter by agent or project |
| Agent | Only their own sales data (filter by agent is locked to self) |

---

## KPI Cards

The main dashboard displays these cards:

### Contracts Signed

Total number of contracts signed in the selected period. Click to view the contracts list filtered by the same criteria.

### Total Revenue

Sum of `agreedPrice` on all signed contracts in the period. This is the total committed sale value, not the amount received.

### Average Contract Value

`Total Revenue ÷ Contracts Signed`. Useful for tracking deal size trends.

### Deposits — Pending

Deposits created but not yet confirmed. These represent potential buyers who have committed but whose deposits have not been processed. Monitor this number to ensure timely processing.

### Deposits — Confirmed

Deposits formally confirmed. Each confirmed deposit has a corresponding CLIENT contact.

### Properties Available

Properties currently in ACTIVE status, ready for sale.

### Properties Sold

Properties in SOLD status. These have a signed contract.

### Properties On Hold

Properties in RESERVED status — either a deposit is pending or a reservation is active. The **Expiring Soon** sub-count shows holds expiring in the next 7 days.

---

## Discount Analytics

The discount section shows how much price negotiation is happening across your portfolio:

| Metric | Description |
|--------|-------------|
| Average discount % | Mean of `(listPrice - agreedPrice) / listPrice × 100` across all signed contracts |
| Maximum discount % | Largest discount given on a single contract |
| Discount by agent | Each agent's average and maximum discount |

A high average discount may indicate that list prices need adjustment, or that agents are over-negotiating. This data is only meaningful when `listPrice` is set accurately on each contract.

---

## Prospect Source Funnel

This chart shows where your prospects are coming from:

| Source | Examples |
|--------|---------|
| REFERRAL | Referred by an existing client |
| WEBSITE | Inbound from your website |
| EXHIBITION | Met at a property exhibition |
| COLD_CALL | Outbound contact |
| WALK_IN | Walked into the office |
| OTHER | Miscellaneous |

The funnel helps you identify which acquisition channels are most productive. Sources are set when converting a contact to Prospect.

---

## Receivables Dashboard

Access via the **Receivables** tab on the dashboard (Admin and Manager only).

The receivables view shows outstanding payment schedule items bucketed by aging:

| Bucket | Age of outstanding items |
|--------|------------------------|
| Current | Not yet due |
| 1–30 days overdue | Up to 30 days past due date |
| 31–60 days overdue | 31 to 60 days past due date |
| 61–90 days overdue | 61 to 90 days past due date |
| 90+ days overdue | More than 90 days past due date |

Also shown:
- **Total issued** — Sum of all issued payment schedule items
- **Total received** — Sum of all recorded payments
- **Outstanding by project** — Breakdown per project

Use this view to prioritise collection follow-up. Items in the 61–90 and 90+ buckets need immediate attention.

---

## Filtering the Dashboard

Use the filter bar at the top of the dashboard:

| Filter | Options |
|--------|---------|
| Date from | Start of the period |
| Date to | End of the period |
| Agent | Select a specific agent (Admin and Manager only) |
| Project | Select a specific project |

Leaving a filter blank shows all-time or all-agent data (depending on your role).

---

## Data Freshness

Dashboard data is cached for 30 seconds. This means:
- A contract signed just now may appear on the dashboard within 30 seconds.
- Refresh the page after 30 seconds if you need the most current figures.

The 30-second cache is a performance optimisation — complex dashboard aggregates can take 100–200 ms to compute, and caching prevents repeated database load when multiple users view the dashboard simultaneously.
