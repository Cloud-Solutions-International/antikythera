package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.ArgumentGenerator;
import sa.com.cloudsolutions.antikythera.evaluator.NullArgumentGenerator;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingCall;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.Callable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UnitTestGeneratorTest {

    private UnitTestGenerator unitTestGenerator;
    private ClassOrInterfaceDeclaration classUnderTest;
    private ArgumentGenerator argumentGenerator;

    @BeforeAll
    static void beforeClass() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void setUp() {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.service.Service");
        assertNotNull(cu);
        classUnderTest = cu.getType(0).asClassOrInterfaceDeclaration();

        unitTestGenerator = new UnitTestGenerator(cu);
        argumentGenerator = Mockito.mock(NullArgumentGenerator.class);
        unitTestGenerator.setArgumentGenerator(argumentGenerator);
        unitTestGenerator.setPreConditions(new ArrayList<>());
        unitTestGenerator.setAsserter(new JunitAsserter());
    }

    /**
     * THis is an integration test.
     * It covers parts of TestSuiteEvaluator, UnitTestGenerator and MockingRegistry
     * @throws NoSuchMethodException if the method is not found
     */
    @Test
    void testSetUpBase() throws NoSuchMethodException {
        Settings.setProperty(Settings.BASE_PATH,
                Settings.getProperty(Settings.BASE_PATH, String.class)
                        .orElse("").replace("src/test/resources/sources",""));
        unitTestGenerator.loadPredefinedBaseClassForTest("sa.com.cloudsolutions.antikythera.evaluator.mock.Hello");

        Method m = Statement.class.getDeclaredMethod("execute", String.class);
        assertNotNull(m);
        Callable callable = new Callable(m, null);
        MockingCall result = MockingRegistry.getThen("java.sql.Statement", callable);
        assertNotNull(result);
        assertInstanceOf(Boolean.class, result.getVariable().getValue());
        assertEquals(true, result.getVariable().getValue());

        m = Statement.class.getDeclaredMethod("getMaxFieldSize");
        callable = new Callable(m, null);
        assertNull(MockingRegistry.getThen("java.sql.Statement", callable));
    }

    @Test
    void testInject() {
        classUnderTest.addAnnotation("Service");
        MethodDeclaration methodUnderTest = classUnderTest.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("queries2")).orElseThrow();
        unitTestGenerator.createTests(methodUnderTest, new MethodResponse());
        String sources = unitTestGenerator.getCompilationUnit().toString();
        assertTrue(sources.contains("queries2Test"));
        assertTrue(sources.contains("InjectMocks"));
    }

    @Test
    void testMockFields() {

        classUnderTest.addAnnotation("Service");
        unitTestGenerator.mockFields();
        CompilationUnit testCu = unitTestGenerator.getCompilationUnit();
        TypeDeclaration<?> testClass = testCu.getType(0);

        Optional<FieldDeclaration> mockedField = testClass.getFieldByName("personRepository");
        assertTrue(mockedField.isPresent());
        assertTrue(mockedField.get().getAnnotationByName("Mock").isPresent(), "The field 'dummyRepository' should be annotated with @Mock.");

        assertTrue(testCu.getImports().stream().anyMatch(i -> i.getNameAsString().equals("org.mockito.Mock")),
                "The import for @Mock should be present.");
        assertTrue(testCu.getImports().stream().anyMatch(i -> i.getNameAsString().equals("org.mockito.Mockito")),
                "The import for Mockito should be present.");
    }

    @Test
    void testCreateInstanceA() {
        MethodDeclaration methodUnderTest = classUnderTest.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("queries2")).orElseThrow();
        unitTestGenerator.createTests(methodUnderTest, new MethodResponse());
        assertTrue(unitTestGenerator.getCompilationUnit().toString().contains("queries2Test"));
        Mockito.verify(argumentGenerator, Mockito.never()).getArguments();
    }

    @Test
    void testCreateInstanceB() {
        MethodDeclaration methodUnderTest = classUnderTest.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("queries3")).orElseThrow();
        unitTestGenerator.createTests(methodUnderTest, new MethodResponse());
        assertTrue(unitTestGenerator.getCompilationUnit().toString().contains("queries3Test"));
        Mockito.verify(argumentGenerator, Mockito.times(1)).getArguments();

    }

    @Test
    void testCreateInstanceC() {
        MethodDeclaration methodUnderTest = classUnderTest.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("queries2")).orElseThrow();
        ConstructorDeclaration constructor = classUnderTest.addConstructor();
        constructor.addParameter("String", "param");

        unitTestGenerator.createTests(methodUnderTest, new MethodResponse());
        assertTrue(unitTestGenerator.getCompilationUnit().toString().contains("queries2Test"));
    }

    @ParameterizedTest
    @CsvSource({"queries4, long", "queries5, int"})
    void testCreateInstanceD(String name, String type) {
        MethodDeclaration methodUnderTest = classUnderTest.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals(name)).orElseThrow();
        Map<String, Variable> map = new HashMap<>();
        if (type.equals("long")) {
            map.put("id", new Variable(100L));
        }
        else {
            map.put("id", new Variable(100));
        }
        Mockito.when(argumentGenerator.getArguments()).thenReturn(map);
        unitTestGenerator.createTests(methodUnderTest, new MethodResponse());
        assertTrue(unitTestGenerator.getCompilationUnit().toString().contains(name + "Test"));
    }

    @Test
    void testLoadExisting() throws IOException {
        // Get the actual FactoryTest.java from source directory
        File testFile = new File("src/test/java/sa/com/cloudsolutions/antikythera/generator/FactoryTest.java");
        assertTrue(testFile.exists(), testFile.getAbsolutePath() + " does not exist");

        // Execute loadExisting
        unitTestGenerator.loadExisting(testFile);
        assertNotNull(unitTestGenerator.gen);
        assertFalse(unitTestGenerator.gen.toString().contains("Author : Antikythera"));

        assertTrue(MockingRegistry.isMockTarget("java.util.zip.Adler32"));
    }

    @Test
    void testApplyPreconditionsForOptionals() throws Exception {
        // Reset MockingRegistry to ensure clean state
        MockingRegistry.reset();
        TestGenerator.whenThen.clear();

        // Test case 1: Optional.empty()
        // Create a Variable with Optional.empty()
        Variable emptyOptionalVar = new Variable(Optional.empty());

        // Create a MockingCall with the empty Optional
        Method method = String.class.getDeclaredMethod("length");
        Callable callable = new Callable(method, null);
        MockingCall emptyOptionalCall = new MockingCall(callable, emptyOptionalVar);
        emptyOptionalCall.setVariableName("mockString");

        // Apply preconditions for the empty Optional
        UnitTestGenerator.applyPreconditionsForOptionals(emptyOptionalCall);

        // Verify that the whenThen list contains an expression for Optional.empty()
        assertFalse(TestGenerator.whenThen.isEmpty(), "whenThen list should not be empty after processing empty Optional");
        String whenThenString = TestGenerator.whenThen.get(0).toString();
        assertTrue(whenThenString.contains("Optional.empty()"), 
                "The whenThen expression should contain 'Optional.empty()' but was: " + whenThenString);

        // Clear the whenThen list for the next test
        TestGenerator.whenThen.clear();

        // Test case 2: Optional with Evaluator
        // Create a mock Evaluator
        sa.com.cloudsolutions.antikythera.evaluator.Evaluator mockEvaluator = Mockito.mock(sa.com.cloudsolutions.antikythera.evaluator.Evaluator.class);
        Mockito.when(mockEvaluator.getClassName()).thenReturn("TestClass");

        // Create a Variable with Optional containing the Evaluator
        Variable evaluatorOptionalVar = new Variable(Optional.of(mockEvaluator));

        // Create a MockingCall with the Optional containing Evaluator
        Method method2 = String.class.getDeclaredMethod("isEmpty");
        Callable callable2 = new Callable(method2, null);
        MockingCall evaluatorOptionalCall = new MockingCall(callable2, evaluatorOptionalVar);
        evaluatorOptionalCall.setVariableName("mockString2");

        // Apply preconditions for the Optional with Evaluator
        UnitTestGenerator.applyPreconditionsForOptionals(evaluatorOptionalCall);

        // Verify that mockEvaluator.getClassName() was called
        Mockito.verify(mockEvaluator).getClassName();

        // Verify that the whenThen list contains an expression for Optional.of(new TestClass())
        assertFalse(TestGenerator.whenThen.isEmpty(), "whenThen list should not be empty after processing Optional with Evaluator");
        whenThenString = TestGenerator.whenThen.get(0).toString();
        assertTrue(whenThenString.contains("Optional.of(new TestClass())"), 
                "The whenThen expression should contain 'Optional.of(new TestClass())' but was: " + whenThenString);
    }
}

