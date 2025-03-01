package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.generator.MethodResponse;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.depsolver.ClassProcessor;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        // Test with NameExpr and different Variable types
        NameExpr nameExpr = new NameExpr("testField");

        // Test with a Variable having a Type
        ClassOrInterfaceType stringType = StaticJavaParser.parseClassOrInterfaceType("String");
        Variable typeVariable = new Variable("test");
        typeVariable.setType(stringType);
        evaluator.getFields().put("testField", typeVariable);
        assertEquals("String", evaluator.getFieldClass(nameExpr));

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
}
