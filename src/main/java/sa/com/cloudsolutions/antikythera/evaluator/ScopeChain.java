package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import sa.com.cloudsolutions.antikythera.parser.Callable;

import java.util.LinkedList;
import java.util.Optional;

public class ScopeChain {
    LinkedList<Scope> scopeChain = new LinkedList<>();

    private ScopeChain(){}

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
        ScopeChain chain = new ScopeChain();

        while (true) {
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
            else if (expr.isMethodReferenceExpr()) {
                MethodReferenceExpr mexpr = expr.asMethodReferenceExpr();
                chain.addLast(mexpr.getScope());
                expr = mexpr.getScope();
            }
            else {
                break;
            }
        }
        return chain;
    }

    private void addLast(Expression expressions) {
        scopeChain.addLast(new Scope(expressions));
    }

    public boolean isEmpty() {
        return scopeChain.isEmpty();
    }

    public Scope pollLast() {
        return scopeChain.pollLast();
    }

    public Scope getFirst() {
        return scopeChain.getFirst();
    }

    public static class Scope {
        Expression expression;
        Callable callable;

        private Scope(Expression expression) {
            this.expression = expression;
        }

        public Expression getExpression() {
            return expression;
        }

        public void setExpression(Expression expression) {
            this.expression = expression;
        }

        public Callable getCallable() {
            return callable;
        }

        public void setCallable(Callable callable) {
            this.callable = callable;
        }
    }
}
