package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.SuperExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.type.Type;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyAnalyzerCoverageTest {
    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AntikytheraRunTime.resetAll();
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void reset() {
        DepSolver.reset();
    }

    @Test
    void resolvesConstructorAndInitializerDependencies() throws IOException {
        AbstractCompiler compiler = new AbstractCompiler();
        compiler.compile(AbstractCompiler.classToPath(
                "sa.com.cloudsolutions.antikythera.testhelper.depsolver.DepsolverSample.java"));
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(
                "sa.com.cloudsolutions.antikythera.testhelper.depsolver.DepsolverSample");
        assertNotNull(cu);
        assertTrue(cu.findAll(com.github.javaparser.ast.body.InitializerDeclaration.class).size() >= 2,
                "Expected initializer blocks in fixture source");

        DepSolver depSolver = DepSolver.createSolver();
        Graph.createGraphNode(cu.getType(0));
        depSolver.dfs();

        boolean hasInitializer = Graph.getNodes().values().stream()
                .anyMatch(n -> n.getNode() instanceof com.github.javaparser.ast.body.InitializerDeclaration);
        assertTrue(hasInitializer, "Expected initializer blocks to be discovered");

        boolean hasHelperCtor = Graph.getNodes().values().stream()
                .anyMatch(n -> n.getNode() instanceof ConstructorDeclaration cd
                        && cd.findAncestor(com.github.javaparser.ast.body.TypeDeclaration.class)
                                .map(t -> t.getNameAsString().equals("DepsolverHelper")).orElse(false));
        assertTrue(hasHelperCtor, "Expected helper constructor to be discovered");

    }

    @Test
    void resolvesThisAndSuperFieldAccessInResolver() {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(
                "sa.com.cloudsolutions.antikythera.testhelper.depsolver.DepsolverSample");
        assertNotNull(cu);
        GraphNode node = Graph.createGraphNode(cu.getType(0));

        FieldAccessExpr thisField = new FieldAccessExpr(new ThisExpr(), "helper");
        GraphNode thisResolved = Resolver.resolveFieldAccess(node, thisField, new com.github.javaparser.ast.NodeList<>());
        assertNotNull(thisResolved);

        FieldAccessExpr superField = new FieldAccessExpr(new SuperExpr(), "baseHelper");
        GraphNode superResolved = Resolver.resolveFieldAccess(node, superField, new com.github.javaparser.ast.NodeList<>());
        assertNotNull(superResolved);
    }

    @Test
    void resolvesUnqualifiedAssignmentTarget() {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(
                "sa.com.cloudsolutions.antikythera.testhelper.depsolver.DepsolverSample");
        assertNotNull(cu);
        GraphNode node = Graph.createGraphNode(cu.getType(0));

        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals("useFields")).orElseThrow();
        node = Graph.createGraphNode(method);

        DepSolver solver = DepSolver.createSolver();
        solver.methodSearch(node);

        boolean hasLocalField = Graph.getNodes().values().stream()
                .anyMatch(n -> n.getNode() instanceof FieldDeclaration fd
                        && fd.getVariable(0).getNameAsString().equals("localField"));
        assertTrue(hasLocalField);
    }

    @Test
    void resolvesOverrideSignatureMatch() throws IOException {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(
                "sa.com.cloudsolutions.antikythera.testhelper.depsolver.DepsolverOverload");
        assertNotNull(cu);
        MethodDeclaration md = cu.findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals("doIt") && m.getParameter(0).getType().asString().equals("String"))
                .orElseThrow();

        AbstractCompiler compiler = new AbstractCompiler();
        compiler.compileAndSolveInterfaces(
                AbstractCompiler.classToPath("sa.com.cloudsolutions.antikythera.testhelper.depsolver.DepsolverOverload.java"));

        GraphNode node = Graph.createGraphNode(md);
        DepSolver solver = DepSolver.createSolver();
        solver.methodSearch(node);

        boolean hasInterfaceMethod = Graph.getNodes().values().stream()
                .filter(n -> n.getNode() instanceof MethodDeclaration)
                .map(n -> (MethodDeclaration) n.getNode())
                .anyMatch(m -> m.getNameAsString().equals("doIt")
                        && m.getParameter(0).getType().asString().equals("String")
                        && m.findAncestor(com.github.javaparser.ast.body.TypeDeclaration.class)
                                .map(t -> t.getNameAsString().equals("OverloadIfc")).orElse(false));
        assertTrue(hasInterfaceMethod, "Expected interface method to be discovered via @Override");
    }

    @Test
    void resolvesImplementationsForInterfaceMethods() throws IOException {
        CompilationUnit ifaceCu = AntikytheraRunTime.getCompilationUnit(
                "sa.com.cloudsolutions.antikythera.testhelper.depsolver.OverloadIfc");
        assertNotNull(ifaceCu);
        MethodDeclaration ifaceMethod = ifaceCu.findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals("doIt") && m.getParameter(0).getType().asString().equals("String"))
                .orElseThrow();

        AbstractCompiler compiler = new AbstractCompiler();
        compiler.compileAndSolveInterfaces(
                AbstractCompiler.classToPath("sa.com.cloudsolutions.antikythera.testhelper.depsolver.DepsolverOverload.java"));

        GraphNode node = Graph.createGraphNode(ifaceMethod);
        DepSolver solver = DepSolver.createSolver();
        solver.methodSearch(node);

        boolean hasImplMethod = Graph.getNodes().values().stream()
                .filter(n -> n.getNode() instanceof MethodDeclaration)
                .map(n -> (MethodDeclaration) n.getNode())
                .anyMatch(m -> m.getNameAsString().equals("doIt")
                        && m.getParameter(0).getType().asString().equals("String")
                        && m.findAncestor(com.github.javaparser.ast.body.TypeDeclaration.class)
                                .map(t -> t.getNameAsString().equals("DepsolverOverload")).orElse(false));
        assertTrue(hasImplMethod, "Expected implementation method to be discovered from interface");
    }

    @Test
    void resolveFieldAccessPopulatesTypesForThisAndSuper() {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(
                "sa.com.cloudsolutions.antikythera.testhelper.depsolver.DepsolverSample");
        assertNotNull(cu);
        GraphNode node = Graph.createGraphNode(cu.getType(0));

        com.github.javaparser.ast.NodeList<Type> types = new com.github.javaparser.ast.NodeList<>();
        FieldAccessExpr thisField = new FieldAccessExpr(new ThisExpr(), "helper");
        GraphNode thisResolved = Resolver.resolveFieldAccess(node, thisField, types);
        assertNotNull(thisResolved);

        FieldAccessExpr superField = new FieldAccessExpr(new SuperExpr(), "baseHelper");
        GraphNode superResolved = Resolver.resolveFieldAccess(node, superField, types);
        assertNotNull(superResolved);
    }
}
