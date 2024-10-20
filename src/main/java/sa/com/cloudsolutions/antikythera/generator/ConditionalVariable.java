package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.expr.Expression;

public class ConditionalVariable {
    Expression expression;
    Object lower;
    Object upper;

    ConditionalVariable(Expression expr) {
        this.expression = expr;
    }

    public Expression getExpression() {
        return expression;
    }

    public void setExpression(Expression expression) {
        this.expression = expression;
    }

    public Object getLower() {
        return lower;
    }

    public void setLower(Object lower) {
        this.lower = lower;
    }

    public Object getUpper() {
        return upper;
    }

    public void setUpper(Object upper) {
        this.upper = upper;
    }
}
