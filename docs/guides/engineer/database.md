# Database Guide — Engineer Guide

> Legacy note
>
> This guide predates the full migration to the current `societe_id` model and still references the
> earlier `tenant` structure in several examples. For the active schema, use
> [../../context/DATA_MODEL.md](../../context/DATA_MODEL.md) and
> [../../spec/technical-spec.md](../../spec/technical-spec.md).

This guide covers the PostgreSQL schema, Liquibase migration strategy, how to add a changeset, how to work with the database locally, and common schema patterns.

## Table of Contents

1. [Database Overview](#database-overview)
2. [Liquibase Migration Strategy](#liquibase-migration-strategy)
3. [Adding a New Changeset](#adding-a-new-changeset)
4. [Changeset Inventory](#changeset-inventory)
5. [Local Database Operations](#local-database-operations)
6. [Schema Patterns](#schema-patterns)
7. [Indexes and Performance](#indexes-and-performance)
8. [Seed Data](#seed-data)

---

## Database Overview

| Setting | Value |
|---------|-------|
| Engine | PostgreSQL 16 |
| Database name | `hlm` |
| Default schema | `public` |
| Charset | UTF-8 |
| Connection pool | HikariCP (max 20, min idle 5) |

The schema is fully managed by Liquibase. Hibernate is configured with `ddl-auto: validate` — it validates entity mappings against the live schema but does NOT create or modify tables.

---

## Liquibase Migration Strategy

### Immutable Changesets

Every applied changeset is immutable. Liquibase checksums each changeset at application startup; if a checksum does not match the stored value in `DATABASECHANGELOG`, the application crashes with:

```
Caused by: liquibase.exception.ValidationFailedException: Validation Failed:
     1 change sets check sum
```

**Never edit an applied changeset.** If a fix is needed, create a new changeset.

### Additive-Only Rule

New changesets may only:
- `CREATE TABLE`
- `ADD COLUMN`
- `CREATE INDEX`
- `INSERT` seed data
- `DROP TABLE` (for legacy cleanup, done in a new changeset)
- `UPDATE` (for seed data fixes, done in a new changeset)

Dropping columns is avoided because it risks data loss and FK violations.

### Naming Convention

```
{NNN}-{short-description}.yaml
```

Examples:
- `001-create-core-tables.yaml`
- `027-add-login-lockout-fields.yaml`
- `030-update-seed-password.yaml`

Changesets are numbered sequentially. Gap-free numbering is preferred but not enforced.

---

## Adding a New Changeset

1. Determine the next sequence number (check `db.changelog-master.yaml` for the last included file).

2. Create the file at:
   ```
   hlm-backend/src/main/resources/db/changelog/{NNN}-{description}.yaml
   ```

3. Write the changeset in YAML format:

```yaml
databaseChangeLog:
  - changeSet:
      id: {NNN}-{description}
      author: your-name
      changes:
        - createTable:
            tableName: my_new_table
            columns:
              - column:
                  name: id
                  type: uuid
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: tenant_id
                  type: uuid
                  constraints:
                    nullable: false
              - column:
                  name: name
                  type: varchar(200)
                  constraints:
                    nullable: false
              - column:
                  name: created_at
                  type: timestamp
                  constraints:
                    nullable: false
        - addForeignKeyConstraint:
            baseTableName: my_new_table
            baseColumnNames: tenant_id
            referencedTableName: tenant
            referencedColumnNames: id
            constraintName: fk_mnt_tenant
        - createIndex:
            tableName: my_new_table
            indexName: idx_mnt_tenant
            columns:
              - column:
                  name: tenant_id
```

4. Add the new file to `db.changelog-master.yaml`:

```yaml
databaseChangeLog:
  # ... existing entries ...
  - include:
      file: db/changelog/{NNN}-{description}.yaml
      relativeToChangelogFile: false
```

5. Run the application or `./mvnw liquibase:update` to apply. Verify with `docker exec -it hlm-postgres psql -U hlm_user -d hlm -c "\dt"`.

---

## Changeset Inventory

| Changeset | Description |
|-----------|-------------|
| `001` | Core tables: `tenant`, `app_user` |
| `002` | Seed tenant and owner user |
| `003` | `project` table |
| `004` | `property` table |
| `005` | `contact` table |
| `006` | `deposit` table |
| `007` | `sale_contract` table |
| `008` | `contact_interest` table |
| `009` | `commercial_audit` table |
| `010` | `notification` table |
| `011` | `commission_rule` table |
| `012` | `commission` table |
| `013` | `prospect_detail` table |
| `014` | `client_detail` table |
| `015` | `outbound_message` table |
| `016` | `payment_schedule` table |
| `017` | `payment_schedule_item` table |
| `018` | `schedule_payment` table |
| `019` | `property_media` table |
| `020` | Indexes on payment tables |
| `021` | `portal_token` table |
| `022` | GDPR consent fields on `contact` |
| `023` | `data_processing_record` table |
| `024` | Commission rule project scope |
| `025` | Portal token TTL column |
| `026` | `property_reservation` table |
| `027` | Lockout fields on `app_user` (`failed_login_attempts`, `locked_until`) |
| `028` | Drop v1 payment tables (`payment`, `payment_tranche`, `payment_call`, `payment_schedule` v1) |
| `029` | Additional indexes |
| `030` | Update seed password BCrypt hash (Admin123! → Admin123!Secure) |

---

## Local Database Operations

### Connect to the running container

```bash
docker exec -it hlm-postgres psql -U hlm_user -d hlm
```

### Useful psql commands

```sql
-- List all tables
\dt

-- Describe a table
\d contact

-- Show applied Liquibase changesets
SELECT id, author, dateexecuted FROM databasechangelog ORDER BY orderexecuted;

-- Count rows per table (useful for debugging seed data)
SELECT schemaname, tablename, n_live_tup
FROM pg_stat_user_tables
ORDER BY n_live_tup DESC;
```

### Reset the database (development only)

This destroys all data and reapplies all 30 changesets:

```bash
docker compose down -v        # removes postgres_data volume
docker compose up -d postgres
docker compose logs -f hlm-backend  # watch Liquibase apply all changesets
```

### Run Liquibase commands via Maven

```bash
cd hlm-backend

# Apply pending changesets
./mvnw liquibase:update

# Generate a diff report (requires DB connection)
./mvnw liquibase:diff

# Generate SQL for pending changesets without applying
./mvnw liquibase:updateSQL
```

---

## Schema Patterns

### UUID Primary Keys

All tables use UUID v4 primary keys generated by Hibernate (`@GeneratedValue(strategy = GenerationType.UUID)`). PostgreSQL stores them as `uuid` type (16 bytes, no hyphen overhead in storage).

### Tenant Foreign Key

Every domain table (except `tenant` itself) has a `tenant_id uuid NOT NULL REFERENCES tenant(id)`. This is the foundation of the multi-tenancy isolation model. Every query passes `tenant_id` as a WHERE clause predicate.

### Audit Timestamps

All tables have `created_at TIMESTAMP NOT NULL` and `updated_at TIMESTAMP NOT NULL`. The JPA `@PrePersist` / `@PreUpdate` hooks set these automatically. No database triggers are used.

### Soft Delete

Properties use soft delete: `deleted_at TIMESTAMP NULL`. A `NULL` value means the record is active; a non-null value means deleted. All list queries filter `WHERE deleted_at IS NULL`.

### JSONB Metadata

The `commercial_audit` table stores event metadata as a `JSONB` column, allowing flexible per-event-type key-value data without schema changes.

---

## Indexes and Performance

### Standard Indexes

Every table has at minimum:
- Primary key index on `id` (automatic).
- Index on `tenant_id` (all queries filter by tenant first).
- Composite index on `(tenant_id, id)` for direct-lookup queries.

### Payment Table Indexes

`payment_schedule_item` has three composite indexes for dashboard aggregate queries:

```
idx_psi_tenant_contract    (tenant_id, contract_id)
idx_psi_tenant_project_due (tenant_id, project_id, due_date)
idx_psi_tenant_due_status  (tenant_id, due_date, status)
```

### Query Performance Tips

- Always include `tenant_id` in the first position of composite indexes (highest cardinality filter first).
- Use `EXPLAIN ANALYZE` in psql to verify index usage for new queries.
- For dashboard aggregate queries that run frequently (30 s cache TTL), verify they use index-only scans.

---

## Seed Data

Applied by changesets `002` and `030`:

| Field | Value |
|-------|-------|
| Tenant key | `acme` |
| Tenant UUID | `11111111-1111-1111-1111-111111111111` |
| Admin email | `admin@acme.com` |
| Admin UUID | `22222222-2222-2222-2222-222222222222` |
| Admin password | `Admin123!Secure` |
| Admin role | `ROLE_ADMIN` |

The BCrypt hash stored in the DB corresponds to `Admin123!Secure` (15 characters). The old hash (from changeset `002`) used `Admin123!` which failed the 12-character minimum password policy enforced by `@StrongPassword`. Changeset `030` corrected the hash.

To verify the seed user exists:
```sql
SELECT id, email, role, enabled FROM app_user WHERE email = 'admin@acme.com';
```
