package sa.com.cloudsolutions.antikythera.evaluator.mock;

import com.github.javaparser.ast.expr.Expression;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;

public class MockingCall {
    private boolean fromSetup;
    private Expression expression;
    private Variable variable;

    public MockingCall(Variable variable) {
        this.variable = variable;
    }

    public boolean isFromSetup() {
        return fromSetup;
    }

    public void setFromSetup(boolean fromSetup) {
        this.fromSetup = fromSetup;
    }

    public Expression getExpression() {
        return expression;
    }

    public void setExpression(Expression expression) {
        this.expression = expression;
    }

    public Variable getVariable() {
        return variable;
    }

    public void setVariable(Variable variable) {
        this.variable = variable;
    }
}
