package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithArguments;
import com.github.javaparser.ast.type.Type;
import sa.com.cloudsolutions.antikythera.evaluator.AKBuddy;
import sa.com.cloudsolutions.antikythera.evaluator.EvaluatorFactory;
import sa.com.cloudsolutions.antikythera.evaluator.MethodInterceptor;
import sa.com.cloudsolutions.antikythera.evaluator.MockingEvaluator;
import sa.com.cloudsolutions.antikythera.evaluator.Reflect;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;

import java.util.Optional;

/**
 * Wraps method call expressions to solve their argument types.
 * At the time that a method call is being evaluated, typically we only have the argument names
 * and not their types. findMethodDeclaration in AbstractCompiler requires that the types be known
 * this class bridges that gap.
 */
public class MCEWrapper {
    /**
     * The MCE being wrapped
     */
    private final NodeWithArguments<?> methodCallExpr;
    /**
     * The type of each argument (if correctly identified or else null)
     */
    private NodeList<Type> argumentTypes;

    private Callable matchingCallable;

    public MCEWrapper(NodeWithArguments<?> oce) {
        this.methodCallExpr = oce;
        argumentTypes = new NodeList<>();
    }

    /**
     *
     * @return the argument types maybe null if not properly identified
     */
    public NodeList<Type> getArgumentTypes() {
        return argumentTypes;
    }

    public Class<?>[] getArgumentTypesAsClasses()  {
        if (argumentTypes == null) {
            return null;
        }
        Class<?>[] classes = new Class<?>[argumentTypes.size()];

        for (int i = 0; i < argumentTypes.size(); i++) {

            Type type = argumentTypes.get(i);
            CompilationUnit cu = (methodCallExpr instanceof MethodCallExpr mce)
                    ? mce.findCompilationUnit().orElseThrow()
                    : ((ObjectCreationExpr) methodCallExpr).findCompilationUnit().orElseThrow();


            classes[i] = Reflect.resolveComponentClass(cu, type.getElementType());
        }

        return classes;
    }

    /**
     * Sets the types of the arguments for the method call expression
     * @param argumentTypes the types of the arguments
     */
    public void setArgumentTypes(NodeList<Type> argumentTypes) {
        this.argumentTypes = argumentTypes;
    }

    public NodeWithArguments<?> getMethodCallExpr() {
        return methodCallExpr;
    }

    @Override
    public String toString() {
        if (methodCallExpr != null) {
            return methodCallExpr.toString();
        }
        return "";
    }

    public String getMethodName() {
        if (methodCallExpr instanceof MethodCallExpr mce) {
            return mce.getNameAsString();
        }

        return null;
    }

    public Callable getMatchingCallable() {
        return matchingCallable;
    }

    public void setMatchingCallable(Callable match) {
        this.matchingCallable = match;
    }

    public Optional<MethodCallExpr> asMethodCallExpr() {
        if (methodCallExpr instanceof MethodCallExpr mce) {
            return Optional.of(mce);
        }
        return Optional.empty();
    }
}
