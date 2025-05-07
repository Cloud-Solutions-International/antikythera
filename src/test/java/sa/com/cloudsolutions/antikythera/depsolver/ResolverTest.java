package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
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

public class ResolverTest extends TestHelper {
    private DepSolver depSolver;
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
        depSolver = DepSolver.createSolver();
        DepSolver.reset();

        cu = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.antikythera.evaluator.Person");
        sourceClass = cu.getType(0).asClassOrInterfaceDeclaration();
        node = Graph.createGraphNode(sourceClass); // Use the Graph.createGraphNode method to create GraphNode

    }


    @Test
    void testAnnotationBinary() {
        depSolver = DepSolver.createSolver();
        DepSolver.reset();

        cu = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.antikythera.depsolver.DummyClass");
        sourceClass = cu.getType(0).asClassOrInterfaceDeclaration();
        node = Graph.createGraphNode(sourceClass); // Use the Graph.createGraphNode method to create GraphNode

        AnnotationExpr ann = sourceClass
                .getMethodsByName("binaryAnnotation").getFirst()
                .getAnnotationByName("DummyAnnotation").orElseThrow();
        Resolver.resolveNormalAnnotationExpr(node, ann.asNormalAnnotationExpr());
        CompilationUnit cu = Graph.getDependencies().get("sa.com.cloudsolutions.antikythera.depsolver.DummyClass");
        assertNotNull(cu);
        String s = cu.toString();
        assertFalse(s.contains("DummyAnnotation"),
                "The annotation visitor is not invoked so annotation should not be present");
        assertTrue(s.contains("PREFIX"),"Direct call to resolveNormalAnnotationExpr keeps PRE field");
        assertTrue(s.contains("SUFFIX"),"Direct call to resolveNormalAnnotationExpr keeps PRE field");

    }


    @Test
    void testAnnotationWithField() {
        depSolver = DepSolver.createSolver();
        DepSolver.reset();

        cu = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.antikythera.depsolver.DummyClass");
        sourceClass = cu.getType(0).asClassOrInterfaceDeclaration();
        node = Graph.createGraphNode(sourceClass); // Use the Graph.createGraphNode method to create GraphNode

        AnnotationExpr ann = sourceClass
                .getMethodsByName("annotationWIthField").getFirst()
                .getAnnotationByName("DummyAnnotation").orElseThrow();
        Resolver.resolveNormalAnnotationExpr(node, ann.asNormalAnnotationExpr());
        CompilationUnit cu = Graph.getDependencies().get("sa.com.cloudsolutions.antikythera.depsolver.DummyClass");
        assertNotNull(cu);
        String s = cu.toString();
        assertFalse(s.contains("DummyAnnotation"),
                "The annotation visitor is not invoked so annotation should not be present");
        assertTrue(s.contains("PREFIX"),"Direct call to resolveNormalAnnotationExpr keeps PRE field");
        assertFalse(s.contains("SUFFIX"),"Direct call to resolveNormalAnnotationExpr keeps PRE field");
    }

}
