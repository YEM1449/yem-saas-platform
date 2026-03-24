package com.yem.hlm.backend.common.lock;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.util.Objects;

/**
 * ShedLock configuration — prevents duplicate scheduler execution across multiple
 * backend instances sharing the same PostgreSQL database.
 *
 * <h3>How it works</h3>
 * <p>ShedLock inserts a row into the {@code shedlock} table (created by changeset 052)
 * when a {@code @SchedulerLock}-annotated method starts, and removes / releases the lock
 * when it finishes. If a second instance tries to acquire the same lock while it is held,
 * it skips that execution entirely.
 *
 * <h3>Provider choice</h3>
 * <p>{@link JdbcTemplateLockProvider} reuses the existing PostgreSQL data source — no
 * extra Redis/ZooKeeper dependency needed. The table uses optimistic UPDATE semantics
 * with a {@code locked_at} / {@code lock_until} timestamp pair so that even a crashed
 * node releases the lock after {@code lockAtMostFor} elapses.
 *
 * <h3>Default lock duration</h3>
 * <p>{@code defaultLockAtMostFor = "PT10M"} (ISO-8601 duration) — if a scheduler
 * method hangs indefinitely, the lock is forcibly released after 10 minutes. Individual
 * schedulers may override this via {@code @SchedulerLock(lockAtMostFor = "...")}.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class ShedLockConfig {

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        DataSource ds = Objects.requireNonNull(dataSource, "dataSource must not be null");
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(ds))
                        // Use DB-server time for atomic lock acquisition (avoids clock skew)
                        .usingDbTime()
                        .build()
        );
    }
}
