package sa.com.cloudsolutions.antikythera.exception;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.expr.Expression;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionTest {

    // --- AntikytheraException ---

    @Test
    void antikytheraExceptionWithMessage() {
        AntikytheraException ex = new AntikytheraException("test error");
        assertEquals("test error", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void antikytheraExceptionWithMessageAndCause() {
        Throwable cause = new IllegalStateException("root cause");
        AntikytheraException ex = new AntikytheraException("wrapper", cause);
        assertEquals("wrapper", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void antikytheraExceptionWithCause() {
        Throwable cause = new RuntimeException("boom");
        AntikytheraException ex = new AntikytheraException(cause);
        assertSame(cause, ex.getCause());
    }

    @Test
    void antikytheraExceptionIsRuntimeException() {
        AntikytheraException ex = new AntikytheraException("test");
        assertInstanceOf(RuntimeException.class, ex);
    }

    // --- EvaluatorException ---

    @Test
    void evaluatorExceptionWithMessage() {
        EvaluatorException ex = new EvaluatorException("eval error");
        assertEquals("eval error", ex.getMessage());
        assertEquals(0, ex.getError());
    }

    @Test
    void evaluatorExceptionWithExpressions() {
        Expression left = StaticJavaParser.parseExpression("a");
        Expression right = StaticJavaParser.parseExpression("b");
        EvaluatorException ex = new EvaluatorException(left, right);
        assertTrue(ex.getMessage().contains("binary operation"));
        assertTrue(ex.getMessage().contains("a"));
        assertTrue(ex.getMessage().contains("b"));
    }

    @Test
    void evaluatorExceptionWithErrorCode() {
        EvaluatorException ex = new EvaluatorException("npe", EvaluatorException.NPE);
        assertEquals(EvaluatorException.NPE, ex.getError());
        assertEquals("npe", ex.getMessage());
    }

    @Test
    void evaluatorExceptionSetError() {
        EvaluatorException ex = new EvaluatorException("test");
        ex.setError(EvaluatorException.INTERNAL_SERVER_ERROR);
        assertEquals(EvaluatorException.INTERNAL_SERVER_ERROR, ex.getError());
    }

    @Test
    void evaluatorExceptionWithCause() {
        Throwable cause = new NullPointerException();
        EvaluatorException ex = new EvaluatorException("wrapped", cause);
        assertEquals("wrapped", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void evaluatorExceptionExtendsAntikytheraException() {
        EvaluatorException ex = new EvaluatorException("test");
        assertInstanceOf(AntikytheraException.class, ex);
    }

    // --- AUTException ---

    @Test
    void autExceptionWithMessage() {
        AUTException ex = new AUTException("aut error");
        assertEquals("aut error", ex.getMessage());
        assertNull(ex.getVariable());
    }

    @Test
    void autExceptionWithMessageAndCause() {
        Throwable cause = new IllegalArgumentException("bad arg");
        AUTException ex = new AUTException("wrapper", cause);
        assertEquals("wrapper", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void autExceptionWithCause() {
        Throwable cause = new RuntimeException("boom");
        AUTException ex = new AUTException(cause);
        assertSame(cause, ex.getCause());
    }

    @Test
    void autExceptionWithVariable() {
        Variable v = new Variable("hello");
        AUTException ex = new AUTException("with var", v);
        assertEquals("with var", ex.getMessage());
        assertSame(v, ex.getVariable());
    }

    @Test
    void autExceptionSetVariable() {
        AUTException ex = new AUTException("test");
        assertNull(ex.getVariable());
        Variable v = new Variable(42);
        ex.setVariable(v);
        assertSame(v, ex.getVariable());
    }

    @Test
    void autExceptionExtendsAntikytheraException() {
        AUTException ex = new AUTException("test");
        assertInstanceOf(AntikytheraException.class, ex);
    }

    // --- DepsolverException ---

    @Test
    void depsolverExceptionWithMessage() {
        DepsolverException ex = new DepsolverException("dep error");
        assertEquals("dep error", ex.getMessage());
    }

    @Test
    void depsolverExceptionWithThrowable() {
        Throwable cause = new RuntimeException("root");
        DepsolverException ex = new DepsolverException(cause);
        assertSame(cause, ex.getCause());
    }

    @Test
    void depsolverExceptionWithAntikytheraException() {
        AntikytheraException cause = new AntikytheraException("ak error");
        DepsolverException ex = new DepsolverException(cause);
        assertSame(cause, ex.getCause());
    }

    @Test
    void depsolverExceptionIsRuntimeException() {
        DepsolverException ex = new DepsolverException("test");
        assertInstanceOf(RuntimeException.class, ex);
    }

    // --- GeneratorException ---

    @Test
    void generatorExceptionWithMessage() {
        GeneratorException ex = new GeneratorException("gen error");
        assertEquals("gen error", ex.getMessage());
    }

    @Test
    void generatorExceptionWithCause() {
        Throwable cause = new IOException();
        GeneratorException ex = new GeneratorException(cause);
        assertSame(cause, ex.getCause());
    }

    @Test
    void generatorExceptionWithMessageAndCause() {
        Throwable cause = new IllegalStateException("state");
        GeneratorException ex = new GeneratorException("gen wrapper", cause);
        assertEquals("gen wrapper", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void generatorExceptionIsRuntimeException() {
        GeneratorException ex = new GeneratorException("test");
        assertInstanceOf(RuntimeException.class, ex);
    }

    // IOException is not imported above; use a nested class to keep the test self-contained.
    private static class IOException extends Exception {}
}
