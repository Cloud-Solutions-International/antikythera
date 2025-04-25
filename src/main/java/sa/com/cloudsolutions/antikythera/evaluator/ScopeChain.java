package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import sa.com.cloudsolutions.antikythera.parser.Callable;
import sa.com.cloudsolutions.antikythera.parser.MCEWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ScopeChain {
    List<Scope> chain = new ArrayList<>();
    Expression expression;

    private ScopeChain(Expression expression) {
        this.expression = expression;
    }

    /**
     * <p>People have a nasty habit of chaining a sequence of method calls.</p>
     *
     * If you are a D3.js programmer, this is probably the only way you do things. Even
     * Byte Buddy seems to behave the same. But at the end of the day how so you handle this?
     * You need to place them in a stack and pop them off one by one!
     *
     * @param expr the expression for which we need to determine the scope change
     * @return the scope chain
     */
    public static ScopeChain findScopeChain(Expression expr) {
        ScopeChain chain = new ScopeChain(expr);

        while (expr != null) {
            if (expr.isMethodCallExpr()) {
                MethodCallExpr mce = expr.asMethodCallExpr();
                Optional<Expression> scopeD = mce.getScope();
                if (scopeD.isEmpty()) {
                    break;
                }
                chain.addLast(scopeD.get());
                expr = scopeD.get();
            }
            else if (expr.isFieldAccessExpr()) {
                FieldAccessExpr mce = expr.asFieldAccessExpr();
                chain.addLast(mce.getScope());
                expr = mce.getScope();
            }
            else {
                expr = null;
            }
        }
        return chain;
    }

    private void addLast(Expression expressions) {
        chain.addLast(new Scope(expressions));
    }

    public boolean isEmpty() {
        return chain.isEmpty();
    }

    public List<Scope> getChain() {
        return chain;
    }

    public Expression getExpression() {
        return expression;
    }

}
