package sa.com.cloudsolutions.antikythera.evaluator.functional;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
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
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.ImportWrapper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.Optional;

public abstract class FPEvaluator<T> extends Evaluator {
    public static final String SUPPLIER = "java.util.function.Supplier";
    public static final String FUNCTION = "java.util.function.Function";
    public static final String BI_FUNCTION = "java.util.function.BiFunction";
    public static final String RUNNABLE = "java.lang.Runnable";
    public static final String CONSUMER = "java.util.function.Consumer";
    public static final String BI_CONSUMER = "java.util.function.BiConsumer";

    /**
     * The Method declaration to execute if this method is available in source code
     * will take precedence over the method defined below. In other words, if both a method and a
     * methodDeclration have been defined , the methodDeclaration will be executed rather than
     * the method.
     */
    protected MethodDeclaration methodDeclaration;
    /**
     * A method from a compiled java class to be executed if a methodDeclaration is not found.
     */
    protected Method method;

    /**
     * If method is defined by methodDeclaration is undefined invoke the method on the object
     */
    protected Object object;

    public FPEvaluator(String className) {
        super(className);
    }

    public void setMethodDeclaration(MethodDeclaration methodDeclaration) {
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

    public static FPEvaluator<?> create(MethodReferenceExpr methodRef, Field field) throws ReflectiveOperationException {
        Object obj = field.get(null);
        Class<?> clazz = obj.getClass();
        FPEvaluator<?> eval = null;

        if (methodRef.getParentNode().isPresent() && methodRef.getParentNode().get() instanceof MethodCallExpr mce) {
            TypeDeclaration<?> cdecl = mce.findAncestor(TypeDeclaration.class).orElseThrow();
            for (MethodDeclaration md : cdecl.getMethodsByName(methodRef.getIdentifier())) {
                for (Parameter param : md.getParameters()) {
                    if (param.findCompilationUnit().isPresent()) {

                    }
                    else {
                        eval = findSAM(mce, param);
                    }
                }
            }
        }
        else {
            throw new AntikytheraException("A method reference has to be an argument to a method call");
        }

        if (eval != null) {
            for (Method m : clazz.getMethods()) {
                if (m.getName().equals(methodRef.getIdentifier())) {
                    for (Class<?> p : m.getParameterTypes()) {
                        if (p.getName().equals(eval.getClassName())) {
                            eval.method = m;
                            eval.object = obj;
                            return eval;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static FPEvaluator<?> findSAM(MethodCallExpr mce, Parameter param) throws ClassNotFoundException {
        String fqn = AbstractCompiler.findFullyQualifiedName(
                mce.findCompilationUnit().orElseThrow(), param.getNameAsString());
        Class<?> clazz = AbstractCompiler.loadClass(fqn);
        for (Class<?> iface : clazz.getInterfaces()) {
            if (iface.isAnnotationPresent(FunctionalInterface.class)) {
                switch (iface.getName()) {
                    case SUPPLIER -> {
                        return new SupplierEvaluator<>();
                    }
                    case FUNCTION -> {
                        return new FunctionEvaluator<>();
                    }
                    case BI_FUNCTION -> {
                        return new BiFunctionEvaluator<>();
                    }
                    case RUNNABLE -> {
                        return new RunnableEvaluator();
                    }
                    case CONSUMER -> {
                        return new ConsumerEvaluator<>();
                    }
                    case BI_CONSUMER -> {
                        return new BiConsumerEvaluator<>();
                    }
                }
            }
        }
        return null;
    }

    public static FPEvaluator<?> create(MethodReferenceExpr methodRef, Evaluator enclosure) throws ReflectiveOperationException {
        CompilationUnit cu = enclosure.getCompilationUnit();
        TypeDeclaration<?> cdecl = AbstractCompiler.getMatchingType(cu, methodRef.getScope().toString());
        MethodDeclaration md = cdecl.findFirst(
                MethodDeclaration.class, mx -> mx.getNameAsString().equals(methodRef.getIdentifier())
        ).orElseThrow();

        BlockStmt body;
        if (md.getBody().isPresent()) {
            body = md.getBody().get();
        } else {
            body = new BlockStmt();
            md.setBody(body);
        }
        md.setType(new UnknownType());

        return createEvaluator(enclosure, md, body);
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

        return createEvaluator(enclosure, md, body);
    }

    private static FPEvaluator<?> createEvaluator(Evaluator enclosure, MethodDeclaration md, BlockStmt body) throws ReflectiveOperationException {
        if (checkReturnType(enclosure, body, md) ) {
            FPEvaluator<?> eval = switch (md.getParameters().size()) {
                case 0 -> new SupplierEvaluator<>();
                case 1 -> new FunctionEvaluator<>();
                case 2 -> new BiFunctionEvaluator<>();
                default -> null;
            };
            eval.setMethodDeclaration(md);
            return eval;
        }
        else {
            FPEvaluator<?> eval = switch(md.getParameters().size()) {
                case 0 -> new RunnableEvaluator();
                case 1 -> new ConsumerEvaluator<>();
                case 2 -> new BiConsumerEvaluator<>();
                default -> null;
            };

            eval.setMethodDeclaration(md);
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
