package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.type.Type;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
        postSetup("sa.com.cloudsolutions.antikythera.testhelper.evaluator.Person");
        FieldDeclaration field = sourceClass.getFieldByName("name").orElseThrow();
        node.setNode(field);

        depSolver.fieldSearch(node);

        Optional<FieldDeclaration> addedField = node.getTypeDeclaration().getFieldByName("name");
        assertTrue(addedField.isPresent(), "Field should be added to the class.");
    }

    @Test
    void testFieldSearchAddsAnnotations() throws AntikytheraException {
        postSetup("sa.com.cloudsolutions.antikythera.testhelper.evaluator.Person");
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
        postSetup("sa.com.cloudsolutions.antikythera.testhelper.evaluator.Person");
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
        postSetup("sa.com.cloudsolutions.antikythera.testhelper.evaluator.Person");
        MethodDeclaration md = sourceClass.getMethodsByName("getName").getFirst();
        node.setNode(md);

        depSolver.methodSearch(node);
        MethodDeclaration m = node.getTypeDeclaration().getMethodsByName("getName").getFirst();
        assertNotNull(m, "method should be added to the class.");

    }

    @Test
    void testSetter() throws AntikytheraException {
        postSetup("sa.com.cloudsolutions.antikythera.testhelper.evaluator.Person");
        MethodDeclaration md = sourceClass.getMethodsByName("setId").get(1);
        node.setNode(md);

        depSolver.methodSearch(node);
        MethodDeclaration m = node.getTypeDeclaration().getMethodsByName("setId").getFirst();
        assertNotNull(m, "method should be added to the class.");
        assertEquals(1, m.getParameters().size());
        assertEquals("String", m.getParameter(0).getTypeAsString());
        assertTrue(Graph.getDependencies().containsKey("sa.com.cloudsolutions.antikythera.testhelper.evaluator.IPerson"));
    }

    @Test
    void testMethodReference() {
        postSetup("sa.com.cloudsolutions.antikythera.testhelper.evaluator.Functional");
        Graph.createGraphNode(sourceClass.getMethodsByName("people1").getFirst());
        depSolver.dfs();

        CompilationUnit resolved = Graph.getDependencies().get("sa.com.cloudsolutions.antikythera.testhelper.evaluator.Person");
        assertNotNull(resolved);
        String s = resolved.toString();
        assertTrue(s.contains("getName"));
    }


    @ParameterizedTest
    @ValueSource(strings = {"searchFakeDataWithCriteria1", "searchFakeDataWithCriteria2"})
    void testSpecification(String name) {
        postSetup("sa.com.cloudsolutions.antikythera.testhelper.evaluator.FakeService");
        Graph.createGraphNode(
                node.getEnclosingType().asClassOrInterfaceDeclaration()
                        .getMethodsByName(name).getFirst());

        depSolver.dfs();
        Map<String, CompilationUnit> a = Graph.getDependencies();
        assertTrue(a.containsKey("sa.com.cloudsolutions.antikythera.testhelper.evaluator.FakeService"));
        assertTrue(a.containsKey("sa.com.cloudsolutions.antikythera.testhelper.evaluator.FakeSearchModel"));
        assertTrue(a.containsKey("sa.com.cloudsolutions.antikythera.testhelper.evaluator.FakeEntity"));
        assertTrue(a.containsKey("sa.com.cloudsolutions.antikythera.testhelper.evaluator.FakeRepository"));
        assertTrue(a.containsKey("sa.com.cloudsolutions.antikythera.testhelper.evaluator.CrazySpecification"));

        CompilationUnit cs = a.get("sa.com.cloudsolutions.antikythera.testhelper.evaluator.CrazySpecification");
        ClassOrInterfaceDeclaration cst = cs.getType(0).asClassOrInterfaceDeclaration();
        assertEquals(2, cst.findAll(MethodDeclaration.class).size(),
                "Should be two with the inner method");

    }

    @Test
    void testAnnotationBinary() {
        postSetup("sa.com.cloudsolutions.antikythera.testhelper.depsolver.DummyClass");

        AnnotationExpr ann = sourceClass
                .getMethodsByName("binaryAnnotation").getFirst()
                .getAnnotationByName("DummyAnnotation").orElseThrow();
        Resolver.resolveNormalAnnotationExpr(node, ann.asNormalAnnotationExpr());

        CompilationUnit resolved = Graph.getDependencies().get("sa.com.cloudsolutions.antikythera.testhelper.depsolver.DummyClass");
        assertNotNull(resolved);
        String s = resolved.toString();
        assertFalse(s.contains("DummyAnnotation"),
                "The annotation visitor is not invoked so annotation should not be present");
        assertTrue(s.contains("PREFIX"),"Direct call to resolveNormalAnnotationExpr keeps PRE field");
        assertTrue(s.contains("SUFFIX"),"Direct call to resolveNormalAnnotationExpr keeps PRE field");
    }


    @Test
    void testAnnotationWithField() {
        postSetup("sa.com.cloudsolutions.antikythera.testhelper.depsolver.DummyClass");

        AnnotationExpr ann = sourceClass
                .getMethodsByName("annotationWIthField").getFirst()
                .getAnnotationByName("DummyAnnotation").orElseThrow();
        Resolver.resolveNormalAnnotationExpr(node, ann.asNormalAnnotationExpr());
        CompilationUnit resolved = Graph.getDependencies().get("sa.com.cloudsolutions.antikythera.testhelper.depsolver.DummyClass");
        assertNotNull(resolved);
        String s = resolved.toString();
        assertFalse(s.contains("DummyAnnotation"),
                "The annotation visitor is not invoked so annotation should not be present");
        assertTrue(s.contains("PREFIX"),"Direct call to resolveNormalAnnotationExpr keeps PRE field");
        assertFalse(s.contains("SUFFIX"),"Direct call to resolveNormalAnnotationExpr keeps PRE field");
    }

    @Test
    void testThisAccess1() {
        postSetup("sa.com.cloudsolutions.antikythera.testhelper.evaluator.Employee");
        // create a new FieldAccessExpression with this.
        FieldAccessExpr fieldAccessExpr = new FieldAccessExpr(new NameExpr("this"), "p");
        Resolver.resolveField(node, fieldAccessExpr);

        CompilationUnit resolved = Graph.getDependencies().get("sa.com.cloudsolutions.antikythera.testhelper.evaluator.Employee");
        assertNotNull(resolved);
        String s = resolved.toString();
        assertTrue(s.contains("Hornblower"));
    }

    @Test
    void testThisAccess2() {
        postSetup("sa.com.cloudsolutions.antikythera.testhelper.evaluator.Employee");
        // create a new FieldAccessExpression with this.
        FieldAccessExpr fieldAccessExpr = new FieldAccessExpr(new NameExpr("this"), "objectMapper");
        Resolver.resolveField(node, fieldAccessExpr);
        CompilationUnit resolved = Graph.getDependencies().get("sa.com.cloudsolutions.antikythera.testhelper.evaluator.Employee");
        assertNotNull(resolved);
        String s = resolved.toString();
        assertTrue(s.contains("com.fasterxml.jackson.databind.ObjectMapper"));
        assertTrue(s.contains("objectMapper = new ObjectMapper()"));
    }

    @Test
    void testFindInterfaceImplementations() throws AntikytheraException {
        postSetup("sa.com.cloudsolutions.antikythera.testhelper.evaluator.IPerson");
        assertTrue(sourceClass.isInterface(), "Test should use an interface");

        MethodDeclaration method = sourceClass.getMethodsByName("getName").getFirst();
        depSolver.methodSearch(Graph.createGraphNode(method));

        Map<String, CompilationUnit> deps = Graph.getDependencies();
        assertTrue(deps.containsKey("sa.com.cloudsolutions.antikythera.testhelper.evaluator.Person"),
                "Should find Person implementation");

        GraphNode top = depSolver.peek();
        assertNotNull(top);
        assertInstanceOf(MethodDeclaration.class, top.getNode());
    }

    @Test
    void testResolveArrayAccessExpr() {
        postSetup("sa.com.cloudsolutions.antikythera.testhelper.evaluator.Locals");

        // Find array access expressions in the people method
        MethodDeclaration peopleMethod = sourceClass.getMethodsByName("people").getFirst();
        var arrayAccessExprs = peopleMethod.findAll(ArrayAccessExpr.class);
        assertFalse(arrayAccessExprs.isEmpty());

        // Setup array type in names map
        DepSolver.getNames().put("a", new com.github.javaparser.ast.type.ArrayType(
                new com.github.javaparser.ast.type.ClassOrInterfaceType(null, "IPerson")));

        // Test the method
        NodeList<Type> types = new NodeList<>();
        Resolver.processExpression(node, arrayAccessExprs.get(0), types);

        // Verify component type extracted
        assertFalse(types.isEmpty());
        assertEquals("IPerson", types.get(0).asString());
    }

    @ParameterizedTest
    @CsvSource({
        "drinkable, 0",
        "ternary6, 2"
    })
    void testResolveConditionalExpr(String methodName, int expectedTypeCount) {
        postSetup("sa.com.cloudsolutions.antikythera.testhelper.evaluator.Conditional");

        MethodDeclaration method = sourceClass.getMethodsByName(methodName).getFirst();
        var conditionalExprs = method.findAll(com.github.javaparser.ast.expr.ConditionalExpr.class);
        assertFalse(conditionalExprs.isEmpty());

        NodeList<Type> types = new NodeList<>();
        Resolver.processExpression(node, conditionalExprs.get(0), types);

        assertEquals(expectedTypeCount, types.size());
    }

    @Test
    void testResolveFieldAccess() {
        postSetup("sa.com.cloudsolutions.antikythera.testhelper.evaluator.Employee");

        // Find field access expressions in the chained method
        MethodDeclaration chainedMethod = sourceClass.getMethodsByName("chained").getFirst();
        var fieldAccessExprs = chainedMethod.findAll(FieldAccessExpr.class);
        assertFalse(fieldAccessExprs.isEmpty());

        DepSolver.getNames().put("p", new com.github.javaparser.ast.type.ClassOrInterfaceType(null, "Person"));

        NodeList<Type> types = new NodeList<>();
        FieldAccessExpr pNameExpr = fieldAccessExprs.stream()
                .filter(fae -> fae.getNameAsString().equals("name"))
                .findFirst()
                .orElseThrow();

        Resolver.resolveFieldAccess(node, pNameExpr, types);

        assertFalse(types.isEmpty());
        assertTrue(types.get(0).isClassOrInterfaceType());
    }
}
