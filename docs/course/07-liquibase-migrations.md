# Module 07 — Liquibase Migrations

## Learning Objectives

- Explain the additive-only migration rule and why it exists
- Write a correct Liquibase changeset in YAML
- Understand what happens when a changeset is edited after deployment

---

## The Rule: Never Edit an Applied Changeset

Liquibase checksums each changeset when it is applied. On the next startup, Liquibase re-computes the checksum and compares it to the stored value in the `DATABASECHANGELOG` table. If they differ:

```
Caused by: liquibase.exception.ValidationFailedException: Validation Failed:
     1 change sets check sum
     db/changelog/005-create-contact.yaml::005::author was: XXXXXXX but is now: YYYYYYY
```

The application crashes and cannot start. In production, this means downtime.

**Rule:** Once a changeset is merged to main, it is immutable forever. Apply fixes via a new changeset.

---

## Additive-Only Pattern

New changesets may:
- `CREATE TABLE`
- `ADD COLUMN`
- `CREATE INDEX`
- `INSERT` seed data
- `UPDATE` seed data (in a new changeset, e.g., changeset `030` updated the seed password)
- `DROP TABLE` (for cleanup — but this is still additive in the "new changeset" sense)

Avoid `DROP COLUMN` — it removes data permanently and breaks rollback.

---

## Changeset YAML Structure

```yaml
databaseChangeLog:
  - changeSet:
      id: 031-create-example
      author: your-name
      changes:
        - createTable:
            tableName: example
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
                  defaultValueComputed: now()
                  constraints:
                    nullable: false
        - addForeignKeyConstraint:
            baseTableName: example
            baseColumnNames: tenant_id
            referencedTableName: tenant
            referencedColumnNames: id
            constraintName: fk_example_tenant
        - createIndex:
            tableName: example
            indexName: idx_example_tenant
            columns:
              - column:
                  name: tenant_id
```

---

## Adding the Changeset to the Changelog

`db.changelog-master.yaml`:
```yaml
  - include:
      file: db/changelog/031-create-example.yaml
      relativeToChangelogFile: false
```

Add it at the end of the file.

---

## Running Liquibase

Liquibase runs automatically on application startup. To run manually:

```bash
cd hlm-backend
./mvnw liquibase:update
```

To generate SQL without applying:
```bash
./mvnw liquibase:updateSQL
```

---

## Source Files

| File | Purpose |
|------|---------|
| `src/main/resources/db/changelog/db.changelog-master.yaml` | Master changelog |
| `src/main/resources/db/changelog/001-create-core-tables.yaml` | Example: core tables |
| `src/main/resources/db/changelog/027-add-login-lockout-fields.yaml` | Example: ADD COLUMN |
| `src/main/resources/db/changelog/030-update-seed-password.yaml` | Example: UPDATE in new changeset |

---

## Exercise

1. Open `db.changelog-master.yaml` and count the changesets.
2. Open `027-add-login-lockout-fields.yaml` and note how it adds columns to an existing table.
3. Open `030-update-seed-password.yaml` and note how it corrects seed data without modifying `002-seed-tenant-owner.yaml`.
4. Create a new changeset `031-add-contact-notes.yaml` that adds a `notes TEXT NULL` column to the `contact` table. Include it in `db.changelog-master.yaml`.
