package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.Expression;
import sa.com.cloudsolutions.antikythera.parser.Callable;

public class Precondition {
    Expression expression;
    Mock mock;

    public Precondition(Expression expr) {
        this.expression = expr;
    }

    public Precondition(Mock mock) {
        this.mock = mock;
    }

    public Expression getExpression() {
        return expression;
    }

    public static class Mock {
        Callable callable;
        String className;
        Object returnValue;
        public Mock(Callable callable, String className, Object returnValue) {
            this.callable = callable;
            this.className = className;
            this.returnValue = returnValue;
        }
    }
}
