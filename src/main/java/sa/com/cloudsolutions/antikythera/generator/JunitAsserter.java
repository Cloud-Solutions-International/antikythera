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
    public void setupImports(CompilationUnit gen) {
        gen.addImport("org.junit.jupiter.api.Test");
        gen.addImport("org.junit.jupiter.api.Assertions.assertNotNull", true, false);
        gen.addImport("org.junit.jupiter.api.Assertions.assertThrows", true, false);
        gen.addImport("org.junit.jupiter.api.Assertions.assertEquals", true, false);
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
        assertThrows.addArgument(response.getException().getCause().getClass().getName() + ".class");
        assertThrows.addArgument(String.format("() -> { %s }", invocation));
        return assertThrows;
    }
}

