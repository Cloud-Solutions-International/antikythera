package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.Expression;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Arrays;


public class ReflectionArguments {
    /**
     * The name of the method that will have to be matched in a class for relfection.
     */
    private final String methodName;

    private Method method;

    /**
     * the invoke method requires arguments to be presented as an array of objects.
     */
    private Object[] arguments;
    /**
     * In order to find the right method to invoke, we need to know the type of each argument
     */
    private final Class<?>[] argumentTypes;

    /**
     * Some methods have a scope, we may need it for additional type resolutions.
     */
    private Variable scope;

    private Object[] finalArgs;
    private Expression expression;

    public ReflectionArguments(String methodName, Object[] args, Class<?>[] argumentTypes) {
        this.methodName = methodName;
        this.arguments = args;
        this.argumentTypes = argumentTypes;
    }

    public String getMethodName() {
        return methodName;
    }

    public Object[] getArguments() {
        return arguments;
    }

    public void setArguments(Object[] arguments) {
        this.arguments = arguments;
    }

    public Class<?>[] getArgumentTypes() {
        return argumentTypes;
    }

    public Variable getScope() {
        return scope;
    }

    public void setScope(Variable scope) {
        this.scope = scope;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public Method getMethod() {
        return method;
    }

    public void finalizeArguments() {
        if (handleArraysClassSpecialCases()) {
            return;
        }

        if (method.isVarArgs()) {
            processVarArgsMethod();
        } else {
            processRegularMethod();
        }
    }

    private boolean handleArraysClassSpecialCases() {
        if (!Arrays.class.equals(method.getDeclaringClass())) {
            return false;
        }

        String name = method.getName();
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] args = getArguments();

        // Handle Arrays.asList which is a varargs method
        if ("asList".equals(name) && method.isVarArgs()) {
            Object[] varArgArray = new Object[args.length];
            System.arraycopy(args, 0, varArgArray, 0, args.length);
            finalArgs = new Object[]{varArgArray};
            return true;
        }

        // Handle Arrays.stream and Arrays.sort which take array parameters
        if (("stream".equals(name) || "sort".equals(name)) &&
                paramTypes.length == 1 && paramTypes[0].isArray()) {
            finalArgs = args;
            return true;
        }

        return false;
    }

    private void processVarArgsMethod() {
        Class<?>[] paramTypes = method.getParameterTypes();
        int regularParamCount = paramTypes.length - 1;
        Object[] args = getArguments();

        // Check if arguments are already in the correct varargs format
        if (args.length == paramTypes.length && args[regularParamCount] != null &&
                args[regularParamCount].getClass().isArray() &&
                args[regularParamCount].getClass().getComponentType().equals(paramTypes[regularParamCount].getComponentType())) {
            finalArgs = args;
            return;
        }

        // Create new array structure for varargs
        finalArgs = new Object[paramTypes.length];
        System.arraycopy(args, 0, finalArgs, 0, regularParamCount);

        // Create and populate the varargs array
        Class<?> componentType = paramTypes[regularParamCount].getComponentType();
        int varArgCount = args.length - regularParamCount;
        Object varArgArray = Array.newInstance(componentType, varArgCount);

        for (int i = 0; i < varArgCount; i++) {
            Array.set(varArgArray, i, args[regularParamCount + i]);
        }

        finalArgs[regularParamCount] = varArgArray;
    }

    private void processRegularMethod() {
        finalArgs = method.getParameterTypes().length == 1 &&
                method.getParameterTypes()[0].equals(Object[].class) ?
                new Object[]{getArguments()} : getArguments();
    }

    public Object[] getFinalArgs() {
        return finalArgs;
    }

    public void setMethodCallExpression(Expression methodCall) {
        this.expression = methodCall;
    }

    public Expression getMethodCallExpression() {
        return expression;
    }

}
