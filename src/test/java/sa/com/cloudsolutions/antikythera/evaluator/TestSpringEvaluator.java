package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.generator.MethodResponse;
import sa.com.cloudsolutions.antikythera.generator.TestGenerator;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.IOException;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TestSpringEvaluator {

    public static final String PERSON_REPO = "sa.com.cloudsolutions.repository.PersonRepository";

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap();
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void each() {
        AntikytheraRunTime.reset();
    }

    @Test
    void testSimpleController() throws AntikytheraException, ReflectiveOperationException {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.controller.SimpleController");
        SpringEvaluator eval = new SpringEvaluator("sa.com.cloudsolutions.controller.SimpleController");

        // calling with out setting up the proper set of arguments will result in null because
        // the method will not be executed in the absence of the required arguments
        MethodDeclaration md = cu.findFirst(MethodDeclaration.class).orElseThrow();
        eval.executeMethod(md);
        assertNull(eval.returnValue);

        AntikytheraRunTime.push(new Variable(1L));
        eval.executeMethod(md);
        assertNotNull(eval.returnValue);
    }

    @Test
    void testGetFieldClass() {
        SpringEvaluator evaluator = new SpringEvaluator("TestClass");
        evaluator.setCompilationUnit(new CompilationUnit());
        // Test with NameExpr and different Variable types
        NameExpr nameExpr = new NameExpr("testField");

        // Test with a Variable having a Type
        ClassOrInterfaceType stringType = StaticJavaParser.parseClassOrInterfaceType("String");
        Variable typeVariable = new Variable("test");
        typeVariable.setType(stringType);
        evaluator.getFields().put("testField", typeVariable);
        assertEquals("java.lang.String", evaluator.getFieldClass(nameExpr));

        // Test with a Variable containing an Evaluator
        SpringEvaluator innerEvaluator = new SpringEvaluator("TestInnerClass");
        Variable evaluatorVariable = new Variable(innerEvaluator);
        evaluator.getFields().put("evaluatorField", evaluatorVariable);
        assertEquals("TestInnerClass", evaluator.getFieldClass(new NameExpr("evaluatorField")));

        // Test with MethodCallExpr
        MethodCallExpr methodCall = StaticJavaParser.parseExpression("evaluatorField.someMethod()").asMethodCallExpr();
        assertEquals("TestInnerClass", evaluator.getFieldClass(methodCall));

        // Test with non-existent field
        assertNull(evaluator.getFieldClass(new NameExpr("nonExistentField")));
    }

    @Test
    void testExecuteSource() throws AntikytheraException, ReflectiveOperationException {
        SpringEvaluator evaluator = new SpringEvaluator("TestClass");
        evaluator.setCompilationUnit(new CompilationUnit());

        MethodCallExpr methodCall = new MethodCallExpr();
        methodCall.setName("findAll");
        methodCall.setScope(new NameExpr("testRepo"));

        Variable result = evaluator.executeSource(methodCall);
        assertNull(result);
    }

    @Test
    void testCreateTestsUnhappy() {
        SpringEvaluator evaluator = new SpringEvaluator("TestClass");
        assertNull(evaluator.createTests(null));
    }

    @Test
    void testCreateTests() throws ReflectiveOperationException {
        SpringEvaluator evaluator = new SpringEvaluator("TestClass");

        // Access private currentMethod field via reflection
        Field currentMethodField = SpringEvaluator.class.getDeclaredField("currentMethod");
        currentMethodField.setAccessible(true);

        // Mock TestGenerator
        TestGenerator mockGenerator = mock(TestGenerator.class);
        evaluator.addGenerator(mockGenerator);

        // Create test data
        MethodDeclaration methodDecl = new MethodDeclaration()
            .setName("testMethod");
        currentMethodField.set(evaluator, methodDecl);

        // Create a method response
        ResponseEntity<String> responseEntity = new ResponseEntity<>(HttpStatus.OK);
        MethodResponse response = new MethodResponse();
        response.setResponse(new Variable(responseEntity));

        // Execute createTests
        Variable result = evaluator.createTests(response);

        // Verify interactions
        verify(mockGenerator).setPreConditions(any());
        verify(mockGenerator).createTests(methodDecl, response);

        // Verify result
        assertNotNull(result);
        assertInstanceOf(MethodResponse.class, result.getValue());
        assertEquals(response, result.getValue());
    }

    @Test
    void testAutoWireWithAutowiredField()  {
        SpringEvaluator evaluator = new SpringEvaluator("sa.com.cloudsolutions.service.Service");
        CompilationUnit cu = evaluator.getCompilationUnit();

        FieldDeclaration fieldDecl = cu.findFirst(FieldDeclaration.class).get();
        VariableDeclarator variable = fieldDecl.getVariable(0);
        assertTrue(evaluator.autoWire(variable, PERSON_REPO));
        Variable f = AntikytheraRunTime.getAutoWire(PERSON_REPO);
        assertNotNull(f);
    }

    @Test
    void testAutoWireWithMock()  {
        SpringEvaluator evaluator = new SpringEvaluator("sa.com.cloudsolutions.service.Service");
        CompilationUnit cu = evaluator.getCompilationUnit();

        FieldDeclaration fieldDecl = cu.findFirst(FieldDeclaration.class).get();
        VariableDeclarator variable = fieldDecl.getVariable(0);
        AntikytheraRunTime.markAsMocked(fieldDecl.getElementType());

        assertTrue(evaluator.autoWire(variable, PERSON_REPO));
        Variable f = AntikytheraRunTime.getAutoWire(PERSON_REPO);
        assertNotNull(f);
        assertInstanceOf(MockingEvaluator.class, f.getValue());
    }

    @Test
    void testAutoWireWithout()  {
        String testClass = """
            @Component
            public class TestClass {
                private TestRepository testRepo;
            }
            """;

        CompilationUnit cu = StaticJavaParser.parse(testClass);
        AntikytheraRunTime.addClass("TestClass", cu);
        SpringEvaluator evaluator = new SpringEvaluator("TestClass");
        evaluator.setCompilationUnit(cu);

        // Get the field from the parsed class
        FieldDeclaration fieldDecl = cu.findFirst(FieldDeclaration.class).get();
        VariableDeclarator variable = fieldDecl.getVariable(0);
        assertFalse(evaluator.autoWire(variable, "TestClass"));

    }
}
