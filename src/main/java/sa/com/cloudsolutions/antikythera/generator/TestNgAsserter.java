package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;

public class TestNgAsserter extends  Asserter {


    @Override
    public Expression assertNotNull(String variable) {
        MethodCallExpr aNotNull = new MethodCallExpr(new NameExpr("Assert"), "assertNotNull");
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
        MethodCallExpr assertEquals = new MethodCallExpr(new NameExpr("Assert"), "assertEquals");
        return assertEquals;
    }

    @Override
    public Expression assertThrows(String invocation, MethodResponse response) {
        throw new RuntimeException("Not implemented");
    }
}
