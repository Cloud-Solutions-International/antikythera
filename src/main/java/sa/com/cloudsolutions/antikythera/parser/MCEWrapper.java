package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithArguments;
import com.github.javaparser.ast.type.Type;
import sa.com.cloudsolutions.antikythera.evaluator.Reflect;

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
    NodeWithArguments<?> methodCallExpr;
    /**
     * The type of each argument (if correctly identified or else null)
     */
    NodeList<Type> argumentTypes;

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

            String elementType = argumentTypes.get(i).getElementType().toString();
            try {
                classes[i] = Reflect.getComponentClass(elementType);
            } catch (ClassNotFoundException e) {
                classes[i] = Object.class;
            }
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

    public void setMethodCallExpr(NodeWithArguments<?> methodCallExpr) {
        this.methodCallExpr = methodCallExpr;
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
}
