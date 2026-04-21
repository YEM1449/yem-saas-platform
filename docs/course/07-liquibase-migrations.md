# Module 07: Liquibase Migrations

## Why This Matters

Schema changes are part of the product contract and must remain traceable.

## Learning Goals

- understand how schema changes are applied
- understand why additive changes are preferred
- understand the relationship between migrations and entities

## Current Reality

- the master changelog includes changesets through `069`
- history includes the evolution from older `tenant` naming to `societe`
- JPA validates schema rather than generating it

## Files To Study

- [../../hlm-backend/src/main/resources/db/changelog/db.changelog-master.yaml](../../hlm-backend/src/main/resources/db/changelog/db.changelog-master.yaml)
- [../context/DATA_MODEL.md](../context/DATA_MODEL.md)
- [../guides/engineer/database.md](../guides/engineer/database.md)

## Migration Rules

- use a new changeset for schema changes
- keep migrations explicit and reviewable
- remember indexes, foreign keys, and RLS implications
- update tests and docs with the same change

## Exercise

Open two distant migrations in the history and describe how the data model matured between them.
