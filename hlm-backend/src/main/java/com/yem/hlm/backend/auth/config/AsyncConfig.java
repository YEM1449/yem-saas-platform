package com.yem.hlm.backend.auth.config;

import com.yem.hlm.backend.societe.async.SocieteContextTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async executor configuration.
 *
 * <p>Registers a {@link ThreadPoolTaskExecutor} decorated with
 * {@link SocieteContextTaskDecorator} so that every {@code @Async} method
 * automatically inherits the calling thread's société context
 * ({@code societeId}, {@code userId}, {@code role}, {@code superAdmin}, {@code impersonatedBy}).
 *
 * <h3>Why this matters</h3>
 * <p>{@code SocieteContext} uses {@link ThreadLocal} variables. Without the decorator,
 * async threads start with a blank context and any call to
 * {@code SocieteContextHelper.requireSocieteId()} throws {@code CrossSocieteAccessException}.
 * The decorator captures the context snapshot at submit time and restores it on the worker
 * thread, clearing it in a {@code finally} block to prevent ThreadLocal leaks.
 *
 * <h3>Thread pool sizing</h3>
 * <ul>
 *   <li>Core size 4 / max 16 — tuned for mixed I/O (email, SMS, PDF) workloads.</li>
 *   <li>Queue capacity 100 — bounded to surface back-pressure early.</li>
 *   <li>Graceful shutdown enabled — waits up to 30 s for in-flight tasks.</li>
 * </ul>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("hlm-async-");
        executor.setTaskDecorator(new SocieteContextTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
