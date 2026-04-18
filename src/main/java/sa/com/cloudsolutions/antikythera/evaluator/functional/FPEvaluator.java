package sa.com.cloudsolutions.antikythera.evaluator.functional;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.UnknownType;
import com.github.javaparser.ast.type.VoidType;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.EvaluatorFactory;
import sa.com.cloudsolutions.antikythera.evaluator.InnerClassEvaluator;
import sa.com.cloudsolutions.antikythera.evaluator.Scope;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.evaluator.Symbol;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.util.Map;
import java.util.Optional;

public abstract class FPEvaluator<T> extends InnerClassEvaluator {
    public static final String OBJECT_TYPE = "Object";
    protected MethodDeclaration methodDeclaration;
    Expression expr;

    protected FPEvaluator(EvaluatorFactory.Context context) {
        super(context);
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
            md.setType(new ClassOrInterfaceType().setName(OBJECT_TYPE));
        } else {
            if (isReturning(lambdaExpr)) {
                md.setType(new ClassOrInterfaceType().setName(OBJECT_TYPE));
                Statement last = body.getStatements().get(body.getStatements().size() - 1);
                addReturnStatement(body, last);
            }
        }

        for (Parameter param : lambdaExpr.getParameters()) {
            md.addParameter(param);
            if (param.getType() instanceof UnknownType) {
                param.setType(OBJECT_TYPE);
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
                case 0 -> EvaluatorFactory.create("java.util.function.Supplier", SupplierEvaluator.class);
                case 1 -> EvaluatorFactory.create("java.util.function.Function", FunctionEvaluator.class);
                case 2 -> EvaluatorFactory.create("java.util.function.BiFunction", BiFunctionEvaluator.class);
                default -> EvaluatorFactory.create("java.util.function.Function", NAryFunctionEvaluator.class);
            };
            eval.setMethod(md);
            return eval;
        } else {
            FPEvaluator<?> eval = switch (md.getParameters().size()) {
                case 0 -> EvaluatorFactory.create("java.lang.Runnable", RunnableEvaluator.class);
                case 1 -> EvaluatorFactory.create("java.util.function.Consumer", ConsumerEvaluator.class);
                case 2 -> EvaluatorFactory.create("java.util.function.BiConsumer", BiConsumerEvaluator.class);
                default -> EvaluatorFactory.create("java.util.function.Consumer", NAryConsumerEvaluator.class);
            };
            eval.setMethod(md);
            return eval;
        }
    }


    private static boolean isReturning(LambdaExpr lambdaExpr) {
        /*
         * There are two kinds of lambdas; those that contain a block and those that don't
         *
         * If you have a block statement, and you are supposed to return something, you are need an
         * explicit return statement. So those are pretty easy to spot, just look at the last line
         * of the block.
         *
         * Those without a block statement are trickier. The simplest approach is to look at the
         * outer method and see if it is one of the usual suspects that are supposed to return
         * a value.
         */
        Optional<Node> parentNode = lambdaExpr.getParentNode();
        if (parentNode.isPresent()) {
            if (!lambdaExpr.getBody().isBlockStmt()) {
                return true;
            }
            if (parentNode.get() instanceof MethodCallExpr mce) {
                String name = mce.getNameAsString();
                return switch (name) {
                    case "map", "filter", "sorted", "reduce", "anyMatch", "allMatch", "noneMatch",
                         "findFirst", "findAny", "flatMap", "mapToInt", "mapToLong", "mapToDouble",
                         "mapToObj", "collect", "min", "max", "takeWhile", "dropWhile" -> true;
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

    public MethodDeclaration getMethodDeclaration() {
        return methodDeclaration;
    }

    @Override
    public Symbol getValue(Node n, String name) {
        Symbol v = super.getValue(n, name);
        if (v == null) {
            Optional<Node> parentNode = expr.getParentNode();
            if (parentNode.isPresent()) {
                v = enclosure.getValue(parentNode.get(), name);
                if (v != null) {
                    return v;
                }
                for (Map<String, Symbol> local : enclosure.getLocals().values()) {
                    v = local.get(name);
                    if (v != null) {
                        return v;
                    }
                }
            }
        }
        return v;
    }

    /**
     * Dispatch a method call on this functional evaluator (e.g. {@code toPredicate()},
     * {@code apply()}, {@code accept()}) via {@link FunctionalInvocationHandler}.
     *
     * <p>The base {@link Evaluator#executeMethod(Scope)} would attempt
     * {@code AbstractCompiler.getMatchingType(cu, getClassName()).orElseThrow()}, which fails
     * when the compilation unit has been borrowed from the enclosure by
     * {@link InnerClassEvaluator} — the borrowed CU belongs to a different class, so the
     * FP evaluator's own class name is not found, causing {@link java.util.NoSuchElementException}.
     * Overriding here routes any functional-interface call directly to the lambda body.</p>
     */
    @Override
    public Variable executeMethod(Scope sc) throws ReflectiveOperationException {
        MethodCallExpr mce = sc.getMCEWrapper().asMethodCallExpr().orElse(null);
        if (mce == null) return null;

        Object[] args = new Object[mce.getArguments().size()];
        for (int i = 0; i < args.length; i++) {
            Variable v = evaluateExpression(mce.getArgument(i));
            args[i] = v != null ? v.getValue() : null;
        }

        try {
            Object result = new FunctionalInvocationHandler(this).invoke(null, null, args);
            return result != null ? new Variable(result) : null;
        } catch (Throwable t) {
            if (t instanceof ReflectiveOperationException roe) throw roe;
            throw new AntikytheraException(t);
        }
    }

    public abstract Type getType();

    @Override
    protected Variable resolveExpressionHelper(TypeWrapper wrapper) {
        if (wrapper.getType() != null) {
            Variable v;
            Evaluator eval = EvaluatorFactory.create(wrapper.getType().getFullyQualifiedName().orElseThrow(), Evaluator.class);
            eval.setupFields();
            eval.initializeFields();
            v = new Variable(eval);
            return v;
        }
        return null;
    }

    @Override
    protected Object findScopeType(String s) {
        Object o = super.findScopeType(s);
        if (o == null) {
            TypeWrapper wrapper = AbstractCompiler.findType(enclosure.getCompilationUnit(), s);
            if (wrapper.getType() != null) {
                return EvaluatorFactory.create(wrapper.getFullyQualifiedName(), enclosure);
            }
            return wrapper.getClazz();
        }
        return o;
    }

    @Override
    protected Variable resolveExpression(NameExpr expr) {
        Variable v = super.resolveExpression(expr);
        if (v == null) {
            TypeWrapper wrapper = AbstractCompiler.findType(enclosure.getCompilationUnit(), expr.getNameAsString());
            return resolveExpressionHelper(wrapper);
        }
        return v;
    }
}
