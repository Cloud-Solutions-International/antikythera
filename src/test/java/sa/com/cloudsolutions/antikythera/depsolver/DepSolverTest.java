package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.TestHelper;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepSolverTest extends TestHelper {
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

    }

    @AfterAll
    static void afterAll() {
        AntikytheraRunTime.resetAll();
    }

    private void postSetup(String name) {
        cu = AntikytheraRunTime.getCompilationUnit(name);
        sourceClass = cu.getType(0).asClassOrInterfaceDeclaration();
        node = Graph.createGraphNode(sourceClass); // Use the Graph.createGraphNode method to create GraphNode
    }

    @Test
    void testFieldSearchAddsFieldToClass() throws AntikytheraException {
        postSetup("sa.com.cloudsolutions.antikythera.evaluator.Person");
        FieldDeclaration field = sourceClass.getFieldByName("name").orElseThrow();
        node.setNode(field);

        depSolver.fieldSearch(node);

        Optional<FieldDeclaration> addedField = node.getTypeDeclaration().getFieldByName("name");
        assertTrue(addedField.isPresent(), "Field should be added to the class.");
    }

    @Test
    void testFieldSearchAddsAnnotations() throws AntikytheraException {
        postSetup("sa.com.cloudsolutions.antikythera.evaluator.Person");
        FieldDeclaration field = sourceClass.getFieldByName("name").orElseThrow();
        node.setNode(field);
        field.addAnnotation("Deprecated");

        depSolver.fieldSearch(node);

        Optional<FieldDeclaration> addedField = sourceClass.getFieldByName("name");
        assertTrue(addedField.isPresent(), "Field should be added to the class.");
        assertTrue(addedField.get().getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals("Deprecated")),
                "Field should have the Deprecated annotation.");
    }

    @Test
    void testFieldSearchAddsImports() throws AntikytheraException {
        postSetup("sa.com.cloudsolutions.antikythera.evaluator.Person");
        FieldDeclaration field = sourceClass.getFieldByName("name").orElseThrow();
        node.setNode(field);

        field.addAnnotation("Data");
        cu.addImport("org.lombok.Data");

        depSolver.fieldSearch(node);

        assertTrue(node.getDestination().getImports().stream().anyMatch(i -> i.getNameAsString().equals("org.lombok.Data")),
                "Import for lombok should be added.");
    }

    @Test
    void testMethodSearch() throws AntikytheraException {
        postSetup("sa.com.cloudsolutions.antikythera.evaluator.Person");
        MethodDeclaration md = sourceClass.getMethodsByName("getName").getFirst();
        node.setNode(md);

        depSolver.methodSearch(node);
        MethodDeclaration m = node.getTypeDeclaration().getMethodsByName("getName").getFirst();
        assertNotNull(m, "method should be added to the class.");

    }

    @Test
    void testSetter() throws AntikytheraException {
        postSetup("sa.com.cloudsolutions.antikythera.evaluator.Person");
        MethodDeclaration md = sourceClass.getMethodsByName("setId").get(1);
        node.setNode(md);

        depSolver.methodSearch(node);
        MethodDeclaration m = node.getTypeDeclaration().getMethodsByName("setId").getFirst();
        assertNotNull(m, "method should be added to the class.");
        assertEquals(1, m.getParameters().size());
        assertEquals("String", m.getParameter(0).getTypeAsString());
        assertTrue(Graph.getDependencies().containsKey("sa.com.cloudsolutions.antikythera.evaluator.IPerson"));
    }

    @Test
    void testMethodReference() {
        postSetup("sa.com.cloudsolutions.antikythera.evaluator.Functional");
        Graph.createGraphNode(sourceClass.getMethodsByName("people1").getFirst());
        depSolver.dfs();

        CompilationUnit resolved = Graph.getDependencies().get("sa.com.cloudsolutions.antikythera.evaluator.Person");
        assertNotNull(resolved);
        String s = resolved.toString();
        assertTrue(s.contains("getName"));
    }


    @Test
    void testSpecification() {
        postSetup("sa.com.cloudsolutions.antikythera.evaluator.FakeService");
        Graph.createGraphNode(
                node.getEnclosingType().asClassOrInterfaceDeclaration()
                        .getMethodsByName("searchFakeDataWithCriteria").get(0));

        depSolver.dfs();
        Map<String, CompilationUnit> a = Graph.getDependencies();
        assertTrue(a.containsKey("sa.com.cloudsolutions.antikythera.evaluator.FakeService"));
        assertTrue(a.containsKey("sa.com.cloudsolutions.antikythera.evaluator.FakeSearchModel"));
        assertTrue(a.containsKey("sa.com.cloudsolutions.antikythera.evaluator.FakeEntity"));
        assertTrue(a.containsKey("sa.com.cloudsolutions.antikythera.evaluator.FakeRepository"));
        assertTrue(a.containsKey("sa.com.cloudsolutions.antikythera.evaluator.CrazySpecification"));

        CompilationUnit cs = a.get("sa.com.cloudsolutions.antikythera.evaluator.CrazySpecification");
        ClassOrInterfaceDeclaration cst = cs.getType(0).asClassOrInterfaceDeclaration().asClassOrInterfaceDeclaration();
        assertEquals(2, cst.findAll(MethodDeclaration.class).size(),
                "Should be two with the inner method");

    }

    @Test
    void testAnnotationBinary() {
        postSetup("sa.com.cloudsolutions.antikythera.depsolver.DummyClass");

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
        postSetup("sa.com.cloudsolutions.antikythera.depsolver.DummyClass");

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
        postSetup("sa.com.cloudsolutions.antikythera.evaluator.Employee");
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
        postSetup("sa.com.cloudsolutions.antikythera.evaluator.Employee");
        // create a new FieldAccessExpression with this.
        FieldAccessExpr fieldAccessExpr = new FieldAccessExpr(new NameExpr("this"), "objectMapper");
        Resolver.resolveField(node, fieldAccessExpr);
        CompilationUnit resolved = Graph.getDependencies().get("sa.com.cloudsolutions.antikythera.evaluator.Employee");
        assertNotNull(resolved);
        String s = resolved.toString();
        assertTrue(s.contains("com.fasterxml.jackson.databind.ObjectMapper"));
        assertTrue(s.contains("objectMapper = new ObjectMapper()"));
    }

}
