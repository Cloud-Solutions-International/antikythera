package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;

import static org.junit.jupiter.api.Assertions.*;

class TestBinaryOps {

    @Test
    void testEqualityComparisons() {
        // Null checks
        assertTrue((Boolean) BinaryOps.checkEquality(null, null).getValue());
        assertFalse((Boolean) BinaryOps.checkEquality(new Variable(1), null).getValue());
        assertFalse((Boolean) BinaryOps.checkEquality(null, new Variable(1)).getValue());

        // Same value different objects
        assertTrue((Boolean) BinaryOps.checkEquality(new Variable(42), new Variable(42)).getValue());
        assertTrue((Boolean) BinaryOps.checkEquality(new Variable("test"), new Variable("test")).getValue());

        // Different values
        assertFalse((Boolean) BinaryOps.checkEquality(new Variable(42), new Variable(43)).getValue());
        assertFalse((Boolean) BinaryOps.checkEquality(new Variable("test"), new Variable("test2")).getValue());
    }

    @Test
    void testNumericComparisons() {
        var left = new Variable(10);
        var right = new Variable(5);
        var expr = new IntegerLiteralExpr();

        // Greater than
        assertTrue((Boolean) BinaryOps.binaryOps(BinaryExpr.Operator.GREATER, expr, expr, left, right).getValue());

        // Less than
        assertFalse((Boolean) BinaryOps.binaryOps(BinaryExpr.Operator.LESS, expr, expr, left, right).getValue());

        // Greater equals
        assertTrue((Boolean) BinaryOps.binaryOps(BinaryExpr.Operator.GREATER_EQUALS, expr, expr, left, right).getValue());

        // Less equals
        assertFalse((Boolean) BinaryOps.binaryOps(BinaryExpr.Operator.LESS_EQUALS, expr, expr, left, right).getValue());
    }

    @Test
    void testLogicalOperators() {
        var trueVar = new Variable(true);
        var falseVar = new Variable(false);
        var expr = new IntegerLiteralExpr();

        // AND operator
        assertTrue((Boolean) BinaryOps.binaryOps(BinaryExpr.Operator.AND, expr, expr, trueVar, trueVar).getValue());
        assertFalse((Boolean) BinaryOps.binaryOps(BinaryExpr.Operator.AND, expr, expr, trueVar, falseVar).getValue());

        // OR operator
        assertTrue((Boolean) BinaryOps.binaryOps(BinaryExpr.Operator.OR, expr, expr, trueVar, falseVar).getValue());
        assertFalse((Boolean) BinaryOps.binaryOps(BinaryExpr.Operator.OR, expr, expr, falseVar, falseVar).getValue());
    }

    @Test
    void testArithmeticOperations() {
        var five = new Variable(5);
        var three = new Variable(3);
        var expr = new IntegerLiteralExpr();

        assertEquals(8, BinaryOps.binaryOps(BinaryExpr.Operator.PLUS, expr, expr, five, three).getValue());
        assertEquals(2, BinaryOps.binaryOps(BinaryExpr.Operator.MINUS, expr, expr, five, three).getValue());
        assertEquals(15, BinaryOps.binaryOps(BinaryExpr.Operator.MULTIPLY, expr, expr, five, three).getValue());
        // integer division
        assertEquals(1, BinaryOps.binaryOps(BinaryExpr.Operator.DIVIDE, expr, expr, five, three).getValue());
    }

    @Test
    void testInvalidComparisons() {
        var number = new Variable(5);
        var text = new Variable("test");
        var expr = new StringLiteralExpr();

        assertThrows(EvaluatorException.class, () ->
            BinaryOps.binaryOps(BinaryExpr.Operator.GREATER, expr, expr, number, text)
        );

        assertThrows(EvaluatorException.class, () ->
            BinaryOps.binaryOps(BinaryExpr.Operator.LESS, expr, expr, text, number)
        );
    }
}
