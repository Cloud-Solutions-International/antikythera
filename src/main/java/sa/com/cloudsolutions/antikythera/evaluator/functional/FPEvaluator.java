package sa.com.cloudsolutions.antikythera.evaluator.functional;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.UnknownType;
import com.github.javaparser.ast.type.VoidType;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;

import java.util.Map;

public abstract class FPEvaluator<T> extends Evaluator {
    protected MethodDeclaration methodDeclaration;
    protected Evaluator enclosure;
    Expression expr;

    public FPEvaluator(String className) {
        super(className);
    }

    public static Variable create(LambdaExpr lambda, Evaluator enclosure) {
        LambdaExpr lambdaExpr = lambda.clone();
        lambdaExpr.setParentNode(lambda.getParentNode().orElseThrow());

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
        md.setType(new VoidType());

        if (lambdaExpr.getBody().findFirst(ReturnStmt.class).isPresent()) {
            md.setType(new ClassOrInterfaceType("Object"));
        } else {
            if (isReturning(lambdaExpr)) {
                md.setType(new ClassOrInterfaceType("Object"));
                Statement last = body.getStatements().get(body.getStatements().size() - 1);
                addReturnStatement(body, last);
            }
        }

        for (Parameter param : lambdaExpr.getParameters()) {
            md.addParameter(param);
            if (param.getType() instanceof UnknownType) {
                param.setType("Object");
            }
        }

        FPEvaluator<?> fp = createEvaluator(md);
        fp.enclosure = enclosure;
        fp.expr = lambda;
        Variable v = new Variable(fp);
        v.setType(fp.getType());
        return v;
    }

    private static FPEvaluator<?> createEvaluator(MethodDeclaration md) {
        if (md.getBody().orElseThrow().findFirst(ReturnStmt.class).isPresent()) {
            FPEvaluator<?> eval = switch (md.getParameters().size()) {
                case 0 -> new SupplierEvaluator<>("java.util.function.Supplier");
                case 1 -> new FunctionEvaluator<>("java.util.function.Function");
                case 2 -> new BiFunctionEvaluator<>("java.util.function.BiFunction");
                default -> null;
            };
            eval.setMethod(md);
            return eval;
        } else {
            FPEvaluator<?> eval = switch (md.getParameters().size()) {
                case 0 -> new RunnableEvaluator("java.lang.Runnable");
                case 1 -> new ConsumerEvaluator<>("java.util.function.Consumer");
                case 2 -> new BiConsumerEvaluator<>("java.util.function.BiConsumer");
                default -> null;
            };

            eval.setMethod(md);
            return eval;
        }
    }

    private static boolean isReturning(LambdaExpr lambdaExpr) {
        /*
         * There are two kinds of lambdas; those that contain a block and those that don't
         *
         * If you have a block statement and you are supposed to return something, you are need an
         * explicit return statement. So those are pretty easy to spot, just look at the last line
         * of the block.
         *
         * Those without a block statement are trickier. The simpliest approach is to look at the
         * outer method and see if it is one of the usual suspects that are supposed to return
         * a value.
         */
        if (lambdaExpr.getParentNode().isPresent()) {
            if (!lambdaExpr.getBody().isBlockStmt()) {
                return true;
            }
            if (lambdaExpr.getParentNode().get() instanceof MethodCallExpr mce) {
                String name = mce.getNameAsString();
                return switch (name) {
                    case "map", "filter", "sorted", "reduce", "anyMatch", "allMatch", "noneMatch",
                         "findFirst", "findAny" -> true;
                    default -> false;
                };
            }
        }
        return false;
    }

    private static void addReturnStatement(BlockStmt body, Statement last) {
        body.remove(last);
        ReturnStmt returnStmt = new ReturnStmt();
        returnStmt.setExpression(last.asExpressionStmt().getExpression());
        body.addStatement(returnStmt);
    }

    public void setMethod(MethodDeclaration methodDeclaration) {
        this.methodDeclaration = methodDeclaration;
    }

    @Override
    public Variable executeLocalMethod(MethodCallExpr methodCall) throws ReflectiveOperationException {
        returnFrom = null;
        if (methodCall.getNameAsString().equals("apply") || methodCall.getNameAsString().equals("accept")) {
            wrapCallExpression(methodCall);
            return executeMethod(methodDeclaration);
        }
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Variable getValue(Node n, String name) {
        Variable v = super.getValue(n, name);
        if (v == null) {
            v = enclosure.getValue(expr.getParentNode().get(), name);
            if (v != null) {
                return v;
            }
            for (Map<String, Variable> local : enclosure.getLocals().values()) {
                v = local.get(name);
                if (v != null) {
                    return v;
                }
            }
        }
        return v;
    }

    public abstract Type getType();
}
