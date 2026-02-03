package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JunitAsserterTest {
    private JunitAsserter asserter;

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void setUp() {
        asserter = new JunitAsserter();
    }

    @Test
    void assertNotNullGeneratesCorrectExpression() {
        Expression expr = asserter.assertNotNull("testVar");
        assertEquals("assertNotNull(testVar)", expr.toString());
    }

    @Test
    void assertNullGeneratesCorrectExpression() {
        Expression expr = asserter.assertNull("testVar");
        assertEquals("assertNull(testVar)", expr.toString());
    }

    @Test
    void assertEqualsGeneratesCorrectExpression() {
        Expression expr = asserter.assertEquals("expected", "actual");
        assertEquals("assertEquals(expected, actual)", expr.toString());
    }

    @Test
    void setupImportsAddsCorrectImports() {
        CompilationUnit cu = new CompilationUnit();
        asserter.setupImports(cu);

        assertTrue(cu.getImports().stream()
                .anyMatch(i -> i.getNameAsString().equals("org.junit.jupiter.api.Test")));
        assertTrue(cu.getImports().stream()
                .anyMatch(i -> i.getNameAsString().equals("org.junit.jupiter.api.Assertions")));
    }

    @Test
    void assertThrowsGeneratesCorrectExpression() {
        MethodResponse response = new MethodResponse();
        response.setException(new EvaluatorException("Ouch", new IllegalArgumentException()));

        Expression expr = asserter.assertThrows("someMethod()", response);
        assertEquals("assertThrows(java.lang.IllegalArgumentException.class, () -> someMethod())",
                    expr.toString());
    }

    @Test
    void fieldAssertionWithStringValueGeneratesCorrectExpression() {
        Variable v = new Variable("bada");
        v.setValue("test value");

        Expression expr = asserter.fieldAssertion("getName", v);
        assertEquals("assertEquals(\"test value\", resp.getName())", expr.toString());
    }

    @Test
    void fieldAssertionWithCollectionGeneratesCorrectExpression() {
        List<String> list = new ArrayList<>();
        list.add("item1");

        Variable v = new Variable("bada");
        v.setValue(list);
        v.setClazz(ArrayList.class);

        Expression expr = asserter.fieldAssertion("getList", v);
        assertEquals("assertEquals(1, resp.getList().size())", expr.toString());
    }

    @Test
    void fieldAssertionWithNumberValueGeneratesCorrectExpression() {
        Variable v = new Variable("bada");
        v.setValue(42);

        Expression expr = asserter.fieldAssertion("getCount", v);
        assertEquals("assertEquals(42, resp.getCount())", expr.toString());
    }

    @Test
    void assertOutputGeneratesCorrectExpression() {
        Expression expr = asserter.assertOutput("Hello World");
        assertEquals("assertEquals(\"Hello World\", outputStream.toString().trim())", expr.toString());
    }

}
