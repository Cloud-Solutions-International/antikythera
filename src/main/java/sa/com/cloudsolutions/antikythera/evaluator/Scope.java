package sa.com.cloudsolutions.antikythera.evaluator;


import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import sa.com.cloudsolutions.antikythera.parser.Callable;
import sa.com.cloudsolutions.antikythera.parser.MCEWrapper;

public class Scope {
    Expression expression;
    Callable callable;
    MethodCallExpr scopedMethodCall;
    Variable variable;
    private MCEWrapper wrapper;

    Scope(Expression expression) {
        this.expression = expression;
    }

    public Expression getExpression() {
        return expression;
    }

    public Callable getCallable() {
        return callable;
    }

    public void setCallable(Callable callable) {
        this.callable = callable;
    }

    public MethodCallExpr getScopedMethodCall() {
        return scopedMethodCall;
    }

    public void setScopedMethodCall(MethodCallExpr scopedMethodCall) {
        this.scopedMethodCall = scopedMethodCall;
    }

    public Variable getVariable() {
        return variable;
    }

    public void setVariable(Variable variable) {
        this.variable = variable;
    }

    public void setMCEWrapper(MCEWrapper wrapper) {
        this.wrapper = wrapper;
    }

    public MCEWrapper getMCEWrapper() {
        return wrapper;
    }
}
