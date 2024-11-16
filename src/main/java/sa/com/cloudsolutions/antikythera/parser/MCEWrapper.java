package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.expr.MethodCallExpr;

public class MCEWrapper {
    MethodCallExpr methodCallExpr;
    NodeList<Type> argumentTypes;

    public NodeList<Type> getArgumentTypes() {
        return argumentTypes;
    }

    public void setArgumentTypes(NodeList<Type> argumentTypes) {
        this.argumentTypes = argumentTypes;
    }

    public MethodCallExpr getMethodCallExpr() {
        return methodCallExpr;
    }

    public void setMethodCallExpr(MethodCallExpr methodCallExpr) {
        this.methodCallExpr = methodCallExpr;
    }
}
