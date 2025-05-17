package sa.com.cloudsolutions.antikythera.evaluator.mock;

import com.github.javaparser.ast.expr.Expression;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.parser.Callable;

import java.util.List;

public class MockingCall {
    private boolean fromSetup;
    private List<Expression> expression;
    private Variable variable;
    private final Callable callable;
    private String variableName;

    public MockingCall(Callable callable, Variable variable)
    {
        this.callable = callable;
        this.variable = variable;
    }

    public boolean isFromSetup() {
        return fromSetup;
    }

    public void setFromSetup(boolean fromSetup) {
        this.fromSetup = fromSetup;
    }

    public List<Expression> getExpression() {
        return expression;
    }

    public void setExpression(List<Expression> expression) {
        this.expression = expression;
    }

    public Variable getVariable() {
        return variable;
    }

    public void setVariable(Variable variable) {
        this.variable = variable;
    }

    public Callable getCallable() {
        return callable;
    }

    public String getVariableName() {
        return variableName;
    }

    public void setVariableName(String variableName) {
        this.variableName = variableName;
    }
}
