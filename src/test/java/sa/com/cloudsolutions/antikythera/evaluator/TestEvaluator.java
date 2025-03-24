package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.finch.Finch;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestEvaluator extends TestHelper {

    public static final String CLASS_UNDER_TEST = "sa.com.cloudsolutions.antikythera.evaluator.KitchenSink";

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void each() {
        evaluator = new Evaluator(CLASS_UNDER_TEST);
    }

    @Test
    void evaluateExpressionReturnsIntegerLiteral() throws AntikytheraException, ReflectiveOperationException {
        Evaluator evaluator = new Evaluator("");
        Expression expr = new IntegerLiteralExpr(42);
        Variable result = evaluator.evaluateExpression(expr);
        assertEquals(42, result.getValue());
    }

    @Test
    void evaluateExpressionReturnsStringLiteral() throws AntikytheraException, ReflectiveOperationException {
        Evaluator evaluator = new Evaluator("");
        Expression expr = new StringLiteralExpr("test");
        Variable result = evaluator.evaluateExpression(expr);
        assertEquals("test", result.getValue());
    }

    @Test
    void evaluateExpressionReturnsVariableValue() throws AntikytheraException, ReflectiveOperationException {
        Evaluator evaluator = new Evaluator("");
        Variable expected = new Variable(42);
        evaluator.setLocal(new IntegerLiteralExpr(42), "testVar", expected);
        Expression expr = new NameExpr("testVar");
        Variable result = evaluator.evaluateExpression(expr);
        assertEquals(expected, result);
    }

    @Test
    void evaluateBinaryExpression() throws AntikytheraException, ReflectiveOperationException {
        Evaluator evaluator = new Evaluator("");
        Variable result = evaluator.evaluateBinaryExpression(BinaryExpr.Operator.PLUS,
                new IntegerLiteralExpr("40"), new IntegerLiteralExpr("2"));
        assertEquals(42, result.getValue());

        result = evaluator.evaluateBinaryExpression(BinaryExpr.Operator.PLUS,
                new DoubleLiteralExpr("1.0"), new DoubleLiteralExpr("2.0"));
        assertEquals(3.0, result.getValue());

        result = evaluator.evaluateBinaryExpression(BinaryExpr.Operator.PLUS,
                new StringLiteralExpr("40"), new StringLiteralExpr("2.0"));
        assertEquals("402.0", result.getValue());
    }

    @Test
    void evaluateMethodCallPrintsToSystemOut() throws AntikytheraException, ReflectiveOperationException {
        Evaluator evaluator = new Evaluator("");
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        MethodCallExpr methodCall = new MethodCallExpr(new FieldAccessExpr(new NameExpr("System"), "out"), "println", NodeList.nodeList(new StringLiteralExpr("Hello World")));
        evaluator.evaluateMethodCall(methodCall);
        assertTrue(outContent.toString().contains("Hello World"));
        System.setOut(System.out);
    }

    @Test
    void executeViaDataAnnotation() throws ReflectiveOperationException {
        Evaluator evaluator = new Evaluator("");

        // Create a class declaration with @Data annotation
        ClassOrInterfaceDeclaration classDecl = new ClassOrInterfaceDeclaration()
            .addAnnotation("Data")
            .setName("TestClass");


        annotationHelper(evaluator, classDecl);
    }

    @Test
    void executeViaGetterSetterAnnotation() throws ReflectiveOperationException {
        Evaluator evaluator = new Evaluator("");

        // Create a class declaration with @Data annotation
        ClassOrInterfaceDeclaration classDecl = new ClassOrInterfaceDeclaration()
                .addAnnotation("Setter").addAnnotation("Getter")
                .setName("TestClass");


        annotationHelper(evaluator, classDecl);
    }

    private static void annotationHelper(Evaluator evaluator, ClassOrInterfaceDeclaration classDecl) throws ReflectiveOperationException {
        MethodCallExpr getterCall = new MethodCallExpr()
            .setName("getName");

        Variable nameVar = new Variable("test value");
        evaluator.getFields().put("name", nameVar);

        Variable result = evaluator.executeViaDataAnnotation(classDecl, getterCall);
        assertEquals("test value", result.getValue());

        MethodCallExpr setterCall = new MethodCallExpr()
            .setName("setName")
            .addArgument(new StringLiteralExpr("new value"));

        evaluator.executeViaDataAnnotation(classDecl, setterCall);
        assertEquals("new value", evaluator.getFields().get("name").getValue());
    }

    @Test
    void testResolveNonPrimitiveFields()  {

        Map<String, Variable> resolvedFields = evaluator.getFields();

        assertNotNull(resolvedFields.get("stringList"));
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

}

class TestEvaluatorWithFinches extends TestHelper {

    @BeforeAll
    static void setup() throws IOException, ClassNotFoundException {
        Settings.loadConfigMap(new File("src/test/resources/finches.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @Test
    void testFinching() throws ClassNotFoundException {
        Finch.clear();
        new Evaluator("");
        assertNotNull(Finch.getFinch("sa.com.cloudsolutions.Hello"));
    }

    @Test
    void testResolveNonPrimitiveField() {
        String cls = """
                import sa.com.cloudsolutions.Hello;
                
                class TestClass {
                    Hello hello;
                }
                """;
        CompilationUnit cu = StaticJavaParser.parse(cls);
        AntikytheraRunTime.addClass("TestClass", cu);

        Finch.clear();
        Evaluator eval = new Evaluator("TestClass");

        Variable v = eval.getFields().get("hello");
        assertNotNull(v);
    }

}
