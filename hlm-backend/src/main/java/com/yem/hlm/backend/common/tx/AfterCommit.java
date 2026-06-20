package com.yem.hlm.backend.common.tx;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs a side effect <b>after the surrounding transaction commits</b>, so the work happens once
 * the database connection has been returned to the pool.
 *
 * <p>Used to move external network I/O (Brevo email, R2 object I/O) out of a {@code @Transactional}
 * method (DA-005): calling a remote endpoint while still holding a scarce Neon connection across the
 * round trip is how one mail/storage hiccup cascades into connection-pool starvation. Deferring to
 * {@code afterCommit} releases the connection first; bounded HTTP timeouts on the clients then cap how
 * long the post-commit call itself can block.
 *
 * <p>If no transaction is active (e.g. a scheduled job already outside one), the action runs inline.
 * The action only fires on <b>commit</b> — a rolled-back transaction never triggers the side effect,
 * which is also the correct semantics (don't email a link for a token that was never persisted).
 */
public final class AfterCommit {

    private AfterCommit() {}

    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
