package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;

public class BinaryOps {
    /**
     * Check that the left and right variables are equals
     * @param left a Variable
     * @param right the other Variable
     * @return a Variable holding either Boolean.TRUE or Boolean.FALSE
     */
    static Variable checkEquality(Variable left, Variable right) {
        if (left == null) {
            if (right == null || right.getValue() == null) {
                return new Variable(Boolean.TRUE);
            }
            return new Variable(Boolean.FALSE);
        }
        if (right == null) {
            if (left.getValue() == null) {
                return new Variable(Boolean.TRUE);
            }
            return new Variable(Boolean.FALSE);
        }
        if (left.getValue() == right.getValue()) {
            return new Variable(Boolean.TRUE);
        }
        return new Variable(left.getValue().equals(right.getValue()));
    }

    static Variable binaryOps(BinaryExpr.Operator operator, Expression leftExpression, Expression rightExpression, Variable left, Variable right) {
        switch (operator) {
            case EQUALS:
                return BinaryOps.checkEquality(left, right);

            case AND:
                return new Variable((boolean) left.getValue() && (boolean) right.getValue());

            case GREATER:
                if (left.getValue() instanceof Number && right.getValue() instanceof Number) {
                    return new Variable(NumericComparator.compare(left.getValue(), right.getValue()) > 0);
                }
                throw new EvaluatorException("Cannot compare " + leftExpression + " and " + rightExpression);

            case GREATER_EQUALS:
                if (left.getValue() instanceof Number && right.getValue() instanceof Number) {
                    return new Variable(NumericComparator.compare(left.getValue(), right.getValue()) >= 0);
                }
                throw new EvaluatorException("Cannot compare " + leftExpression + " and " + rightExpression);

            case LESS:
                if (left.getValue() instanceof Number && right.getValue() instanceof Number) {
                    return new Variable(NumericComparator.compare(left.getValue(), right.getValue()) < 0);
                }
                throw new EvaluatorException("Cannot compare " + leftExpression + " and " + rightExpression);

            case LESS_EQUALS:
                if (left.getValue() instanceof Number && right.getValue() instanceof Number) {
                    return new Variable(NumericComparator.compare(left.getValue(), right.getValue()) <= 0);
                }
                throw new EvaluatorException("Cannot compare " + leftExpression + " and " + rightExpression);

            case NOT_EQUALS:
                Variable v = BinaryOps.checkEquality(left, right);
                if (v.getValue() == null || Boolean.parseBoolean(v.getValue().toString())) {
                    return new Variable(Boolean.FALSE);
                }
                return new Variable(Boolean.TRUE);

            case OR:
                if (  (left.getClazz().equals(Boolean.class) || left.getClazz().equals(boolean.class))
                        && (right.getClazz().equals(Boolean.class) || right.getClazz().equals(boolean.class))) {
                    return new Variable((Boolean) left.getValue() || (Boolean) right.getValue());
                }
                return null;

            case PLUS:
            case MINUS:
            case MULTIPLY:
            case DIVIDE:
            case REMAINDER:
                return Arithmetics.operate(left, right, operator);

            default:
                return null;
        }
    }
}
