# Module 07 — Liquibase Migrations

## Learning Objectives

- Explain why applied changesets are immutable
- Understand the structure and evolution of this repository’s changelog history
- Write a new changeset that fits the platform’s rollout discipline
- Read older `tenant`-named changesets without confusing them with current domain terminology

## Why Liquibase Discipline Matters Here

This platform is a shared SaaS system with:

- long-lived production databases
- incremental feature rollout
- historical schema evolution from `tenant` naming to `societe` naming

That means schema history is part of the product, not just build tooling.

## Immutable Changesets

Once a changeset has been applied in any environment that matters, treat it as immutable.

Why:

- Liquibase stores the checksum in `DATABASECHANGELOG`
- editing the file changes the checksum
- the next startup fails validation

Typical failure shape:

```text
Validation Failed:
1 change sets check sum
... was: <old> but is now: <new>
```

The correct fix is almost always:

- add a new changeset
- do not rewrite the old one

## Repository Structure

Master changelog:

- [db.changelog-master.yaml](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/resources/db/changelog/db.changelog-master.yaml)

Changesets live under:

- [db/changelog/changes/](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/resources/db/changelog/changes)

The master file includes them in order. New files are appended at the end.

## Understanding The Historical Timeline

The changelog tells a story:

### Early foundation

- `001` to `030`
- core user, contact, property, deposit, reservation, payment, and portal foundations
- some filenames still use `tenant`

### Société migration

- `031` to `035`
- creation of société structures and migration away from legacy naming
- especially important:
  - `031-create-societe.yaml`
  - `032-create-app-user-societe.yaml`
  - `033-rename-tenant-id-to-societe-id.yaml`

### Hardening and scale-up

- later changesets add performance indexes, security fields, RLS, ShedLock, media, tasks, documents, and tranche generation

This historical context matters because you will still see immutable early filenames such as:

- `001-init-tenant-user.yaml`
- `002-seed-tenant-owner.yaml`
- `047-rename-tenant-indexes.yaml`

Those names remain because schema history cannot be rewritten safely.

## Additive-Only In Practice

In this codebase, “additive-only” means:

- add tables
- add columns
- add indexes
- update data through a new changeset
- rename or migrate through a new changeset
- deprecate and replace instead of mutating history

It does not mean the schema never becomes cleaner. It means the cleanup itself is represented as a new migration step.

## What A Good Changeset Looks Like

A good changeset is:

- small
- single-purpose
- clearly named
- safe to run once
- understandable without oral history

Example structure:

```yaml
databaseChangeLog:
  - changeSet:
      id: 060-add-project-labels
      author: your-name
      changes:
        - createTable:
            tableName: project_label
            columns:
              - column:
                  name: id
                  type: uuid
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: societe_id
                  type: uuid
                  constraints:
                    nullable: false
              - column:
                  name: project_id
                  type: uuid
                  constraints:
                    nullable: false
              - column:
                  name: label
                  type: varchar(100)
                  constraints:
                    nullable: false
        - createIndex:
            tableName: project_label
            indexName: idx_project_label_societe_project
            columns:
              - column:
                  name: societe_id
              - column:
                  name: project_id
```

## Naming Guidance

Prefer changeset names that describe the business or structural intent:

- `060-add-project-labels.yaml`
- `061-index-contact-email-by-societe.yaml`

Avoid vague names like:

- `060-fix-stuff.yaml`
- `061-update-schema.yaml`

## How To Add A Changeset

1. create a new file under `db/changelog/changes/`
2. append it at the end of `db.changelog-master.yaml`
3. run the application or Liquibase locally
4. verify the database shape
5. if needed, add integration tests that depend on the new schema

Do not insert the file in the middle of history unless there is a very specific reason and you fully understand the consequences.

## Real Examples Worth Reading

### Security state

- [011-add-user-token-version.yaml](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/resources/db/changelog/changes/011-add-user-token-version.yaml)

Why it matters:

- shows a focused column addition tied directly to auth behavior

### Naming migration

- [033-rename-tenant-id-to-societe-id.yaml](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/resources/db/changelog/changes/033-rename-tenant-id-to-societe-id.yaml)

Why it matters:

- demonstrates how the platform migrated terminology safely instead of rewriting early history

### RLS rollout

- [050-enable-rls-phase1.yaml](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/resources/db/changelog/changes/050-enable-rls-phase1.yaml)
- [051-rls-phase2-all-tables.yaml](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/resources/db/changelog/changes/051-rls-phase2-all-tables.yaml)

Why they matter:

- show that large infrastructure changes are often broken into staged migrations

### Schema hardening

- [057-schema-hardening.yaml](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/resources/db/changelog/changes/057-schema-hardening.yaml)

Why it matters:

- demonstrates that cleanup and hardening still happen as forward-only migrations

## Common Mistakes

### Mistake 1: editing an old file because “it hasn’t reached prod yet”

In a collaborative repo, you often do not control every environment where a migration may already have run.

### Mistake 2: using current terminology to justify renaming old immutable files

Use current terminology in new files. Leave old historical filenames alone.

### Mistake 3: mixing unrelated concerns in one changeset

A migration that adds columns, renames indexes, seeds unrelated data, and updates business defaults is hard to reason about and hard to recover from.

### Mistake 4: forgetting the application code that depends on the migration

A successful Liquibase run is not enough. The entity mappings, repositories, and tests must also line up.

## Running And Inspecting Liquibase

From the backend directory:

```bash
./mvnw liquibase:update
./mvnw liquibase:updateSQL
```

Use `updateSQL` when you want to inspect generated SQL before applying changes.

## Source Files To Study

| File | Why it matters |
| --- | --- |
| [db.changelog-master.yaml](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/resources/db/changelog/db.changelog-master.yaml) | authoritative migration order |
| [011-add-user-token-version.yaml](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/resources/db/changelog/changes/011-add-user-token-version.yaml) | auth-linked schema evolution |
| [031-create-societe.yaml](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/resources/db/changelog/changes/031-create-societe.yaml) | current société model foundation |
| [033-rename-tenant-id-to-societe-id.yaml](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/resources/db/changelog/changes/033-rename-tenant-id-to-societe-id.yaml) | terminology migration |
| [050-enable-rls-phase1.yaml](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/resources/db/changelog/changes/050-enable-rls-phase1.yaml) | staged security rollout |

## Exercises

### Exercise 1 — Read the timeline

1. Open [db.changelog-master.yaml](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/resources/db/changelog/db.changelog-master.yaml).
2. Identify where the société migration begins.
3. Identify where RLS rollout begins.
4. Explain why older `tenant` filenames still exist.

### Exercise 2 — Design a safe new migration

1. Create a hypothetical changeset `060-add-project-labels.yaml`.
2. Decide the table, indexes, and constraints.
3. Add it to the end of the master changelog.
4. Explain how you would verify the corresponding JPA mapping.

### Exercise 3 — Explain why you should not edit `002-seed-tenant-owner.yaml`

Answer in terms of:

- Liquibase checksum validation
- deployment safety
- repository history
