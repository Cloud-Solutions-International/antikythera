package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.UnaryExpr;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;

import java.util.List;

public class BinaryOps {

    private BinaryOps() {

    }
    /**
     * Check that the left and right variables are equals
     * @param left a Variable
     * @param right the other Variable
     * @return a Variable holding either `Boolean.TRUE` or `Boolean.FALSE`
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

        if (leftVal == null) {
            return new Variable(Boolean.FALSE);
        }

        if (leftVal instanceof Number && rightVal instanceof Number) {
            return new Variable(NumericComparator.compare(leftVal, rightVal) == 0);
        }

        return new Variable(leftVal.equals(rightVal));
    }

    public static Expression negateCondition(Expression condition) {
        if (condition instanceof BinaryExpr binaryExpr) {
            BinaryExpr.Operator newOp = switch (binaryExpr.getOperator()) {
                case EQUALS -> BinaryExpr.Operator.NOT_EQUALS;
                case NOT_EQUALS -> BinaryExpr.Operator.EQUALS;
                case GREATER -> BinaryExpr.Operator.LESS_EQUALS;
                case GREATER_EQUALS -> BinaryExpr.Operator.LESS;
                case LESS -> BinaryExpr.Operator.GREATER_EQUALS;
                case LESS_EQUALS -> BinaryExpr.Operator.GREATER;
                default -> null;
            };

            if (newOp != null) {
                return new BinaryExpr(binaryExpr.getLeft(), binaryExpr.getRight(), newOp);
            }
        }

        return new UnaryExpr(condition, UnaryExpr.Operator.LOGICAL_COMPLEMENT);
    }

    public static Expression getCombinedCondition(List<Expression> conditions) {
        if (conditions.isEmpty()) {
            return null;
        }

        if (conditions.size() == 1) {
            return conditions.getFirst().clone();
        }

        Expression combined = new EnclosedExpr(conditions.get(0));
        for (int i = 1; i < conditions.size(); i++) {
            BinaryExpr.Operator operator = BinaryExpr.Operator.AND;
            Expression right = new EnclosedExpr(conditions.get(i));
            combined = new BinaryExpr(combined, right, operator);
        }

        return combined;
    }
    static Variable binaryOps(BinaryExpr.Operator operator, Expression leftExpression, Expression rightExpression, Variable left, Variable right) {
        return switch (operator) {
            case EQUALS -> BinaryOps.checkEquality(left, right);

            case AND -> new Variable((boolean) left.getValue() && (boolean) right.getValue());

            case GREATER -> {
                if (left.getValue() instanceof Number && right.getValue() instanceof Number) {
                    yield new Variable(NumericComparator.compare(left.getValue(), right.getValue()) > 0);
                }
                if (left.getValue() == null || right.getValue() == null) {
                    throw new NullPointerException();
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
