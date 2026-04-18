package sa.com.cloudsolutions.antikythera.evaluator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Lightweight in-memory trace of branch exploration decisions.
 *
 * <p>This is intended for tests and diagnostics while improving branch-combination coverage.
 * It is deliberately process-local and cleared explicitly by tests.</p>
 */
public final class BranchingTrace {
    private static final List<String> EVENTS = new ArrayList<>();
    private static boolean enabled = Boolean.getBoolean("antikythera.branching.trace");

    private BranchingTrace() {
    }

    public static synchronized void clear() {
        EVENTS.clear();
    }

    public static synchronized void enable() {
        enabled = true;
    }

    public static synchronized void disable() {
        enabled = false;
        EVENTS.clear();
    }

    public static synchronized boolean isEnabled() {
        return enabled;
    }

    public static synchronized void record(String event) {
        if (!enabled) {
            return;
        }
        EVENTS.add(event);
    }

    public static synchronized void record(Supplier<String> eventSupplier) {
        if (!enabled) {
            return;
        }
        EVENTS.add(eventSupplier.get());
    }

    public static synchronized List<String> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(EVENTS));
    }
}
