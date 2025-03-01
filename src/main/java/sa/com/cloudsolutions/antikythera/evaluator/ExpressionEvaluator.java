package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import sa.com.cloudsolutions.antikythera.parser.MCEWrapper;

import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;

public interface ExpressionEvaluator {
    Variable evaluateExpression(Expression expr) throws ReflectiveOperationException;
    CompilationUnit getCompilationUnit();
    void setCompilationUnit(CompilationUnit compilationUnit);
    void executeConstructor(CallableDeclaration<?> md) throws ReflectiveOperationException;
    void setupFields(CompilationUnit cu);
    Map<String, Variable> getFields();
    Variable executeMethod(CallableDeclaration<?> cd) throws ReflectiveOperationException;
    Variable executeMethod(MCEWrapper wrapper)  throws ReflectiveOperationException;
    void setLocal(Node node, String nameAsString, Variable v);

    /**
     * People have a nasty habit of chaining a sequence of method calls.
     *
     * If you are a D3.js programmer, this is probably the only way you do things. Even
     * Byte Buddy seems to behave the same. But at the end of the day how so you handle this?
     * You need to place them in a stack and pop them off one by one!
     *
     * @param expr
     * @return
     */
    public static LinkedList<Expression> findScopeChain(Expression expr) {
        LinkedList<Expression> chain = new LinkedList<>();
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

}
