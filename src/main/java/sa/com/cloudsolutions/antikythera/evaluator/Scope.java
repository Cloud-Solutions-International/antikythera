package sa.com.cloudsolutions.antikythera.evaluator;


import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.MCEWrapper;

public class Scope {
    Expression expression;
    MethodCallExpr scopedMethodCall;
    Variable variable;
    private MCEWrapper mceWrapper;
    private TypeWrapper typeWrapper;
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
        this.mceWrapper = wrapper;
    }

    public MCEWrapper getMCEWrapper() {
        return mceWrapper;
    }

    public ScopeChain getScopeChain() {
        return chain;
    }

    public void setTypeWrapper(TypeWrapper typeWrapper) {
        this.typeWrapper = typeWrapper;
    }
    public TypeWrapper getTypeWrapper() {
        return typeWrapper;
    }
}
