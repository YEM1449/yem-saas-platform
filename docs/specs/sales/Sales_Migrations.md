# Sales DB Migrations — Applied and Related
Date: 2026-03-05

This document maps sales-related schema changes already present in Liquibase.

## Core Sales Changesets
### `016-create-sale-contract.yaml`
Introduces `sale_contract` table with:
- tenant/project/property/buyer/agent linkage
- status and pricing fields (`agreed_price`, optional `list_price`)
- lifecycle timestamps (`signed_at`, `canceled_at`)
- optional `source_deposit_id`

Also includes:
- FK constraints to tenant/project/property/contact/user
- KPI-oriented indexes
- partial unique index `uk_sc_property_signed`:
  - one active signed contract per `(tenant_id, property_id)`

### `018-add-buyer-snapshot-to-sale-contract.yaml`
Adds immutable buyer snapshot fields captured at sign time:
- `buyer_type`
- `buyer_display_name`
- `buyer_phone`
- `buyer_email`
- `buyer_ice`
- `buyer_address`

### `019-create-commercial-audit-event.yaml`
Adds append-only commercial audit table used for contract/deposit lifecycle traceability.

## Adjacent Commercial Changesets
### `020`, `021`, `022`
Payment-related schema (schedule, calls, payments) supporting post-sale finance workflows.

### `024-create-commission-rule.yaml`
Commission rules for sales performance reporting.

## Migration Rules
1. Never modify applied changesets.
2. Add new sequential changesets for any schema evolution.
3. Keep service-level validation aligned with DB constraints (both required).

## Validation Checklist for New Sales Schema Changes
- [ ] Added include entry in `db.changelog-master.yaml`
- [ ] Added tenant-scoped indexes where query volume justifies
- [ ] Confirmed rollback strategy (if applicable) for non-production-only scenarios
- [ ] Added/updated integration tests for new constraints and lifecycle behavior
