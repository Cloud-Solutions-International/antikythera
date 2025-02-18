package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;

public class JunitAsserter extends Asserter {
    @Override
    public void assertNotNull(BlockStmt body, String variable) {
        body.addStatement("assertNotNull(" + variable + ");");
    }

    @Override
    public void setupImports(CompilationUnit gen) {
        gen.addImport("org.junit.jupiter.api.Test");
        gen.addImport("org.junit.jupiter.api.Assertions.assertNotNull", true, false);
    }
}
