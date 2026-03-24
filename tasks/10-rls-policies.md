# Task 10 — PostgreSQL Row-Level Security (Defense-in-Depth)

## Priority: 🟠 MEDIUM
## Risk: Application-only isolation can be bypassed by missing query filters
## Effort: 2 hours
## Depends on: Independent (can be done anytime)

## Problem

All isolation is enforced at the application layer. If a new repository method omits `societe_id` filtering, data leaks across sociétés. PostgreSQL Row-Level Security (RLS) acts as a safety net at the database level.

## Approach

1. Create a PostgreSQL session variable `app.current_societe_id` 
2. Set it at the start of every Hibernate connection via a `ConnectionCustomizer`
3. Enable RLS on critical tables with a policy matching `societe_id = current_setting('app.current_societe_id')::uuid`
4. The `hlm_user` DB role gets the RLS policies; a separate `hlm_admin` role bypasses them for migrations

## Phased Rollout

**Phase 1 (this task):** Enable RLS on `contact` and `property` tables only.  
**Phase 2 (future):** Extend to all remaining tables.

## Files to Create

### 1. NEW: `hlm-backend/src/main/java/com/yem/hlm/backend/societe/SocieteConnectionInterceptor.java`

A Hibernate `StatementInspector` or a `DataSource` wrapper that sets the session variable on each connection:

```java
package com.yem.hlm.backend.societe;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Sets the PostgreSQL session variable 'app.current_societe_id' before each SQL statement.
 * Used by RLS policies as a defense-in-depth layer for multi-société data isolation.
 *
 * When SocieteContext has no societeId (system mode / SUPER_ADMIN), the variable is set
 * to a nil UUID (00000000-0000-0000-0000-000000000000) which matches NO rows —
 * schedulers must bypass RLS by running with the hlm_admin role or by using
 * SocieteContext.setSystem() which sets superAdmin=true.
 */
@Component
public class SocieteConnectionInterceptor implements StatementInspector {

    private static final String NIL_UUID = "00000000-0000-0000-0000-000000000000";

    @Override
    public String inspect(String sql) {
        // Only SET on the first statement per transaction; or use a connection hook instead.
        // Note: StatementInspector is called for EVERY statement — this is a simple but
        // potentially high-overhead approach. A better alternative is a Hikari connection
        // customizer. See alternative below.
        return sql;
    }
}
```

**Better approach — Hikari connection customizer:**

```java
package com.yem.hlm.backend.societe;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

@Configuration
public class RlsDataSourceConfig {

    @Bean
    public DataSource dataSource(DataSourceProperties properties) {
        HikariDataSource ds = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();

        // Set the session variable on each connection checkout
        ds.setConnectionInitSql(null); // Don't use static init
        
        // Use a custom wrapper or AspectJ to set before each transaction
        return new RlsAwareDataSource(ds);
    }
}
```

**Simplest approach — use a Spring `TransactionSynchronization`** or a `@Before` aspect on `@Transactional` methods:

```java
package com.yem.hlm.backend.societe;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
public class RlsContextAspect {

    private final JdbcTemplate jdbc;

    public RlsContextAspect(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Before("@annotation(org.springframework.transaction.annotation.Transactional)")
    public void setSocieteIdOnConnection() {
        UUID societeId = SocieteContext.getSocieteId();
        if (societeId != null) {
            jdbc.execute("SET LOCAL app.current_societe_id = '" + societeId + "'");
        } else if (SocieteContext.isSuperAdmin()) {
            // System mode: set to nil UUID — RLS permits no rows (safe default).
            // Schedulers that need cross-société access should query with explicit
            // societeId parameters, not rely on session variable.
            jdbc.execute("SET LOCAL app.current_societe_id = '00000000-0000-0000-0000-000000000000'");
        }
    }
}
```

**Note:** `SET LOCAL` is scoped to the current transaction — it auto-resets when the transaction ends.

### 2. Liquibase Migration: `041-enable-rls-phase1.yaml`

```yaml
databaseChangeLog:
  - changeSet:
      id: "041-enable-rls-phase1"
      author: "claude-audit"
      comment: "Enable Row-Level Security on contact and property tables"
      changes:
        - sql:
            sql: |
              -- Enable RLS on contact table
              ALTER TABLE contact ENABLE ROW LEVEL SECURITY;
              ALTER TABLE contact FORCE ROW LEVEL SECURITY;
              
              -- Policy: rows visible only when societe_id matches session var
              CREATE POLICY societe_isolation_contact ON contact
                USING (societe_id = current_setting('app.current_societe_id', true)::uuid)
                WITH CHECK (societe_id = current_setting('app.current_societe_id', true)::uuid);
              
              -- Enable RLS on property table
              ALTER TABLE property ENABLE ROW LEVEL SECURITY;
              ALTER TABLE property FORCE ROW LEVEL SECURITY;
              
              CREATE POLICY societe_isolation_property ON property
                USING (societe_id = current_setting('app.current_societe_id', true)::uuid)
                WITH CHECK (societe_id = current_setting('app.current_societe_id', true)::uuid);
      rollback:
        - sql:
            sql: |
              DROP POLICY IF EXISTS societe_isolation_contact ON contact;
              ALTER TABLE contact DISABLE ROW LEVEL SECURITY;
              DROP POLICY IF EXISTS societe_isolation_property ON property;
              ALTER TABLE property DISABLE ROW LEVEL SECURITY;
```

**Important:** `current_setting('app.current_societe_id', true)` — the `true` parameter means "return NULL if not set" instead of throwing an error. This is critical for Liquibase migrations which don't set the session variable.

**Also important:** RLS does NOT apply to the table owner. If `hlm_user` owns the tables, RLS won't work. You need:
```sql
-- Ensure the application connects as a non-owner role
-- Or use FORCE ROW LEVEL SECURITY (which we do above)
```

## Testing Strategy

1. Write a test that creates a contact in société A, then attempts to read it with a raw JDBC query (bypassing the application layer) while the session variable is set to société B's UUID. Verify the row is not returned.

2. Verify that the existing `CrossSocieteIsolationIT` still passes with RLS enabled.

## Tests to Run

```bash
cd hlm-backend && ./mvnw test -Dtest=CrossSocieteIsolationIT
cd hlm-backend && ./mvnw test
```

## Acceptance Criteria

- [ ] RLS enabled on `contact` and `property` tables
- [ ] Session variable `app.current_societe_id` is set on each transactional method
- [ ] Rows are invisible when session variable doesn't match societe_id
- [ ] Liquibase migrations still run (they don't set the variable, RLS returns NULL = no match = safe)
- [ ] All existing tests pass
- [ ] Schedulers (system mode) still function correctly

## Risks / Caution

- **RLS + Liquibase:** Ensure Liquibase connects as the table owner or a superuser that bypasses RLS
- **RLS + JPA:** Hibernate may cache query results — verify that switching société in the same session (switchSociete) invalidates the L1 cache
- **Performance:** RLS adds a per-row filter. For large tables, ensure `societe_id` has an index (already present)
- **This is a significant infrastructure change.** Test thoroughly in a staging environment before production.
