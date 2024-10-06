package sa.com.cloudsolutions.antikythera.evaluator;

import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestEvaluator {

    CompilationUnit dto = StaticJavaParser.parse(getClass().getClassLoader().getResourceAsStream(
            "sources/src/main/java/sa/com/cloudsolutions/dto/SimpleDTO.java"));
    CompilationUnit exp = StaticJavaParser.parse(getClass().getClassLoader().getResourceAsStream(
            "sources/src/main/java/sa/com/cloudsolutions/expressions/SimpleDTOExpressions.java"));


    @Test
    void evaluateExpressionReturnsIntegerLiteral() throws AntikytheraException, ReflectiveOperationException {
        Evaluator evaluator = new Evaluator();
        Expression expr = new IntegerLiteralExpr(42);
        Variable result = evaluator.evaluateExpression(expr);
        assertEquals(42, result.getValue());
    }

    @Test
    void evaluateExpressionReturnsStringLiteral() throws AntikytheraException, ReflectiveOperationException {
        Evaluator evaluator = new Evaluator();
        Expression expr = new StringLiteralExpr("test");
        Variable result = evaluator.evaluateExpression(expr);
        assertEquals("test", result.getValue());
    }

    @Test
    void evaluateExpressionReturnsVariableValue() throws AntikytheraException, ReflectiveOperationException {
        Evaluator evaluator = new Evaluator();
        Variable expected = new Variable(42);
        evaluator.setLocal(new IntegerLiteralExpr(42), "testVar", expected);
        Expression expr = new NameExpr("testVar");
        Variable result = evaluator.evaluateExpression(expr);
        assertEquals(expected, result);
    }

    @Test
    void evaluateBinaryExpression() throws AntikytheraException, ReflectiveOperationException {
        Evaluator evaluator = new Evaluator();
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
        Evaluator evaluator = new Evaluator();
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        MethodCallExpr methodCall = new MethodCallExpr(new FieldAccessExpr(new NameExpr("System"), "out"), "println", NodeList.nodeList(new StringLiteralExpr("Hello World")));
        evaluator.evaluateMethodCall(methodCall);
        assertTrue(outContent.toString().contains("Hello World"));
        System.setOut(System.out);
    }
}
