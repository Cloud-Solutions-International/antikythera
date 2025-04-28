package sa.com.cloudsolutions.antikythera.evaluator;


import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import sa.com.cloudsolutions.antikythera.parser.MCEWrapper;

public class Scope {
    Expression expression;
    MethodCallExpr scopedMethodCall;
    Variable variable;
    private MCEWrapper wrapper;
    private final ScopeChain chain;

    Scope(ScopeChain chain, Expression expression)
    {
        this.expression = expression;
        this.chain = chain;
    }

    public Expression getExpression() {
        return expression;
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

    public ScopeChain getScopeChain() {
        return chain;
    }
}
