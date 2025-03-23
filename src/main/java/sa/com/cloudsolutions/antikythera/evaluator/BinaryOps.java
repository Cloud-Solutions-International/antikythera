package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;

public class BinaryOps {

    private BinaryOps() {

    }
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

        Object leftVal = left.getValue();
        Object rightVal = right.getValue();

        if (leftVal == rightVal) {
            return new Variable(Boolean.TRUE);
        }

        if (leftVal instanceof Number && rightVal instanceof Number) {
            return new Variable(NumericComparator.compare(leftVal, rightVal) == 0);
        }

        return new Variable(leftVal.equals(rightVal));
    }

    static Variable binaryOps(BinaryExpr.Operator operator, Expression leftExpression, Expression rightExpression, Variable left, Variable right) {
        return switch (operator) {
            case EQUALS -> BinaryOps.checkEquality(left, right);

            case AND -> new Variable((boolean) left.getValue() && (boolean) right.getValue());

            case GREATER -> {
                if (left.getValue() instanceof Number && right.getValue() instanceof Number) {
                    yield new Variable(NumericComparator.compare(left.getValue(), right.getValue()) > 0);
                }
                throw new EvaluatorException(leftExpression, rightExpression);
            }

            case GREATER_EQUALS -> {
                if (left.getValue() instanceof Number && right.getValue() instanceof Number) {
                    yield new Variable(NumericComparator.compare(left.getValue(), right.getValue()) >= 0);
                }
                throw new EvaluatorException(leftExpression, rightExpression);
            }

            case LESS -> {
                if (left.getValue() instanceof Number && right.getValue() instanceof Number) {
                    yield new Variable(NumericComparator.compare(left.getValue(), right.getValue()) < 0);
                }
                throw new EvaluatorException(leftExpression, rightExpression);
            }

            case LESS_EQUALS -> {
                if (left.getValue() instanceof Number && right.getValue() instanceof Number) {
                    yield new Variable(NumericComparator.compare(left.getValue(), right.getValue()) <= 0);
                }
                throw new EvaluatorException(leftExpression, rightExpression);
            }

            case NOT_EQUALS -> {
                Variable v = BinaryOps.checkEquality(left, right);
                yield (v.getValue() == null || Boolean.parseBoolean(v.getValue().toString())) ?
                    new Variable(Boolean.FALSE) : new Variable(Boolean.TRUE);
            }

            case OR -> {
                if ((left.getClazz().equals(Boolean.class) || left.getClazz().equals(boolean.class))
                        && (right.getClazz().equals(Boolean.class) || right.getClazz().equals(boolean.class))) {
                    yield new Variable((Boolean) left.getValue() || (Boolean) right.getValue());
                }
                yield null;
            }

            case PLUS, MINUS, MULTIPLY, DIVIDE, REMAINDER -> Arithmetics.operate(left, right, operator);

            default -> null;
        };
    }
}
