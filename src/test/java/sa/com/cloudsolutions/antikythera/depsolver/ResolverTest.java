package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.Type;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResolverTest {
    DepSolver depSolver;

    @BeforeAll
    static void setupClass() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void each() {
        depSolver = DepSolver.createSolver();
        DepSolver.reset();
    }

    @AfterAll
    static void afterAll() {
        AntikytheraRunTime.resetAll();
    }

    @Test
    void lombokSolver1() {
        TypeDeclaration<?> t = AntikytheraRunTime.getTypeDeclaration(
                "sa.com.cloudsolutions.antikythera.evaluator.Tea").orElseThrow();
        GraphNode node = Graph.createGraphNode(t); // Use the Graph.createGraphNode method to create GraphNode
        MethodCallExpr methodCallExpr = t.findFirst(MethodCallExpr.class).orElseThrow();
        Type type = Resolver.lombokSolver(methodCallExpr, t.asClassOrInterfaceDeclaration(), node);
        assertNotNull(type);
    }

    @Test
    void lombokSolver2() {
        TypeDeclaration<?> t = AntikytheraRunTime.getTypeDeclaration(
                "sa.com.cloudsolutions.antikythera.evaluator.Tea").orElseThrow();
        GraphNode node = Graph.createGraphNode(t); // Use the Graph.createGraphNode method to create GraphNode
        MethodCallExpr methodCallExpr = t.findFirst(MethodCallExpr.class).orElseThrow();
        methodCallExpr.setName("getGibberish");
        Type type = Resolver.lombokSolver(methodCallExpr, t.asClassOrInterfaceDeclaration(), node);
        assertNull(type);
    }

    @Test
    void wrapCallable() {
        NodeList<Type> nodes = new NodeList<>();
        TypeDeclaration<?> t = AntikytheraRunTime.getTypeDeclaration(
                "sa.com.cloudsolutions.antikythera.evaluator.Tea").orElseThrow();
        GraphNode node = Graph.createGraphNode(t); // Use the Graph.createGraphNode method to create GraphNode
        MethodCallExpr methodCallExpr = t.findFirst(MethodCallExpr.class).orElseThrow();
        Resolver.wrapCallable(node, methodCallExpr, nodes);
        assertFalse(nodes.isEmpty(), "Expected nodes to be empty, but it was not.");
    }
}
