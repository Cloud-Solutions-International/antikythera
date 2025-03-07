package sa.com.cloudsolutions.antikythera.evaluator.functional;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.UnknownType;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;

import java.util.LinkedList;
import java.util.Optional;
import java.util.function.Function;

public class FunctionEvaluator<T,R> extends FunctionalEvaluator implements Function<T,R> {
    public FunctionEvaluator(String className) {
        super(className);
    }

    public static FunctionalEvaluator create(LambdaExpr lambdaExpr, Evaluator enclosure) {
        // Create a synthetic method from the lambda
        MethodDeclaration md = new MethodDeclaration();

        BlockStmt body;
        if (lambdaExpr.getBody().isBlockStmt()) {
            body = lambdaExpr.getBody().asBlockStmt();
            md.setBody(body);
        } else {
            body = new BlockStmt();
            body.addStatement(lambdaExpr.getBody());
            md.setBody(body);
        }
        md.setType(new UnknownType());

        if (!body.findFirst(ReturnStmt.class).isPresent()) {
            Statement last = body.getStatements().get(body.getStatements().size() - 1);
            if (last.isExpressionStmt()) {
                Expression expr = last.asExpressionStmt().getExpression();
                if (expr.isMethodCallExpr()) {
                    LinkedList<Expression> chain = Evaluator.findScopeChain(expr.asMethodCallExpr());
                    if (chain.isEmpty()) {
                        /*
                         * We are only concerned about finding the return type here so we don't
                         * need to bother with overloading. All overloaded methods are required to
                         * have the same return type.
                         */
                        CompilationUnit cu = enclosure.getCompilationUnit();
                        if (cu != null) {
                            cu.findFirst(MethodDeclaration.class
                                    , decl -> md.getNameAsString().equals(expr.asMethodCallExpr().getNameAsString())
                            ).ifPresent(mm ->
                                addReturnStatement(body, last)
                            );
                        }
                    }
                    else {

                    }
                }
                else {
                    addReturnStatement(body, last);
                }
            }
        }

        lambdaExpr.getParameters().forEach(md::addParameter);
        FunctionEvaluator eval = new FunctionEvaluator("functional");
        eval.setMethod(md);
        return eval;
    }

    private static void addReturnStatement(BlockStmt body, Statement last) {
        body.remove(last);
        ReturnStmt returnStmt = new ReturnStmt();
        returnStmt.setExpression(last.asExpressionStmt().getExpression());
        body.addStatement(returnStmt);
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
}
