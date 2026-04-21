# Module 01: Multi-Societe Architecture

## Why This Matters

Multi-societe isolation is the most important architectural rule in the platform.
If it fails, the product fails.

## Learning Goals

- understand the shared-schema design
- identify where societe scope is enforced
- explain why RLS is defense in depth rather than the only control

## Core Concepts

- one PostgreSQL schema
- `societe_id` on tenant-scoped aggregates
- `app_user_societe` for membership
- request-time `SocieteContext`
- transaction-time `app.current_societe_id`

## Files To Study

- [../../hlm-backend/src/main/java/com/yem/hlm/backend/societe/SocieteContext.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/societe/SocieteContext.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/societe/SocieteContextHelper.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/societe/SocieteContextHelper.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/societe/RlsContextAspect.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/societe/RlsContextAspect.java)
- [../context/ARCHITECTURE.md](../context/ARCHITECTURE.md)

## Walk Through The Flow

1. a user authenticates into one societe
2. the filter resolves the active societe ID from the session
3. services work inside that current scope
4. the RLS aspect pushes the same scope into PostgreSQL
5. repository queries and RLS both protect the data path

## Things To Notice

- platform `SUPER_ADMIN` is different from societe-scoped business users
- async work must propagate context explicitly
- schedulers need deliberate system-mode handling when crossing societe boundaries

## Exercise

Choose one entity such as `contact` or `property` and list every place where its societe scope is enforced.
