package sa.com.cloudsolutions.antikythera.evaluator.mock;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
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
        GeneratorState.clearWhenThen();
        GeneratorState.clearImports();
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

    @Test
    void addMockitoExpressionUsesDeclaredMethodReturnTypeNotRuntimeValueClass() {
        CompilationUnit cu = StaticJavaParser.parse("""
                package test;
                import java.util.ArrayList;
                interface ProblemClient {
                    ArrayList getProblem(String hospitalId, String groupId, String userId, Long id);
                }
                """);
        var method = cu.findFirst(com.github.javaparser.ast.body.MethodDeclaration.class,
                md -> md.getNameAsString().equals("getProblem")).orElseThrow();

        MockingRegistry.addMockitoExpression(method, new Object(), "problemFeignClient");

        assertEquals(1, GeneratorState.getWhenThen().size());
        String stub = GeneratorState.getWhenThen().getFirst().toString();
        assertTrue(stub.contains("thenReturn(new ArrayList<>())"), stub);
        assertFalse(stub.contains("Object.class"), stub);
    }

    @Test
    void addMockitoExpressionAddsImportForDeclaredReferenceReturnType() {
        CompilationUnit cu = StaticJavaParser.parse("""
                package test;
                interface ProblemClient {
                    com.example.ProblemMaster getProblem(String hospitalId, String groupId, String userId, Long id);
                }
                """);
        var method = cu.findFirst(com.github.javaparser.ast.body.MethodDeclaration.class,
                md -> md.getNameAsString().equals("getProblem")).orElseThrow();

        MockingRegistry.addMockitoExpression(method, new Object(), "problemFeignClient");

        assertEquals(1, GeneratorState.getWhenThen().size());
        String stub = GeneratorState.getWhenThen().getFirst().toString();
        assertTrue(stub.contains("thenReturn(Mockito.mock(ProblemMaster.class))"), stub);
        assertTrue(GeneratorState.getImports().stream()
                .anyMatch(i -> i.getNameAsString().equals("com.example.ProblemMaster")));
    }
}
