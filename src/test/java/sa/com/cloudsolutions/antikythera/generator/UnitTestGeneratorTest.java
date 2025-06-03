package sa.com.cloudsolutions.antikythera.generator;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.ArgumentGenerator;
import sa.com.cloudsolutions.antikythera.evaluator.Branching;
import sa.com.cloudsolutions.antikythera.evaluator.DummyArgumentGenerator;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.EvaluatorFactory;
import sa.com.cloudsolutions.antikythera.evaluator.NullArgumentGenerator;
import sa.com.cloudsolutions.antikythera.evaluator.Precondition;
import sa.com.cloudsolutions.antikythera.evaluator.SpringEvaluator;
import sa.com.cloudsolutions.antikythera.evaluator.TestHelper;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingCall;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.Callable;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UnitTestGeneratorTest {

    private UnitTestGenerator unitTestGenerator;
    private ClassOrInterfaceDeclaration classUnderTest;
    private ArgumentGenerator argumentGenerator;
    private CompilationUnit cu;

    @BeforeAll
    static void beforeClass() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void setUp() {
        cu = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.service.PersonService");
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
    void testIdentifyFieldsToBeMocked() {

        classUnderTest.addAnnotation("Service");
        unitTestGenerator.identifyFieldsToBeMocked();
        CompilationUnit testCu = unitTestGenerator.getCompilationUnit();
        TypeDeclaration<?> testClass = testCu.getType(0);

        Optional<FieldDeclaration> mockedField = testClass.getFieldByName("personRepository");
        assertTrue(mockedField.isPresent());
        assertTrue(mockedField.get().getAnnotationByName("Mock").isPresent(), "The field 'dummyRepository' should be annotated with @Mock.");

        assertFalse(testCu.getImports().stream().anyMatch(i -> i.getNameAsString().equals("org.mockito.Mock")));

        unitTestGenerator.addDependencies();

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
    void testLogger() throws ReflectiveOperationException {
        MethodDeclaration md = classUnderTest.getMethodsByName("queries5").getFirst();
        DummyArgumentGenerator argumentGenerator = new DummyArgumentGenerator();
        unitTestGenerator.setArgumentGenerator(argumentGenerator);
        unitTestGenerator.setupAsserterImports();
        unitTestGenerator.addBeforeClass();

        SpringEvaluator evaluator = EvaluatorFactory.create("sa.com.cloudsolutions.service.PersonService", SpringEvaluator.class);
        evaluator.setOnTest(true);
        evaluator.addGenerator(unitTestGenerator);
        evaluator.setArgumentGenerator(argumentGenerator);
        evaluator.visit(md);
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

        UnitTestGenerator ug = new UnitTestGenerator(cu);
        ug.applyPreconditionsForOptionals(emptyOptionalCall);

        // Verify that the whenThen list contains an expression for Optional.empty()
        assertFalse(TestGenerator.whenThen.isEmpty(), "whenThen list should not be empty after processing empty Optional");
        String whenThenString = TestGenerator.whenThen.getFirst().toString();
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
        ug.applyPreconditionsForOptionals(evaluatorOptionalCall);

        // Verify that mockEvaluator.getClassName() was called
        Mockito.verify(mockEvaluator).getClassName();

        // Verify that the whenThen list contains an expression for Optional.of(new TestClass())
        assertFalse(TestGenerator.whenThen.isEmpty(), "whenThen list should not be empty after processing Optional with Evaluator");
        whenThenString = TestGenerator.whenThen.getFirst().toString();
        assertTrue(whenThenString.contains("Optional.of(new TestClass())"), 
                "The whenThen expression should contain 'Optional.of(new TestClass())' but was: " + whenThenString);
    }
}

class UnitTestGeneratorMoreTests extends TestHelper {
    public static final String FAKE_SERVICE = "sa.com.cloudsolutions.antikythera.evaluator.FakeService";
    public static final String PERSON = "sa.com.cloudsolutions.antikythera.evaluator.Person";
    public static final String CONDITIONAL = "sa.com.cloudsolutions.antikythera.evaluator.Conditional";
    CompilationUnit cu;
    UnitTestGenerator unitTestGenerator;

    @BeforeEach
    void setup() {
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    void after() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.DEBUG);

    }

    @BeforeAll
    static void beforeClass() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
        AntikytheraRunTime.reset();
        Branching.clear();
        MockingRegistry.reset();
        TestGenerator.whenThen.clear();
    }

    private MethodDeclaration setupMethod(String className, String name) {
        cu = AntikytheraRunTime.getCompilationUnit(className);
        unitTestGenerator = new UnitTestGenerator(cu);
        unitTestGenerator.setArgumentGenerator(new DummyArgumentGenerator());
        MethodDeclaration md = cu.findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals(name)).orElseThrow();
        unitTestGenerator.methodUnderTest = md;
        unitTestGenerator.testMethod = unitTestGenerator.buildTestMethod(md);
        unitTestGenerator.setAsserter(new JunitAsserter());
        return md;
    }

    @Test
    void integrationTestCasting() throws ReflectiveOperationException {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.OFF);


        MethodDeclaration md = setupMethod(FAKE_SERVICE,"castingHelper");
        DummyArgumentGenerator argumentGenerator = new DummyArgumentGenerator();
        unitTestGenerator.setArgumentGenerator(argumentGenerator);
        unitTestGenerator.setupAsserterImports();
        unitTestGenerator.addBeforeClass();

        SpringEvaluator evaluator = EvaluatorFactory.create(FAKE_SERVICE, SpringEvaluator.class);
        evaluator.setOnTest(true);
        evaluator.addGenerator(unitTestGenerator);
        evaluator.setArgumentGenerator(argumentGenerator);
        evaluator.visit(md);
        assertTrue(outContent.toString().contains("Found!Not found!"));
        String s = unitTestGenerator.gen.toString();
        assertTrue(s.contains("(List<Integer>)"));
        assertTrue(s.contains("(Set<Integer>)"));
        assertFalse(s.contains("Bada"));
        assertFalse(s.contains(" = Mockito.mock(Set.class);"));
    }

    @Test
    void integrationTestFindAll() throws ReflectiveOperationException {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.OFF);


        MethodDeclaration md = setupMethod(FAKE_SERVICE,"findAll");
        DummyArgumentGenerator argumentGenerator = new DummyArgumentGenerator();
        unitTestGenerator.setArgumentGenerator(argumentGenerator);
        unitTestGenerator.setupAsserterImports();
        unitTestGenerator.addBeforeClass();

        SpringEvaluator evaluator = EvaluatorFactory.create(FAKE_SERVICE, SpringEvaluator.class);
        evaluator.setOnTest(true);
        evaluator.addGenerator(unitTestGenerator);
        evaluator.setArgumentGenerator(argumentGenerator);
        evaluator.visit(md);
        assertTrue(outContent.toString().contains("1!0!"));
        String s = unitTestGenerator.gen.toString();
        assertTrue(s.contains("List.of(fakeEntity"));
        assertTrue(s.contains("List.of()"));

    }

    @Test
    void testAutowiredCollection() throws ReflectiveOperationException {
        MethodDeclaration md = setupMethod(FAKE_SERVICE,"autoList");

        unitTestGenerator.setupAsserterImports();
        unitTestGenerator.addBeforeClass();

        Evaluator evaluator = EvaluatorFactory.create(FAKE_SERVICE, SpringEvaluator.class);
        evaluator.visit(md);
        assertTrue(outContent.toString().contains("Person: class sa.com.cloudsolutions.antikythera.evaluator.MockingEvaluator"));
        assertTrue(unitTestGenerator.gen.toString().contains("@Mock()\n" +
                "    List<IPerson> persons;"));
    }

    @Test
    void testMockWithMockito1() {
        MethodDeclaration md = setupMethod(CONDITIONAL,"printMap");
        Parameter param = md.getParameter(0);
        unitTestGenerator.mockWithMockito(param, new Variable("hello"));

        assertTrue(unitTestGenerator.testMethod.toString().contains("Mockito"));
    }

    @Test
    void identifyFieldsToBeMocked() {
        setupMethod(CONDITIONAL,"main");
        assertFalse(unitTestGenerator.testMethod.toString().contains("Mockito"));
        Evaluator eval = EvaluatorFactory.create(PERSON, Evaluator.class);
        unitTestGenerator.mockParameterFields(new Variable(eval),  "bada");
        assertFalse(unitTestGenerator.testMethod.toString().contains("Mockito.when(bada.getId()).thenReturn(0);"),
            "Default primitive values should not be mocked.");
        assertFalse(unitTestGenerator.testMethod.toString().contains("Mockito.when(bada.getAge()).thenReturn(0);"));
    }

    @Test
    void mockWithEvaluator() {
        MethodDeclaration md = setupMethod(CONDITIONAL,"switchCase1");
        Type t = new ClassOrInterfaceType().setName("Person");
        Parameter p = new Parameter(t, "person");
        md.getParameters().add(p);

        Variable v = new Variable("bada");
        unitTestGenerator.mockWithMockito(p, v);
        assertTrue(unitTestGenerator.testMethod.toString().contains("Person"));
    }

    @Test
    void testMockWithMockito2() {
        MethodDeclaration md = setupMethod(CONDITIONAL,"main");
        Parameter param = md.getParameter(0);
        unitTestGenerator.mockWithMockito(param, new Variable("hello"));

        assertFalse(unitTestGenerator.testMethod.toString().contains("Mockito"));
        assertTrue(unitTestGenerator.testMethod.toString().contains("String[] args = new String[] { \"Antikythera\" };"));
    }

    @Test
    void testMockWithMockito3() {
        MethodDeclaration md = setupMethod(CONDITIONAL,"main");
        Parameter param = md.getParameter(0);
        unitTestGenerator.mockWithMockito(param, new Variable("hello"));
        MethodCallExpr mce = new MethodCallExpr(new NameExpr("Bean"), "setName");
        mce.addArgument("Shagrat");
        unitTestGenerator.setPreConditions(List.of(new Precondition(mce)));

        assertFalse(unitTestGenerator.testMethod.toString().contains("Shagrat"));
        unitTestGenerator.applyPreconditions();
        assertTrue(unitTestGenerator.testMethod.toString().contains("Shagrat"));

    }

    /**
     * The base class should be added to the class under test.
     */
    @Test
    void testAddingBaseClassToTestClass() {
        CompilationUnit base = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.antikythera.generator.DummyBase");
        assertNotNull(base);
        CompilationUnit compilationUnit = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.antikythera.evaluator.Overlord");
        assertNotNull(compilationUnit);

        ClassOrInterfaceDeclaration classUnderTest = compilationUnit.getType(0).asClassOrInterfaceDeclaration();
        UnitTestGenerator utg = new UnitTestGenerator(compilationUnit);

        assertTrue(classUnderTest.getExtendedTypes().isEmpty());
        CompilationUnit testCu = utg.getCompilationUnit();
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
