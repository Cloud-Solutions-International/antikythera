package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithArguments;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.type.Type;

/**
 * Wraps method call expressions to solve their argument types.
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

    public MCEWrapper() {
    }

    public MCEWrapper(NodeWithArguments<?> oce) {
        this.methodCallExpr = oce;
    }

    /**
     *
     * @return the argument types maybe null if not properly identified
     */
    public NodeList<Type> getArgumentTypes() {
        return argumentTypes;
    }

    /**
     * Sets the types of the arguments for the method call expression
     * @param argumentTypes
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
