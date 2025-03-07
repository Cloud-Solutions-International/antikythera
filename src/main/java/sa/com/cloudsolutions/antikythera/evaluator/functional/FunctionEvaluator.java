package sa.com.cloudsolutions.antikythera.evaluator.functional;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.FunctionalEvaluator;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;

import java.util.function.Function;

public class FunctionEvaluator<T,R> extends FunctionalEvaluator implements Function<T,R> {
    public FunctionEvaluator(String className) {
        super(className);
    }

    @Override
    public R apply(T t) {
        AntikytheraRunTime.push(new Variable(t));
        try {
            Variable v = executeMethod(methodDeclaration);
            return (R) v.getValue();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <V> Function<V, R> compose(Function<? super V, ? extends T> before) {
        return Function.super.compose(before);
    }

    @Override
    public <V> Function<T, V> andThen(Function<? super R, ? extends V> after) {
        return Function.super.andThen(after);
    }

    @Override
    public void setMethod(MethodDeclaration methodDeclaration) {
        super.setMethod(methodDeclaration);
        methodDeclaration.getBody().ifPresent(body -> {
            if (!body.findFirst(ReturnStmt.class).isPresent()) {
                Statement last = body.getStatements().get(body.getStatements().size() - 1);
                body.remove(last);
                if (last.isExpressionStmt()) {
                    ReturnStmt returnStmt = new ReturnStmt();
                    returnStmt.setExpression(last.asExpressionStmt().getExpression());
                    body.addStatement(returnStmt);
                }
            }
        });
    }
}
