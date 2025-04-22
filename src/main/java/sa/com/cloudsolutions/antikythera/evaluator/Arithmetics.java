package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.BinaryExpr;

public class Arithmetics {

    private Arithmetics() {

    }
    /**
     * Simple arithmetic operations.
     * String can be added to anything, but numbers are trickier.
     * @param left the left operand
     * @param right the right operand
     * @return the result of the add operation which may be arithmetic or string concatenation
     */
    static Variable operate(Variable left, Variable right, BinaryExpr.Operator operator) {
        if (left.getValue() instanceof String || right.getValue() instanceof String) {
            return new Variable(left.getValue().toString() + right.getValue().toString());
        }
        if (left.getValue() instanceof Number l && right.getValue() instanceof Number r) {
            Number result = performOperation(l, r, operator);

            if (l instanceof Double || r instanceof Double) {
                return new Variable(result.doubleValue());
            } else if (l instanceof Float || r instanceof Float) {
                return new Variable(result.floatValue());
            } else if (l instanceof Long || r instanceof Long) {
                return new Variable(result.longValue());
            } else if (l instanceof Integer || r instanceof Integer) {
                return new Variable(result.intValue());
            } else if (l instanceof Short || r instanceof Short) {
                return new Variable(result.shortValue());
            } else if (l instanceof Byte || r instanceof Byte) {
                return new Variable(result.byteValue());
            }
        }
        return null;
    }

    static Number performOperation(Number left, Number right, BinaryExpr.Operator operator) {
        return switch (operator) {
            case PLUS -> left.doubleValue() + right.doubleValue();
            case MINUS -> left.doubleValue() - right.doubleValue();
            case DIVIDE -> left.doubleValue() / right.doubleValue();
            case MULTIPLY -> left.doubleValue() * right.doubleValue();
            case REMAINDER -> left.doubleValue() % right.doubleValue();
            default ->
                    throw new IllegalArgumentException("Unsupported operator: " + operator);
        };
    }
}
