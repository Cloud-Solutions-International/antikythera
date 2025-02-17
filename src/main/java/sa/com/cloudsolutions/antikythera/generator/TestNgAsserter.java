package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.BlockStmt;

public class TestNgAsserter extends  Asserter {
    @Override
    public void assertNotNull(BlockStmt body, String variable) {
        body.addStatement("assertNotNull(" + variable + ");");
    }

    @Override
    public void setupImports(CompilationUnit gen) {
        gen.addImport("org.testng.annotations.Test");
        gen.addImport("org.testng.Assert.assertNotNull", true, false);
    }
}
