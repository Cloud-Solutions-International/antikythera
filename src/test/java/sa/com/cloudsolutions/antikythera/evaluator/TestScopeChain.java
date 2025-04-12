package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.expr.Expression;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestScopeChain extends TestHelper {

    @Test
    void testSimpleMethodCallChain() {
        String code = "obj.method1().method2().method3()";
        Expression expr = StaticJavaParser.parseExpression(code);

        ScopeChain scopeChain = ScopeChain.findScopeChain(expr);

        assertFalse(scopeChain.isEmpty(), "ScopeChain should not be empty for a method call chain.");
        assertTrue(scopeChain.pollLast().getExpression().toString().equals("obj"), 
                   "The last scope in the chain should be 'obj'.");
    }

    @Test
    void testFieldAccessChain() {
        String code = "obj.field1.field2.field3";
        Expression expr = StaticJavaParser.parseExpression(code);

        ScopeChain scopeChain = ScopeChain.findScopeChain(expr);

        assertFalse(scopeChain.isEmpty(), "ScopeChain should not be empty for a field access chain.");
        assertTrue(scopeChain.pollLast().getExpression().toString().equals("obj"), 
                   "The last scope in the chain should be 'obj'.");
    }

    @Test
    void testMixedChain() {
        String code = "obj.method1().field1.method2().field2";
        Expression expr = StaticJavaParser.parseExpression(code);

        ScopeChain scopeChain = ScopeChain.findScopeChain(expr);

        assertFalse(scopeChain.isEmpty(), "ScopeChain should not be empty for a mixed chain.");
        assertTrue(scopeChain.pollLast().getExpression().toString().equals("obj"), 
                   "The last scope in the chain should be 'obj'.");
    }

    @Test
    void testNoChain() {
        String code = "obj";
        Expression expr = StaticJavaParser.parseExpression(code);

        ScopeChain scopeChain = ScopeChain.findScopeChain(expr);

        assertTrue(scopeChain.isEmpty(), "ScopeChain should be empty for a single object reference.");
    }
}
