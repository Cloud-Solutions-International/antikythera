package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct unit tests for {@link ExceptionStateTracker}, the exception and loop
 * state tracking extracted out of {@link Evaluator}.
 */
class ExceptionStateTrackerTest {

    @BeforeEach
    @AfterEach
    void clear() {
        ExceptionStateTracker.clear();
        while (ExceptionStateTracker.currentLoop() != null) {
            ExceptionStateTracker.exitLoop();
        }
    }

    @Test
    void recordCapturesTheException() {
        IOException e = new IOException("boom");
        ExceptionStateTracker.recordException(e);

        assertSame(e, ExceptionStateTracker.getLastException());
        assertSame(e, ExceptionStateTracker.getLastExceptionContext().getException());
        assertFalse(ExceptionStateTracker.getLastExceptionContext().isInsideLoop());
        assertNull(ExceptionStateTracker.getLastExceptionContext().getLoopContext());
    }

    @Test
    void recordNullClearsTheException() {
        ExceptionStateTracker.recordException(new IOException("boom"));
        ExceptionStateTracker.recordException(null);

        assertNull(ExceptionStateTracker.getLastException());
        assertNull(ExceptionStateTracker.getLastExceptionContext());
    }

    @Test
    void clearRemovesTheRecordedException() {
        ExceptionStateTracker.recordException(new IOException("boom"));
        ExceptionStateTracker.clear();

        assertNull(ExceptionStateTracker.getLastException());
        assertNull(ExceptionStateTracker.getLastExceptionContext());
    }

    @Test
    void getLastExceptionIsNullWithoutAnyRecord() {
        assertNull(ExceptionStateTracker.getLastException());
        assertNull(ExceptionStateTracker.getLastExceptionContext());
    }

    @Test
    void recordInsideLoopCapturesTheInnermostLoop() {
        LoopContext outer = new LoopContext();
        LoopContext inner = new LoopContext();
        inner.setIterationWhenThrown(3);

        ExceptionStateTracker.enterLoop(outer);
        ExceptionStateTracker.enterLoop(inner);
        ExceptionStateTracker.recordException(new IllegalStateException("in loop"));

        ExceptionContext ctx = ExceptionStateTracker.getLastExceptionContext();
        assertTrue(ctx.isInsideLoop());
        assertSame(inner, ctx.getLoopContext());
    }

    @Test
    void exitLoopRestoresTheEnclosingLoop() {
        LoopContext outer = new LoopContext();
        LoopContext inner = new LoopContext();

        ExceptionStateTracker.enterLoop(outer);
        ExceptionStateTracker.enterLoop(inner);
        assertSame(inner, ExceptionStateTracker.currentLoop());

        ExceptionStateTracker.exitLoop();
        assertSame(outer, ExceptionStateTracker.currentLoop());

        ExceptionStateTracker.exitLoop();
        assertNull(ExceptionStateTracker.currentLoop());
    }

    @Test
    void recordAfterLeavingLoopIsNotMarkedAsInsideLoop() {
        ExceptionStateTracker.enterLoop(new LoopContext());
        ExceptionStateTracker.exitLoop();
        ExceptionStateTracker.recordException(new IllegalStateException("outside"));

        assertFalse(ExceptionStateTracker.getLastExceptionContext().isInsideLoop());
    }

    @Test
    void matchesBinaryTypeBySubclassing() {
        assertTrue(ExceptionStateTracker.matches(new TypeWrapper(RuntimeException.class),
                new IllegalArgumentException()));
        assertFalse(ExceptionStateTracker.matches(new TypeWrapper(IllegalStateException.class),
                new IllegalArgumentException()));
    }

    @Test
    void matchesSourceTypeByFullyQualifiedName() {
        ClassOrInterfaceDeclaration decl = StaticJavaParser
                .parse("package a.b; public class MyException extends RuntimeException {}")
                .getClassByName("MyException").orElseThrow();

        assertFalse(ExceptionStateTracker.matches(new TypeWrapper(decl), new IllegalArgumentException()));
    }
}
