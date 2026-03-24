# Task 05 — Rename Legacy "Tenant" References to "Société"

## Priority: 🟢 LOW
## Risk: Consistency / developer confusion
## Effort: 1 hour

## Problem

The codebase has been migrated from tenant to société, but legacy references remain in:
- Exception class name: `CrossTenantAccessException`
- Database index names: `idx_*_tenant_*`
- Repository method names: `findByTenantIdAndIdForUpdate`
- Javadoc comments referencing "tenant"

## Files to Modify

### 1. Rename Exception Class

Create new class (if not created in Task 02):
`com.yem.hlm.backend.societe.CrossSocieteAccessException`

Then update all files that import `CrossTenantAccessException` to import `CrossSocieteAccessException` instead.

Find all usages:
```bash
rg "CrossTenantAccessException" hlm-backend/src/main/java --type java -l
```

For each file, update the import and the class reference. Keep `CrossTenantAccessException.java` as a deprecated subclass for backward compatibility:

```java
package com.yem.hlm.backend.contact.service;

/**
 * @deprecated Use {@link com.yem.hlm.backend.societe.CrossSocieteAccessException} instead.
 */
@Deprecated
public class CrossTenantAccessException extends com.yem.hlm.backend.societe.CrossSocieteAccessException {
    public CrossTenantAccessException(String message) {
        super(message);
    }
}
```

### 2. Rename Repository Method

In `PropertyRepository.java`, rename:
- `findByTenantIdAndIdForUpdate` → `findBySocieteIdAndIdForUpdate`

Then update all callers (search for `findByTenantIdAndIdForUpdate`):
```bash
rg "findByTenantIdAndIdForUpdate" hlm-backend/src/main/java --type java -l
```

### 3. Rename Database Indexes (Liquibase)

Create changeset `039-rename-tenant-indexes.yaml`:

```yaml
databaseChangeLog:
  - changeSet:
      id: "039-rename-tenant-indexes"
      author: "claude-audit"
      comment: "Rename legacy 'tenant' index names to 'societe' for consistency"
      changes:
        # Note: PostgreSQL allows renaming indexes with ALTER INDEX ... RENAME TO
        # Only rename indexes that have 'tenant' in the name
        - sql:
            sql: |
              DO $$
              DECLARE
                idx RECORD;
              BEGIN
                FOR idx IN
                  SELECT indexname FROM pg_indexes
                  WHERE schemaname = 'public' AND indexname LIKE '%tenant%'
                LOOP
                  EXECUTE format('ALTER INDEX IF EXISTS %I RENAME TO %I',
                    idx.indexname,
                    replace(idx.indexname, 'tenant', 'societe'));
                END LOOP;
              END $$;
```

Add to `db.changelog-master.yaml`.

### 4. Update Javadoc Comments

Search and replace in comments only:
```bash
rg "tenant" hlm-backend/src/main/java --type java -l | head -20
```

Update references in Javadoc comments from "tenant" to "société" where appropriate. Do NOT change:
- Variable names (these are functional and already use `societeId`)
- Column/table names (handled by Liquibase)
- Test class names (updated separately)

## Tests to Run

```bash
cd hlm-backend && ./mvnw test
```

## Acceptance Criteria

- [ ] `CrossSocieteAccessException` exists and is used everywhere
- [ ] `CrossTenantAccessException` exists as deprecated wrapper
- [ ] `findByTenantIdAndIdForUpdate` renamed to `findBySocieteIdAndIdForUpdate`
- [ ] All callers updated
- [ ] Liquibase changeset 039 created for index renames
- [ ] All tests pass
