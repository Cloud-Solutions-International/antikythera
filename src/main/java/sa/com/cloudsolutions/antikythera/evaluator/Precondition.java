package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.Expression;

import java.util.Objects;

/**
 * Wraps a conditional {@link Expression} that must hold true before a particular
 * branch is entered, used during test precondition generation.
 */
public class Precondition {
    Expression expression;

    public Precondition(Expression expr) {
        this.expression = expr;
    }

    public Expression getExpression() {
        return expression;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Precondition that = (Precondition) o;
        return Objects.equals(expression, that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(expression) + 23;
    }
}
