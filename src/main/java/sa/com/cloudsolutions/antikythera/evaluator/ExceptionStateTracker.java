package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.TypeDeclaration;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;

import java.util.Deque;
import java.util.LinkedList;

/**
 * Tracks the exception state of symbolic execution: the most recently raised exception
 * together with its {@link ExceptionContext}, and the stack of loops that are currently
 * being executed so that an exception can be attributed to the iteration it occurred in.
 *
 * <p>Extracted from {@link Evaluator} to reduce the size and responsibility scope of the
 * god-class evaluator. Like the evaluator itself, the recorded exception is static state;
 * the loop stack is thread local.</p>
 */
class ExceptionStateTracker {
    /**
     * Thread-local stack of active loop contexts for exception tracking.
     */
    private static final ThreadLocal<Deque<LoopContext>> activeLoops =
            ThreadLocal.withInitial(LinkedList::new);

    private static ExceptionContext lastExceptionContext;

    private ExceptionStateTracker() {}

    /**
     * Marks the given loop as being executed. Callers must {@link #exitLoop()} in a finally block.
     *
     * @param loopContext the loop that is about to be executed
     */
    static void enterLoop(LoopContext loopContext) {
        activeLoops.get().push(loopContext);
    }

    static void exitLoop() {
        activeLoops.get().pop();
    }

    /**
     * @return the innermost loop being executed, or {@code null} when not inside a loop
     */
    static LoopContext currentLoop() {
        return activeLoops.get().peek();
    }

    /**
     * Records the exception as the most recent one, capturing the enclosing loop if any.
     *
     * @param e the exception that was raised, or {@code null} to clear the recorded exception
     */
    static void record(Exception e) {
        if (e == null) {
            lastExceptionContext = null;
            return;
        }

        ExceptionContext ctx = ExceptionContext.of(e);
        LoopContext loop = currentLoop();
        if (loop != null) {
            ctx.setInsideLoop(true);
            ctx.setLoopContext(loop);
        }

        lastExceptionContext = ctx;
    }

    static ExceptionContext getLastExceptionContext() {
        return lastExceptionContext;
    }

    static Exception getLastException() {
        return lastExceptionContext != null ? lastExceptionContext.getException() : null;
    }

    static void clear() {
        lastExceptionContext = null;
    }

    /**
     * Decides whether the exception would be caught by a catch clause declaring the given type.
     *
     * @param wrapper the type declared by the catch clause
     * @param e       the exception that was raised
     * @return true if the exception matches the declared type
     */
    static boolean matches(TypeWrapper wrapper, Exception e) {
        TypeDeclaration<?> decl = wrapper.getType();
        if (decl != null) {
            return e.getClass().getName().equals(decl.getFullyQualifiedName().orElse(null));
        }
        return wrapper.getClazz().isAssignableFrom(e.getClass());
    }
}
