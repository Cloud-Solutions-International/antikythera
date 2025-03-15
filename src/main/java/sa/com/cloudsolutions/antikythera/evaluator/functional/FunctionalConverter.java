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

    /**
     * Creates a dynamic proxy implementing the target functional interface
     */
    public static Object createProxy(MethodReferenceExpr methodRef, Class<?> targetInterface) throws ReflectiveOperationException {
        // Find scope and target method
        Expression scope = methodRef.getScope();
        Method targetMethod = findTargetMethod(scope, methodRef.getIdentifier());
        Object instance = scope.isThisExpr() ? null : evaluateScope(scope);

        return Proxy.newProxyInstance(
            targetInterface.getClassLoader(),
            new Class<?>[] { targetInterface },
            new FunctionalHandler(instance, targetMethod)
        );
    }

    private static class FunctionalHandler implements InvocationHandler {
        private final Object instance;
        private final Method targetMethod;

        FunctionalHandler(Object instance, Method targetMethod) {
            this.instance = instance;
            this.targetMethod = targetMethod;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }
            return targetMethod.invoke(instance, args);
        }
    }

    /**
     * Find method in target class
     */
    private static Method findTargetMethod(Expression scope, String methodName) throws ReflectiveOperationException {
        Class<?> targetClass = getTargetClass(scope);
        return targetClass.getDeclaredMethod(methodName);
    }

    /**
     * Get class for scope expression
     */
    private static Class<?> getTargetClass(Expression scope) throws ClassNotFoundException {
        if (scope.isTypeExpr()) {
            String className = scope.asTypeExpr().getTypeAsString();
            return PRIMITIVE_TYPES.getOrDefault(className,
                AbstractCompiler.loadClass(className));
        }
        return null;
        //return scope.calculateResolvedType().describe();
    }

    /**
     * Evaluate scope expression to get instance
     */
    private static Object evaluateScope(Expression scope) throws ReflectiveOperationException {
//        if (scope.isNameExpr()) {
//            return AntikytheraRunTime.getVariable(scope.asNameExpr().getNameAsString());
//        }
//        if (scope.isMethodCallExpr()) {
//            return AntikytheraRunTime.evaluateMethodCall(scope.asMethodCallExpr());
//        }
        return null;
    }
}
