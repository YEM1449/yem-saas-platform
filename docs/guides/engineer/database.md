# Database Guide

This guide explains how the database is structured and how to change it safely.

## 1. Current Database Model

- PostgreSQL 16
- shared schema for all societes
- Liquibase-managed schema evolution
- RLS enabled for tenant-scoped protection on key domain tables

## 2. Schema Design Rules

- tenant-scoped tables use `societe_id`
- changes are additive through Liquibase changesets
- JPA validates schema; it does not generate production schema
- mutable aggregates often use optimistic locking with `version`

## 3. Migration Workflow

1. create a new changeset in `hlm-backend/src/main/resources/db/changelog/changes/`
2. include it through the master changelog if your naming process requires it
3. update entities and repositories
4. add or update tests
5. update docs

Current history extends through changeset `069`.

## 4. RLS Expectations

RLS is not optional decoration. It is part of the safety model.

When adding a tenant-scoped table:

- include `societe_id`
- create the right indexes
- ensure queries are societe-scoped in code
- ensure the table is covered by RLS policy strategy

## 5. Indexing Philosophy

Indexes tend to follow:

- `(societe_id, status)`
- `(societe_id, created_at)`
- unique business keys inside a societe
- query-specific indexes for reporting and dashboards

## 6. Typical Aggregates To Study

- `contact`
- `property`
- `vente`
- `sale_contract`
- `payment_schedule_item`
- `outbound_message`

These cover transactional, reporting, compliance, and async use cases.

## 7. Data Evolution Risks

- old migration names still mention `tenant`
- `vente` and `sale_contract` both affect property lifecycle
- compliance fields are spread through business tables, not isolated elsewhere

## 8. Verifying A Database Change

- run backend tests
- run integration tests when behavior or schema interactions changed
- inspect generated SQL or logs when dealing with complex queries
- confirm docs still match actual data semantics

## 9. Practical Warnings

- do not rely on manual schema drift
- do not skip migrations because a local DB “already has the column”
- do not treat RLS as a substitute for scoped repository methods
