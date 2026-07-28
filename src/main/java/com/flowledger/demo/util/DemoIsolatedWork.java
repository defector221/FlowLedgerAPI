package com.flowledger.demo.util;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs optional demo steps in an isolated transaction so failures do not mark the caller's
 * transaction rollback-only (the classic Spring "silently rolled back" trap).
 *
 * <p>Callers must catch exceptions from these methods — do not catch around REQUIRED services
 * inside the same transaction.
 */
@Component
public class DemoIsolatedWork {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void run(Runnable action) {
        action.run();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> T call(Supplier<T> action) {
        return action.get();
    }
}
