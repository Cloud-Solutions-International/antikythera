package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TestInheritance extends TestHelper {

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
        MockingRegistry.reset();

    }

    @BeforeEach
    void each() throws AntikytheraException {
        System.setOut(new PrintStream(outContent));
        evaluator = EvaluatorFactory.create("sa.com.cloudsolutions.antikythera.evaluator.PersonExt", Evaluator.class);
    }

    @Test
    void testFields() {
        assertEquals(6, evaluator.fields.size());
        assertEquals(0, evaluator.fields.get("id").getValue());
        assertNull(evaluator.fields.get("name").getValue());
    }

    @Test
    void testGetterSetterAnnotation() throws ReflectiveOperationException {
        evaluator.getCompilationUnit().getType(0).addAnnotation("Getter");
        evaluator.getCompilationUnit().getType(0).addAnnotation("Setter");

        MethodCallExpr mce = new MethodCallExpr("getAge");
        Variable v = evaluator.evaluateExpression(mce);
        assertNotNull(v);
        assertEquals(0, v.getValue());

        MethodCallExpr setter = new MethodCallExpr("setAge").addArgument(new IntegerLiteralExpr("1"));
        evaluator.evaluateExpression(setter);

        v = evaluator.evaluateExpression(mce);
        assertNotNull(v);
        assertEquals(1, v.getValue());
    }
}
