package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.Statement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.TestHelper;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolverTest extends TestHelper {

    private GraphNode node;
    private CompilationUnit cu;
    private ClassOrInterfaceDeclaration sourceClass;

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.preProcess();
        DepSolver.reset();
    }

    @BeforeEach
    void each() {
        DepSolver.createSolver();
        DepSolver.reset();

        cu = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.antikythera.evaluator.Person");
        sourceClass = cu.getType(0).asClassOrInterfaceDeclaration();
        node = Graph.createGraphNode(sourceClass); // Use the Graph.createGraphNode method to create GraphNode

    }

    private void init(String className) {
        cu = AntikytheraRunTime.getCompilationUnit(className);
        sourceClass = cu.getType(0).asClassOrInterfaceDeclaration();
        node = Graph.createGraphNode(sourceClass); // Use the Graph.createGraphNode method to create GraphNode
    }

    @Test
    void testAnnotationBinary() {
        init("sa.com.cloudsolutions.antikythera.depsolver.DummyClass");

        AnnotationExpr ann = sourceClass
                .getMethodsByName("binaryAnnotation").getFirst()
                .getAnnotationByName("DummyAnnotation").orElseThrow();
        Resolver.resolveNormalAnnotationExpr(node, ann.asNormalAnnotationExpr());

        CompilationUnit resolved = Graph.getDependencies().get("sa.com.cloudsolutions.antikythera.depsolver.DummyClass");
        assertNotNull(resolved);
        String s = resolved.toString();
        assertFalse(s.contains("DummyAnnotation"),
                "The annotation visitor is not invoked so annotation should not be present");
        assertTrue(s.contains("PREFIX"),"Direct call to resolveNormalAnnotationExpr keeps PRE field");
        assertTrue(s.contains("SUFFIX"),"Direct call to resolveNormalAnnotationExpr keeps PRE field");
    }


    @Test
    void testAnnotationWithField() {
        init("sa.com.cloudsolutions.antikythera.depsolver.DummyClass");

        AnnotationExpr ann = sourceClass
                .getMethodsByName("annotationWIthField").getFirst()
                .getAnnotationByName("DummyAnnotation").orElseThrow();
        Resolver.resolveNormalAnnotationExpr(node, ann.asNormalAnnotationExpr());
        CompilationUnit resolved = Graph.getDependencies().get("sa.com.cloudsolutions.antikythera.depsolver.DummyClass");
        assertNotNull(resolved);
        String s = resolved.toString();
        assertFalse(s.contains("DummyAnnotation"),
                "The annotation visitor is not invoked so annotation should not be present");
        assertTrue(s.contains("PREFIX"),"Direct call to resolveNormalAnnotationExpr keeps PRE field");
        assertFalse(s.contains("SUFFIX"),"Direct call to resolveNormalAnnotationExpr keeps PRE field");
    }

    @Test
    void testThisAccess1() {
        init("sa.com.cloudsolutions.antikythera.evaluator.Employee");
        // create a new FieldAccessExpression with this.
        FieldAccessExpr fieldAccessExpr = new FieldAccessExpr(new NameExpr("this"), "p");
        Resolver.resolveField(node, fieldAccessExpr);

        CompilationUnit resolved = Graph.getDependencies().get("sa.com.cloudsolutions.antikythera.evaluator.Employee");
        assertNotNull(resolved);
        String s = resolved.toString();
        assertTrue(s.contains("Hornblower"));
    }

    @Test
    void testThisAccess2() {
        init("sa.com.cloudsolutions.antikythera.evaluator.Employee");
        // create a new FieldAccessExpression with this.
        FieldAccessExpr fieldAccessExpr = new FieldAccessExpr(new NameExpr("this"), "objectMapper");
        Resolver.resolveField(node, fieldAccessExpr);

        CompilationUnit resolved = Graph.getDependencies().get("sa.com.cloudsolutions.antikythera.evaluator.Employee");
        assertNotNull(resolved);
        String s = resolved.toString();
        assertTrue(s.contains("com.fasterxml.jackson.databind.ObjectMapper"));
        assertTrue(s.contains("objectMapper = new ObjectMapper()"));
    }

    @Test
    void testMethodReference() {
        init("sa.com.cloudsolutions.antikythera.evaluator.Functional");

        Statement stmt = sourceClass.getMethodsByName("people1")
                .getFirst().getBody().orElseThrow().getStatement(0);

        Expression expr = stmt.asExpressionStmt().getExpression()
                .asVariableDeclarationExpr().getVariable(0).getInitializer().orElseThrow();
        Resolver.processExpression(node, expr, new NodeList<>());

        CompilationUnit resolved = Graph.getDependencies().get("sa.com.cloudsolutions.antikythera.evaluator.Person");
        assertNotNull(resolved);
        String s = resolved.toString();
        assertTrue(s.contains("getPerson"));
    }
}