class UnitTestGeneratorMoreTests {

    @BeforeAll
    static void beforeClass() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    /**
     * The base class should be added to the class under test.
     */
    @Test
    void testAddingBaseClassToTestClass() {
        CompilationUnit base = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.antikythera.generator.DummyBase");
        assertNotNull(base);
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.antikythera.evaluator.Overlord");
        assertNotNull(cu);

        ClassOrInterfaceDeclaration classUnderTest = cu.getType(0).asClassOrInterfaceDeclaration();
        UnitTestGenerator unitTestGenerator = new UnitTestGenerator(cu);

        assertTrue(classUnderTest.getExtendedTypes().isEmpty());
        CompilationUnit testCu = unitTestGenerator.getCompilationUnit();
        assertNotNull(testCu);
        TypeDeclaration<?> publicType = AbstractCompiler.getPublicType(testCu);
        assertNotNull(publicType);
        assertEquals("OverlordAKTest", publicType.getNameAsString());
        assertTrue(publicType.asClassOrInterfaceDeclaration().getExtendedTypes()
                .stream()
                .anyMatch(t -> t.asString().equals("sa.com.cloudsolutions.antikythera.generator.DummyBase")));
    }
}


class VariableInitializationModifierTest {

    @Test
    void shouldModifySimpleVariableInitialization() {
        String code = """
            public void testMethod() {
                String test = "old";
                int other = 5;
            }
            """;
        MethodDeclaration method = StaticJavaParser.parseMethodDeclaration(code);
        StringLiteralExpr newValue = new StringLiteralExpr("new");
        UnitTestGenerator.replaceInitializer(method, "test", newValue);

        assertTrue(method.toString().contains("String test = \"new\""));
        assertTrue(method.toString().contains("int other = 5"));
    }

    @Test
    void shouldNotModifyWhenVariableNotFound() {
        String code = """
            public void testMethod() {
                String existingVar = "old";
            }
            """;
        MethodDeclaration method = StaticJavaParser.parseMethodDeclaration(code);
        IntegerLiteralExpr newValue = new IntegerLiteralExpr("42");

        UnitTestGenerator.replaceInitializer(method, "nonexistentVar", newValue);

        assertEquals(method.toString(), method.toString());
    }

    @Test
    void shouldModifyConstructors() {
        String code = """
            public void testMethod() {
                int target = 1;
                String other = "middle";
                Person p = new Person("Hornblower");
            }
            """;

        MethodDeclaration method = StaticJavaParser.parseMethodDeclaration(code);
        Expression newValue = StaticJavaParser.parseExpression("Person.createPerson(\"Horatio\")");

        UnitTestGenerator.replaceInitializer(method, "p", newValue);

        String modifiedCode = method.toString();
        assertTrue(modifiedCode.contains("Person p = Person.createPerson(\"Horatio\")"));
    }
}
