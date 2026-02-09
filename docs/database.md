# Database

## Liquibase layout
- Master file: `hlm-backend/src/main/resources/db/changelog/db.changelog-master.yaml`
- Changesets live in: `hlm-backend/src/main/resources/db/changelog/changes/`
- Naming convention: sequential numeric prefix (e.g., `009-create-property-table.yaml`).

## Key tables & tenant ownership
> Tenant-owned tables include a `tenant_id` foreign key and are filtered in repositories/services.

- **tenant**: tenant registry.
- **app_user**: users per tenant (`tenant_id`, `role`).
- **contact**: contacts per tenant (`tenant_id`).
- **contact_interest**: tenant-scoped join of contacts ↔ properties (`tenant_id`).
- **deposit**: tenant-scoped deposits (`tenant_id`, `contact_id`, `property_id`, `agent_id`).
- **notification**: tenant-scoped in-app notifications (`tenant_id`, `recipient_user_id`).
- **property**: tenant-scoped properties (`tenant_id`).
- **project**, **prospect_detail**, **client_detail**: contact-linked tables (tenant ownership inherited via `contact_id`).

## Seed data (non-sensitive)
- A seed tenant + owner user is inserted in `002-seed-tenant-owner.yaml`.
- The changeset uses fixed UUIDs for reproducibility; do not copy secrets from seed data into docs or tickets.

## Safe migration rules
- **Never edit applied changesets.** Create a new changeset for updates.
- **Use additive changes** (new tables/columns/indexes) where possible.
- **Backfill data** in the changeset when adding non-null columns.
- **Keep tenant_id consistent** for any new tenant-owned table; add FK to `tenant`.
- **Validate locally** with Liquibase enabled (`spring.liquibase.enabled=true`).

## Adding a new changeset
1. Create a new file in `db/changelog/changes/` with the next sequence number.
2. Add an `include` entry to `db.changelog-master.yaml`.
3. If the change touches tenant-owned data, add `tenant_id` + FK + tenant indexes.
4. Run migrations locally and through integration tests.
