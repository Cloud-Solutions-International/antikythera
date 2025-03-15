package sa.com.cloudsolutions.antikythera.evaluator.functional;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.UnknownType;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

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
        if (methodCall.getNameAsString().equals("apply")) {
            wrapCallExpression(methodCall);
            return executeMethod(methodDeclaration);
        }
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public static FPEvaluator<?> create(MethodReferenceExpr lambdaExpr, Evaluator enclosure) throws ReflectiveOperationException {
        CompilationUnit cu = enclosure.getCompilationUnit();
        TypeDeclaration<?> cdecl = AbstractCompiler.getMatchingType(cu, lambdaExpr.getScope().toString());
        MethodDeclaration md = cdecl.findFirst(
                MethodDeclaration.class, mx -> mx.getNameAsString().equals(lambdaExpr.getIdentifier())
        ).orElseThrow();

        BlockStmt body;
        if (md.getBody().isPresent()) {
            body = md.getBody().get();
        } else {
            body = new BlockStmt();
            md.setBody(body);
        }
        md.setType(new UnknownType());

        FPEvaluator<?> fp = createEvaluator(enclosure, md, body);
        fp.enclosure = enclosure;
        fp.expr = lambdaExpr;
        return fp;
    }

    public static FPEvaluator<?> create(LambdaExpr lambdaExpr, Evaluator enclosure) throws ReflectiveOperationException {
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
        lambdaExpr.getParameters().forEach(md::addParameter);

        FPEvaluator<?> fp = createEvaluator(enclosure, md, body);
        fp.enclosure = enclosure;
        fp.expr = lambdaExpr;
        return fp;
    }

    @Override
    public Variable getValue(Node n, String name) {
        Variable v = super.getValue(n, name);
        if (v == null) {
            return enclosure.getValue(expr, name);
        }
        return v;
    }

    private static FPEvaluator<?> createEvaluator(Evaluator enclosure, MethodDeclaration md, BlockStmt body) throws ReflectiveOperationException {
        if (checkReturnType(enclosure, body, md) ) {
            FPEvaluator<?> eval = switch (md.getParameters().size()) {
                case 0 -> new SupplierEvaluator<>("S");
                case 1 -> new FunctionEvaluator<>("F");
                case 2 -> new BiFunctionEvaluator<>("BiF");
                default -> null;
            };
            eval.setMethod(md);
            return eval;
        }
        else {
            FPEvaluator<?> eval = switch(md.getParameters().size()) {
                case 0 -> new RunnableEvaluator("R");
                case 1 -> new ConsumerEvaluator<>("C");
                case 2 -> new BiConsumerEvaluator<>("BiC");
                default -> null;
            };

            eval.setMethod(md);
            return eval;
        }
    }

    private static boolean checkReturnType(Evaluator enclosure, BlockStmt body, MethodDeclaration md) throws ReflectiveOperationException {
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
