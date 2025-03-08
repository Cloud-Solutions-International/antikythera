package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.generator.MethodResponse;
import sa.com.cloudsolutions.antikythera.generator.TestGenerator;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.depsolver.ClassProcessor;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.IOException;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TestSpringEvaluator {
    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap();
    }

    @Test
    void testSimpleController() throws IOException, AntikytheraException, ReflectiveOperationException {
        ClassProcessor cp = new ClassProcessor();
        cp.compile( AbstractCompiler.classToPath("sa.com.cloudsolutions.controller.SimpleController"));

        ClassProcessor cp1 = new ClassProcessor();
        cp1.compile( AbstractCompiler.classToPath("sa.com.cloudsolutions.dto.MediumDTO"));

        ClassProcessor cp2 = new ClassProcessor();
        cp2.compile( AbstractCompiler.classToPath("sa.com.cloudsolutions.dto.Constants"));

        CompilationUnit cu = cp.getCompilationUnit();
        SpringEvaluator eval = new SpringEvaluator("sa.com.cloudsolutions.controller.SimpleController");

        eval.executeMethod(cu.findFirst(MethodDeclaration.class).get());
        assertNull(eval.returnValue);

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
        verify(mockGenerator).createTests(eq(methodDecl), eq(response));

        // Verify result
        assertNotNull(result);
        assertTrue(result.getValue() instanceof MethodResponse);
        assertEquals(response, result.getValue());
    }
}
