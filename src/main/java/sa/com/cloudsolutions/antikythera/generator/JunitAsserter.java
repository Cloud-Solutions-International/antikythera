package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;

public class JunitAsserter extends Asserter {
    @Override
    public Expression assertNotNull(String variable) {
        MethodCallExpr aNotNull = new MethodCallExpr("assertNotNull");
        aNotNull.addArgument(variable);
        return aNotNull;
    }

    @Override
    public Expression assertNull(String variable) {
        MethodCallExpr aNotNull = new MethodCallExpr("assertNull");
        aNotNull.addArgument(variable);
        return aNotNull;
    }

    @Override
    public void setupImports(CompilationUnit gen) {
        gen.addImport("org.junit.jupiter.api.Test");
        gen.addImport("org.junit.jupiter.api.Assertions", true, true);
    }

    @Override
    public Expression assertEquals(String lhs, String rhs) {
        MethodCallExpr assertEquals = new MethodCallExpr( "assertEquals");
        assertEquals.addArgument(lhs);
        assertEquals.addArgument(rhs);
        return assertEquals;
    }

    @Override
    public Expression assertThrows(String invocation, MethodResponse response) {
        MethodCallExpr assertThrows = new MethodCallExpr("assertThrows");
        Throwable ex = response.getException();
        String exceptionClass;
        if (ex == null) {
            exceptionClass = RuntimeException.class.getName();
        } else if (ex.getCause() != null) {
            exceptionClass = ex.getCause().getClass().getName();
        } else {
            exceptionClass = ex.getClass().getName();
        }
        assertThrows.addArgument(exceptionClass + ".class");
        assertThrows.addArgument(String.format("() -> %s", invocation.replace(';', ' ')));
        return assertThrows;
    }

    @Override
    public Expression assertDoesNotThrow(String invocation) {
        MethodCallExpr assertDoesNotThrow = new MethodCallExpr("assertDoesNotThrow");
        assertDoesNotThrow.addArgument(String.format("() -> %s", invocation.replace(';', ' ')));
        return assertDoesNotThrow;
    }

}
