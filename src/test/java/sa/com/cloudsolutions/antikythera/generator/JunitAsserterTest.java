package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;

import static org.junit.jupiter.api.Assertions.*;

class JunitAsserterTest {
    private JunitAsserter asserter;

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
}
