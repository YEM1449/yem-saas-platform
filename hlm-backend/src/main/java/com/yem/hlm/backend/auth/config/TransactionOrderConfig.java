package com.yem.hlm.backend.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Fixes the relative ordering between {@link org.springframework.transaction.interceptor.TransactionInterceptor}
 * and {@link com.yem.hlm.backend.societe.RlsContextAspect} so that the RLS
 * {@code set_config} call runs <em>inside</em> the transaction.
 *
 * <h3>The Problem</h3>
 * <p>Spring Boot auto-configures {@code @EnableTransactionManagement} at
 * {@link Ordered#LOWEST_PRECEDENCE} ({@code Integer.MAX_VALUE}).
 * {@code RlsContextAspect} uses {@code @Order(LOWEST_PRECEDENCE - 1)}.
 * In Spring AOP, a <em>lower</em> order number means <em>higher priority</em> = <em>outer</em> proxy.
 * With {@code MAX_VALUE - 1 < MAX_VALUE}, the RLS aspect was the outer proxy, meaning its
 * {@code @Before} advice fired <em>before</em> the transaction interceptor opened the transaction.
 * That caused {@code JdbcTemplate.queryForObject("SELECT set_config(...)")} to run on a
 * standalone (non-transactional) connection that was immediately returned to the pool.
 * The PostgreSQL session variable was therefore never visible to the connection Hibernate
 * used for the actual query, so every RLS policy saw {@code current_setting(...) = ''}
 * and blocked all rows.
 *
 * <h3>The Fix</h3>
 * <p>By registering {@code @EnableTransactionManagement(order = LOWEST_PRECEDENCE - 10)}, this
 * configuration replaces Spring Boot's auto-configured one (see
 * {@code TransactionAutoConfiguration.EnableTransactionManagementConfiguration} which is
 * {@code @ConditionalOnMissingBean(AbstractTransactionManagementConfiguration.class)}).
 * The transaction interceptor now has a <em>lower</em> order ({@code MAX_VALUE - 10}) than the
 * RLS aspect ({@code MAX_VALUE - 1}), making it the <em>outer</em> proxy.  The execution order
 * for each {@code @Transactional} invocation becomes:
 * <ol>
 *   <li>Transaction opens (connection bound to thread by {@code JpaTransactionManager})</li>
 *   <li>{@code RlsContextAspect.@Before} fires — {@code JdbcTemplate} reuses the bound
 *       connection and calls {@code set_config('app.current_societe_id', ?, true)}</li>
 *   <li>Service/repository method body executes</li>
 *   <li>Transaction commits / rolls back — {@code is_local=true} resets the GUC automatically</li>
 * </ol>
 */
@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement(proxyTargetClass = true, order = Ordered.LOWEST_PRECEDENCE - 10)
public class TransactionOrderConfig {
}
