package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.expr.Expression;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestScopeChain extends TestHelper {

    @ParameterizedTest
    @CsvSource({
        "obj.method1().method2().method3(), false, method call chain",
        "obj.field1.field2.field3, false, field access chain",
        "obj.method1().field1.method2().field2, false, mixed chain"
    })
    void testScopeChain(String code, boolean shouldBeEmpty, String description) {
        Expression expr = StaticJavaParser.parseExpression(code);

        ScopeChain scopeChain = ScopeChain.findScopeChain(expr);

        if (shouldBeEmpty) {
            assertTrue(scopeChain.isEmpty(), "ScopeChain should be empty for a single object reference.");
        } else {
            assertFalse(scopeChain.isEmpty(), "ScopeChain should not be empty for a " + description + ".");
            assertEquals("obj", scopeChain.getChain().getLast().getExpression().toString(), "The last scope in the chain should be 'obj'.");
        }
    }

    @Test
    void testNoChain() {
        String code = "obj";
        Expression expr = StaticJavaParser.parseExpression(code);

        ScopeChain scopeChain = ScopeChain.findScopeChain(expr);

        assertTrue(scopeChain.isEmpty(), "ScopeChain should be empty for a single object reference.");
    }
}
