package sa.com.cloudsolutions.antikythera.evaluator.functional;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.UnknownType;
import com.github.javaparser.ast.type.VoidType;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;

import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.Optional;

public abstract class FPEvaluator<T> extends Evaluator {
    protected MethodDeclaration methodDeclaration;
    protected Evaluator enclosure;
    Expression expr;

    public FPEvaluator(String className) {
        super(className);
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

    public static Variable create(LambdaExpr lambdaExpr, Evaluator enclosure) throws ReflectiveOperationException {
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
        md.setType(new VoidType());

        for (Parameter param : lambdaExpr.getParameters()) {
            md.addParameter(param);
            if (param.getType() instanceof UnknownType) {
                param.setType("Object");
            }
        }

        FPEvaluator<?> fp = createEvaluator(enclosure, md);
        fp.enclosure = enclosure;
        fp.expr = lambdaExpr;

        Variable v = new Variable(fp);
        v.setType(fp.getType());
        return v;
    }

    @Override
    public Variable getValue(Node n, String name) {
        Variable v = super.getValue(n, name);
        if (v == null) {
            return enclosure.getValue(expr, name);
        }
        return v;
    }

    private static FPEvaluator<?> createEvaluator(Evaluator enclosure, MethodDeclaration md) throws ReflectiveOperationException {
        if (checkReturnType(enclosure, md) ) {
            FPEvaluator<?> eval = switch (md.getParameters().size()) {
                case 0 -> new SupplierEvaluator<>("java.util.function.Supplier");
                case 1 -> new FunctionEvaluator<>("java.util.function.Function");
                case 2 -> new BiFunctionEvaluator<>("java.util.function.BiFunction");
                default -> null;
            };
            eval.setMethod(md);
            return eval;
        }
        else {
            FPEvaluator<?> eval = switch(md.getParameters().size()) {
                case 0 -> new RunnableEvaluator("java.lang.Runnable");
                case 1 -> new ConsumerEvaluator<>("java.util.function.Consumer");
                case 2 -> new BiConsumerEvaluator<>("java.util.function.BiConsumer");
                default -> null;
            };

            eval.setMethod(md);
            return eval;
        }
    }

    private static boolean checkReturnType(Evaluator enclosure, MethodDeclaration md) throws ReflectiveOperationException {
        BlockStmt body = md.getBody().orElseThrow();
        if (!body.findFirst(ReturnStmt.class).isPresent()) {
            Statement last = body.getStatements().get(body.getStatements().size() - 1);
            if (last.isExpressionStmt()) {
                Expression expr = last.asExpressionStmt().getExpression();
                if (expr.isMethodCallExpr()) {
                    LinkedList<Expression> chain = Evaluator.findScopeChain(expr.asMethodCallExpr());
                    if (chain.isEmpty()) {
                        return checkunscopedMethod(enclosure, body, md, expr, last);
                    }
                    else {
                        return checkScopedMethod(enclosure, body, md, chain, last);
                    }
                }
                else {
                    addReturnStatement(body, last);
                    return true;
                }
            }
        }
        return true;
    }

    private static boolean checkunscopedMethod(Evaluator enclosure, BlockStmt body, MethodDeclaration md, Expression expr, Statement last) {
        /*
         * We are only concerned about finding the return type here so we don't
         * need to bother with overloading. All overloaded methods are required to
         * have the same return type.
         */
        CompilationUnit cu = enclosure.getCompilationUnit();
        if (cu != null) {
            Optional<MethodDeclaration> foundMethod = cu.findFirst(MethodDeclaration.class,
                    decl -> decl.getNameAsString().equals(expr.asMethodCallExpr().getNameAsString()));

            if (foundMethod.isPresent()) {
                addReturnStatement(body, last);
                return true;
            }
        }
        return false;
    }

    private static boolean checkScopedMethod(Evaluator enclosure, BlockStmt body, MethodDeclaration md, LinkedList<Expression> chain, Statement last) throws ReflectiveOperationException {
        try {
            Variable v = enclosure.evaluateScopeChain(chain);
            if (v != null) {
                if (v.getValue() instanceof Evaluator e) {

                } else {
                    Class<?> clz = v.getClazz();
                    for (Method m : clz.getMethods()) {
                        if (m.getName().equals(md.getNameAsString())) {
                            if (!m.getReturnType().equals(Void.TYPE)) {
                                addReturnStatement(body, last);
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (NullPointerException npe) {
            // there are some scopes that cannot be resolved for example
            // Collections.sort(list, (a,b) -> a.getValue().compareTo(b.getValue()));
            // we will leave these for now
        }
        return false;
    }

    private static void addReturnStatement(BlockStmt body, Statement last) {
        body.remove(last);
        ReturnStmt returnStmt = new ReturnStmt();
        returnStmt.setExpression(last.asExpressionStmt().getExpression());
        body.addStatement(returnStmt);
    }

    public abstract Type getType();
}
