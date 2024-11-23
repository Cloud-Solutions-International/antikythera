package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithArguments;
import com.github.javaparser.ast.type.Type;

public class MCEWrapper {
    NodeWithArguments<?> methodCallExpr;
    NodeList<Type> argumentTypes;

    public MCEWrapper() {
    }

    public MCEWrapper(NodeWithArguments<?> oce) {
        this.methodCallExpr = oce;
    }

    public NodeList<Type> getArgumentTypes() {
        return argumentTypes;
    }

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
