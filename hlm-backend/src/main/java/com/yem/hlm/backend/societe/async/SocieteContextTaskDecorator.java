package com.yem.hlm.backend.societe.async;

import com.yem.hlm.backend.societe.SocieteContext;
import org.springframework.core.task.TaskDecorator;
import java.util.UUID;

/**
 * Spring {@link TaskDecorator} that propagates the current société context from the
 * submitting thread into the asynchronous worker thread.
 *
 * <h3>Problem</h3>
 * <p>{@link SocieteContext} uses {@link ThreadLocal} variables. When a service method
 * submits work via {@code @Async} (or any Spring-managed thread pool), the worker
 * thread inherits none of the calling thread's ThreadLocals. Any call to
 * {@code SocieteContextHelper.requireSocieteId()} in the async method will throw
 * {@code CrossSocieteAccessException} with "Missing société context".
 *
 * <h3>Solution</h3>
 * <p>This decorator captures the five ThreadLocal values at submit time and sets them
 * on the worker thread before the task runs, then clears them in a {@code finally}
 * block to prevent ThreadLocal leaks in pooled threads.
 *
 * <h3>Registration</h3>
 * <p>Registered in {@link com.yem.hlm.backend.auth.config.AsyncConfig} via
 * {@code ThreadPoolTaskExecutor.setTaskDecorator(new SocieteContextTaskDecorator())}.
 * All beans annotated with {@code @Async} automatically use this executor.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Calling thread: SocieteContext is set by JwtAuthenticationFilter
 * @Async
 * public void sendWelcomeEmail(UUID contactId) {
 *     UUID sid = contextHelper.requireSocieteId(); // works correctly
 *     ...
 * }
 * }</pre>
 */
public class SocieteContextTaskDecorator implements TaskDecorator {

    @Override
    @SuppressWarnings("NullableProblems") // lambda is never null
    public Runnable decorate(@SuppressWarnings("NullableProblems") Runnable task) {
        // Capture all five ThreadLocal values on the SUBMITTING thread
        UUID societeId      = SocieteContext.getSocieteId();
        UUID userId         = SocieteContext.getUserId();
        String role         = SocieteContext.getRole();
        boolean superAdmin  = SocieteContext.isSuperAdmin();
        UUID impersonatedBy = SocieteContext.getImpersonatedBy();

        return () -> {
            try {
                // Restore context on the WORKER thread
                if (societeId != null)      SocieteContext.setSocieteId(societeId);
                if (userId != null)         SocieteContext.setUserId(userId);
                if (role != null)           SocieteContext.setRole(role);
                if (superAdmin)             SocieteContext.setSuperAdmin(true);
                if (impersonatedBy != null) SocieteContext.setImpersonatedBy(impersonatedBy);

                task.run();
            } finally {
                // Always clear to prevent ThreadLocal leaks in pooled threads
                SocieteContext.clear();
            }
        };
    }
}
