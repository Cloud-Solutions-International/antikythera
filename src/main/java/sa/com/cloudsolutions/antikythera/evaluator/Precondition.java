package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.Expression;

public class Precondition {
    Expression expression;

    public Precondition(Expression expr) {
        this.expression = expr;
    }

    public Expression getExpression() {
        return expression;
    }

}
