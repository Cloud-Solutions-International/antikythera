package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;
import sa.com.cloudsolutions.antikythera.finch.Finch;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestEvaluator extends TestHelper {

    public static final String CLASS_UNDER_TEST = "sa.com.cloudsolutions.antikythera.testhelper.evaluator.KitchenSink";

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
    void evaluateExpressionReturnsIntegerLiteral() throws AntikytheraException, ReflectiveOperationException {
        Evaluator eval = EvaluatorFactory.create("", Evaluator.class);
        Expression expr = new IntegerLiteralExpr("42");
        Variable result = eval.evaluateExpression(expr);
        assertEquals(42, result.getValue());
    }

    @Test
    void evaluateExpressionReturnsStringLiteral() throws AntikytheraException, ReflectiveOperationException {
        Evaluator eval = EvaluatorFactory.create("", Evaluator.class);
        Expression expr = new StringLiteralExpr("test");
        Variable result = eval.evaluateExpression(expr);
        assertEquals("test", result.getValue());
    }

    @Test
    void evaluateExpressionReturnsVariableValue() throws AntikytheraException, ReflectiveOperationException {
        Evaluator eval = EvaluatorFactory.create("", Evaluator.class);
        Variable expected = new Variable(42);
        eval.setLocal(new IntegerLiteralExpr("42"), "testVar", expected);
        Expression expr = new NameExpr("testVar");
        Variable result = eval.evaluateExpression(expr);
        assertEquals(expected, result);
    }

    @Test
    void evaluateBinaryExpression() throws AntikytheraException, ReflectiveOperationException {
        Evaluator eval = EvaluatorFactory.create("", Evaluator.class);
        Variable result = eval.evaluateBinaryExpression(BinaryExpr.Operator.PLUS,
                new IntegerLiteralExpr("40"), new IntegerLiteralExpr("2"));
        assertEquals(42, result.getValue());

        result = eval.evaluateBinaryExpression(BinaryExpr.Operator.PLUS,
                new DoubleLiteralExpr("1.0"), new DoubleLiteralExpr("2.0"));
        assertEquals(3.0, result.getValue());

        result = eval.evaluateBinaryExpression(BinaryExpr.Operator.PLUS,
                new StringLiteralExpr("40"), new StringLiteralExpr("2.0"));
        assertEquals("402.0", result.getValue());
    }

    @Test
    void evaluateMethodCallPrintsToSystemOut() throws AntikytheraException, ReflectiveOperationException {
        Evaluator eval = EvaluatorFactory.create("", Evaluator.class);
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        MethodCallExpr methodCall = new MethodCallExpr(new FieldAccessExpr(new NameExpr("System"), "out"),
                "println", NodeList.nodeList(new StringLiteralExpr("Hello World")));
        eval.evaluateMethodCall(methodCall);
        assertTrue(outContent.toString().contains("Hello World"));
        System.setOut(System.out);
    }

    @Test
    void executeViaDataAnnotation() throws ReflectiveOperationException {
        evaluator.setCompilationUnit(evaluator.getCompilationUnit().clone());
        evaluator.getCompilationUnit().getType(0).addAnnotation("Data");
        annotationHelper();
    }

    @Test
    void executeViaGetterSetterAnnotation() throws ReflectiveOperationException {
        evaluator.setCompilationUnit(evaluator.getCompilationUnit().clone());
        evaluator.getCompilationUnit().getType(0).addAnnotation("Getter");
        evaluator.getCompilationUnit().getType(0).addAnnotation("Setter");
        annotationHelper();
    }

    private void annotationHelper() throws ReflectiveOperationException {
        MethodCallExpr getterCall = new MethodCallExpr()
            .setName("getNumber");

        Variable result = evaluator.evaluateMethodCall(getterCall);
        assertEquals(42, result.getValue());

        MethodCallExpr setterCall = new MethodCallExpr()
            .setName("setNumber")
            .addArgument(new IntegerLiteralExpr("43"));

        evaluator.evaluateMethodCall(setterCall);
        result = evaluator.evaluateMethodCall(getterCall);
        assertEquals(43, result.getValue());
    }

    @Test
    void testResolveNonPrimitiveVariable()  {

        Map<String, Symbol> resolvedFields = evaluator.fields;

        assertNull(resolvedFields.get("stringList").getValue());
        assertTrue(resolvedFields.get("stringList").getType().isClassOrInterfaceType());
        assertEquals("List", resolvedFields.get("stringList").getType().asClassOrInterfaceType().getNameAsString());

        assertNotNull(resolvedFields.get("intList"));
        assertTrue(resolvedFields.get("intList").getType().isClassOrInterfaceType());
        assertEquals("ArrayList", resolvedFields.get("intList").getType().asClassOrInterfaceType().getNameAsString());

        assertNotNull(resolvedFields.get("text"));
        assertEquals("test", resolvedFields.get("text").getValue());

        assertNotNull(resolvedFields.get("number"));
        assertEquals(42, resolvedFields.get("number").getValue());
    }

    @Test
    void testSequence() {
        Variable v = evaluator.getField("id");
        assertEquals(43, v.getValue());

        evaluator = EvaluatorFactory.create("sa.com.cloudsolutions.antikythera.testhelper.evaluator.FakeEntity",
                Evaluator.class);
        v = evaluator.getField("id");
        assertTrue(0 < (long) v.getValue());
        assertFalse(v.getInitializer().isEmpty());
        assertTrue(v.getInitializer().getFirst().toString().startsWith("setId("));

        assertEquals(1, evaluator.getFieldInitializers().size());
    }

    @Test
    void evaluateClassExpressionReturnsClassObject1() throws AntikytheraException, ReflectiveOperationException {
        Evaluator eval = EvaluatorFactory.create("", Evaluator.class);

        // Test with a standard Java class
        ClassExpr stringClassExpr = new ClassExpr(StaticJavaParser.parseType("String"));
        Variable result = eval.evaluateClassExpression(stringClassExpr);
        assertNotNull(result);
        assertEquals(String.class, result.getValue());

        // Test with a class from the project
        ClassExpr evaluatorClassExpr = new ClassExpr(StaticJavaParser.parseType("sa.com.cloudsolutions.antikythera.testhelper.evaluator.Evaluator"));
        result = eval.evaluateClassExpression(evaluatorClassExpr);
        assertNull(result);

    }

    @Test
    void evaluateClassExpressionReturnsClassObject2() throws AntikytheraException, ReflectiveOperationException {
        evaluator.getCompilationUnit().addImport("sa.com.cloudsolutions.antikythera.testhelper.evaluator.Evaluator");
        ClassExpr evaluatorClassExpr = new ClassExpr(StaticJavaParser.parseType("sa.com.cloudsolutions.antikythera.testhelper.evaluator.Person"));
        Variable result = evaluator.evaluateClassExpression(evaluatorClassExpr);
        assertNotNull(result);
        assertEquals("sa.com.cloudsolutions.antikythera.testhelper.evaluator.Person", ((Class<?>) result.getValue()).getName());
    }


    @Test
    void evaluateClassExpressionReturnsClassObject3() throws AntikytheraException, ReflectiveOperationException {
        evaluator.getCompilationUnit().addImport("sa.com.cloudsolutions.antikythera.testhelper.evaluator.FakeRepository");
        ClassExpr evaluatorClassExpr = new ClassExpr(StaticJavaParser.parseType("sa.com.cloudsolutions.antikythera.testhelper.evaluator.FakeRepository"));
        Variable result = evaluator.evaluateClassExpression(evaluatorClassExpr);
        assertNotNull(result);
        assertEquals("sa.com.cloudsolutions.antikythera.testhelper.evaluator.FakeRepository", ((Class<?>) result.getValue()).getName());
    }

    @Test
    void validateReflectiveMethodThrowsExceptionWhenMethodIsNull() {
        Variable nullVariable = new Variable(null);
        ReflectionArguments args = new ReflectionArguments("missingMethod", new Object[]{}, new Class[]{});

        EvaluatorException ex = assertThrows(EvaluatorException.class, () -> {
            Evaluator.validateReflectiveMethod(nullVariable, args, null);
        });
        assertEquals("Application NPE: missingMethod", ex.getMessage());

        Variable notNullVariable = new Variable("not null value");
        ex = assertThrows(EvaluatorException.class, () -> {
            Evaluator.validateReflectiveMethod(notNullVariable, args, null);
        });
        assertEquals("Error evaluating method call: missingMethod", ex.getMessage());
    }

}

class TestEvaluatorWithFinches extends TestHelper {

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/finches.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @Test
    void testFinching() {
        Finch.clear();
        EvaluatorFactory.create("", Evaluator.class);
        assertNotNull(Finch.getFinch("sa.com.cloudsolutions.Hello"));
    }

    @Test
    void testResolveNonPrimitiveVariable() {
        String cls = """
                import sa.com.cloudsolutions.Hello;
                
                class TestClass {
                    Hello hello;
                }
                """;
        CompilationUnit cu = StaticJavaParser.parse(cls);
        AntikytheraRunTime.addCompilationUnit("TestClass", cu);

        Finch.clear();
        Evaluator eval = EvaluatorFactory.create("TestClass", Evaluator.class);

        Variable v = eval.getField("hello");
        assertNotNull(v);
    }

}
