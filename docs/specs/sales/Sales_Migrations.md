# Sales DB Migrations (Plan)
Date: 2026-02-26

## New tables (suggested names; adapt to repo naming)
1) sale_contract
- id UUID PK
- tenant_id UUID FK -> tenant
- project_id UUID FK -> project
- property_id UUID FK -> property
- buyer_contact_id UUID FK -> contact
- agent_id UUID FK -> app_user (or agent table)
- status VARCHAR
- agreed_price DECIMAL
- list_price DECIMAL NULL
- created_at TIMESTAMP NOT NULL
- signed_at TIMESTAMP NULL
- canceled_at TIMESTAMP NULL
- source_deposit_id UUID NULL FK -> deposit

## Constraints
- FK constraints should be tenant-safe (enforce tenantId matching at service-level; DB can’t easily enforce across multiple tables without composite keys)
- Postgres recommended:
  - partial unique index: only one active SIGNED contract per (tenant_id, property_id) where canceled_at is null

## Indexes (for KPI performance)
- sale_contract(tenant_id, signed_at)
- sale_contract(tenant_id, project_id, signed_at)
- sale_contract(tenant_id, agent_id, signed_at)
- sale_contract(tenant_id, property_id)
