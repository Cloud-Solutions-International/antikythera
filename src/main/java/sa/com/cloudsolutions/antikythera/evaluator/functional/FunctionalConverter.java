package sa.com.cloudsolutions.antikythera.evaluator.functional;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

public class FunctionalConverter {
    private static final Map<String, Class<?>> PRIMITIVE_TYPES = Map.of(
        "int", int.class,
        "long", long.class,
        "double", double.class,
        "boolean", boolean.class,
        "void", void.class
    );

    /**
     * Converts a MethodReferenceExpr to a LambdaExpr
     */
    public static LambdaExpr convertToLambda(MethodReferenceExpr methodRef) {
        // Find target method based on scope and identifier
        String methodName = methodRef.getIdentifier();
        Expression scope = methodRef.getScope();

        // Create parameter list based on functional interface
        NodeList<Parameter> parameters = new NodeList<>();
        parameters.add(new Parameter(new ClassOrInterfaceType("Object"), "arg"));

        // Create method call with parameters
        MethodCallExpr call = new MethodCallExpr();
        call.setName(methodName);
        call.setScope(scope);
        call.addArgument(new NameExpr("arg"));

        // Create lambda body
        BlockStmt body = new BlockStmt();
        body.addStatement(new ReturnStmt(call));

        // Create lambda expression
        return new LambdaExpr(parameters, body);
    }
}
