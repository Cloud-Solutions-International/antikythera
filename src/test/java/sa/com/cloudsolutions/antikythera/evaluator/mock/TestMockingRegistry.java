package sa.com.cloudsolutions.antikythera.evaluator.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.EvaluatorFactory;
import sa.com.cloudsolutions.antikythera.evaluator.TestHelper;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.Callable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestMockingRegistry extends TestHelper {

    public static final String CLASS_UNDER_TEST = "sa.com.cloudsolutions.antikythera.evaluator.Employee";

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void each() {
        evaluator = EvaluatorFactory.create(CLASS_UNDER_TEST, Evaluator.class);
    }

    @Test
    void testUseByteBuddy() throws ReflectiveOperationException {
        VariableDeclarator variableDeclarator = evaluator.getCompilationUnit()
                .findFirst(VariableDeclarator.class, vd -> vd.getNameAsString().equals("objectMapper")).orElseThrow();

        Variable result = MockingRegistry.mockIt(variableDeclarator);

        assertNotNull(result);
        assertEquals(ObjectMapper.class, result.getClazz());
    }

    @ParameterizedTest
    @CsvSource({
            "String,     anyString",
            "int,        anyInt",
            "Integer,    anyInt",
            "long,       anyLong",
            "Long,       anyLong",
            "double,     anyDouble",
            "Double,     anyDouble",
            "boolean,    anyBoolean",
            "Boolean,    anyBoolean",
            "Object,     any",
            "UnknownType, any"
    })
    void fakeArgumentsCreatesCorrectMatchers(String parameterType, String expectedMatcher) {
        // Setup
        MethodDeclaration methodDecl = new MethodDeclaration();
        Parameter parameter = new Parameter()
                .setType(parameterType)
                .setName("param");
        methodDecl.addParameter(parameter);

        // Execute
        NodeList<Expression> args = MockingRegistry.fakeArguments(methodDecl);

        // Verify
        assertEquals(1, args.size());
        if (parameterType.equals("UnknownType")) {
            assertInstanceOf(CastExpr.class, args.getFirst().orElseThrow());
        }
        else {
            MethodCallExpr matcher = (MethodCallExpr) args.getFirst().orElseThrow();
            assertEquals("Mockito", matcher.getScope().orElseThrow().toString());
            assertEquals(expectedMatcher, matcher.getNameAsString());
        }
    }

    @Test
    void testGetAllMocks() {
        MockingRegistry.reset();
        MockingRegistry.markAsMocked("TestClass1");
        MockingRegistry.markAsMocked("TestClass2");

        Callable callable1 = new Callable(new MethodDeclaration(), null);
        Callable callable2 = new Callable(new MethodDeclaration(), null);
        Variable returnVal = new Variable("test");

        MockingRegistry.when("TestClass1", new MockingCall(callable1, returnVal));
        MockingRegistry.when("TestClass2", new MockingCall(callable2, returnVal));

        List<MockingCall> result = MockingRegistry.getAllMocks();

        assertEquals(2, result.size());
    }

    @Test
    void testMockItWithMockito() throws Exception {
        // Setup
        MockingRegistry.reset();
        Settings.setProperty(Settings.MOCK_WITH_INTERNAL, "Mockito");

        VariableDeclarator variableDeclarator = evaluator.getCompilationUnit()
                .findFirst(VariableDeclarator.class, vd -> vd.getNameAsString().equals("objectMapper")).orElseThrow();

        Variable result = MockingRegistry.mockIt(variableDeclarator);

        // Verify the initial mock
        assertNotNull(result);
        assertEquals(ObjectMapper.class, result.getClazz());
        assertTrue(Mockito.mockingDetails(result.getValue()).isMock());

        // Call createParser() using reflection and verify its return value is also a mock
        Method createParser = ObjectMapper.class.getMethod("createParser", String.class);
        Object parser = createParser.invoke(result.getValue(), "test");

        assertNotNull(parser);
        assertTrue(Mockito.mockingDetails(parser).isMock());
    }
}
