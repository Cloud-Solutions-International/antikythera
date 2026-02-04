package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.type.Type;

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
        Object leftVal = left != null ? left.getValue() : null;
        Object rightVal = right != null ? right.getValue() : null;

        if (operator == BinaryExpr.Operator.PLUS && (isStringOperand(left, leftVal) || isStringOperand(right, rightVal))) {
            String l = leftVal == null ? "null" : leftVal.toString();
            String r = rightVal == null ? "null" : rightVal.toString();
            return new Variable(l + r);
        }

        if (leftVal instanceof Number l && rightVal instanceof Number r) {
            return createNumericVariable(operator, l, r);
        }
        return null;
    }

    private static Variable createNumericVariable(BinaryExpr.Operator operator, Number l, Number r) {
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

    private static boolean isStringOperand(Variable variable, Object runtimeValue) {
        if (runtimeValue instanceof String) {
            return true;
        }
        if (variable == null) {
            return false;
        }
        Class<?> declaredClass = variable.getClazz();
        if (declaredClass != null && String.class.isAssignableFrom(declaredClass)) {
            return true;
        }
        Type declaredType = variable.getType();
        if (declaredType != null) {
            String typeName = declaredType.asString();
            return "String".equals(typeName) || String.class.getName().equals(typeName);
        }
        return false;
    }
}
