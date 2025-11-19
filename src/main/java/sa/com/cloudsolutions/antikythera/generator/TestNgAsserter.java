package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;

public class TestNgAsserter extends  Asserter {


    public static final String ASSERT = "Assert";

    @Override
    public Expression assertNotNull(String variable) {
        MethodCallExpr aNotNull = new MethodCallExpr(new NameExpr(ASSERT), "assertNotNull");
        aNotNull.addArgument(new NameExpr(variable));
        return aNotNull;
    }


    @Override
    public Expression assertNull(String variable) {
        MethodCallExpr aNotNull = new MethodCallExpr(new NameExpr(ASSERT), "assertNull");
        aNotNull.addArgument(new NameExpr(variable));
        return aNotNull;
    }

    @Override
    public void setupImports(CompilationUnit gen) {
        gen.addImport("org.testng.annotations.Test");
        gen.addImport("org.testng.Assert.assertNotNull", true, false);
    }

    @Override
    public Expression assertEquals(String rhs, String lhs) {
        return new MethodCallExpr(new NameExpr(ASSERT), "assertEquals");
    }

    @Override
    public Expression assertThrows(String invocation, MethodResponse response) {
        throw new IllegalStateException("Not implemented");
    }
}
