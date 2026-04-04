package sa.com.cloudsolutions.antikythera.evaluator.mock;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.evaluator.GeneratorState;

import static org.junit.jupiter.api.Assertions.*;

class MockingRegistryStubReturnTest {

    @AfterEach
    void tearDown() {
        GeneratorState.clearMockStubReturnHints();
        GeneratorState.clearPendingObjectStubReturnFqns();
    }

    @Test
    void expressionFactoryUsesLiteralForJavaLangInteger() {
        Expression expr = MockingRegistry.expressionFactory("java.lang.Integer");
        assertInstanceOf(IntegerLiteralExpr.class, expr);
        assertEquals("0", ((IntegerLiteralExpr) expr).getValue());
    }

    @Test
    void resolveReturnTypeForStubUsesCastHintForObject() {
        GeneratorState.putMockStubReturnHint("commonFilterRepository", "customizeFilter", "java.util.ArrayList");
        assertEquals("java.util.ArrayList",
                MockingRegistry.resolveReturnTypeForStub("java.lang.Object", "customizeFilter", "commonFilterRepository"));
    }

    @Test
    void resolveReturnTypeForStubUsesPendingStackForObject() {
        GeneratorState.pushPendingObjectStubReturnFqn("com.example.FooDto");
        assertEquals("com.example.FooDto",
                MockingRegistry.resolveReturnTypeForStub("java.lang.Object", "anyMethod", "anyMock"));
        GeneratorState.popPendingObjectStubReturnFqn();
    }

    @Test
    void expressionFactoryArrayListForListFqn() {
        Expression expr = MockingRegistry.expressionFactory("java.util.ArrayList");
        assertInstanceOf(ObjectCreationExpr.class, expr);
    }

    @Test
    void buildMockitoWhenEmitsThenReturnWithoutMockingInteger() {
        GeneratorState.clearWhenThen();
        MockingRegistry.buildMockitoWhen("markAllByPomrIdAsDeleted", "java.lang.Integer", "repo");
        assertEquals(1, GeneratorState.getWhenThen().size());
        String stub = GeneratorState.getWhenThen().getFirst().toString();
        assertFalse(stub.contains("mock(Integer.class)"), stub);
        assertTrue(stub.contains("thenReturn(0)") || stub.contains("thenReturn(0 )"), stub);
    }
}
