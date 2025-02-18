package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;

import java.util.Map;

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
