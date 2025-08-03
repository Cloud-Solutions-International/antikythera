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
     * This reflective operation is likely happening because of a class that we have in source form.
     * The evaluator here represents the expression evalaution engine for that class.
     */
    private Evaluator enclosure;
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

    public void setEnclosure(Evaluator enclosure) {
        this.enclosure = enclosure;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public Method getMethod() {
        return method;
    }

    public void finalizeArguments() {
        // Special handling for Arrays class methods
        if (Arrays.class.equals(method.getDeclaringClass())) {
            String methodName = method.getName();
            Class<?>[] paramTypes = method.getParameterTypes();
            Object[] args = getArguments();
            
            // Handle Arrays.asList which is a varargs method
            if ("asList".equals(methodName) && method.isVarArgs()) {
                // Create an array of Objects for the varargs parameter
                Object[] varArgArray = new Object[args.length];
                System.arraycopy(args, 0, varArgArray, 0, args.length);
                finalArgs = new Object[]{varArgArray};
                return;
            }
            
            // Handle Arrays.stream and Arrays.sort which take array parameters
            if (("stream".equals(methodName) || "sort".equals(methodName)) && 
                paramTypes.length == 1 && paramTypes[0].isArray()) {
                finalArgs = args;
                return;
            }
        }
        
        if (method.isVarArgs()) {
            // For varargs methods, we need to handle the last parameter specially
            Class<?>[] paramTypes = method.getParameterTypes();
            int regularParamCount = paramTypes.length - 1;
            Object[] args = getArguments();
            
            // If we have exactly the right number of parameters and the last one is already an array
            // of the correct type, we can use the arguments as is
            if (args.length == paramTypes.length && args[regularParamCount] != null && 
                args[regularParamCount].getClass().isArray() && 
                args[regularParamCount].getClass().getComponentType().equals(paramTypes[regularParamCount].getComponentType())) {
                finalArgs = args;
                return;
            }
            
            // Otherwise, we need to create a new array for the varargs
            finalArgs = new Object[paramTypes.length];
            
            // Copy the regular parameters
            for (int i = 0; i < regularParamCount; i++) {
                finalArgs[i] = args[i];
            }
            
            // Create an array for the varargs parameters
            Class<?> componentType = paramTypes[regularParamCount].getComponentType();
            int varArgCount = args.length - regularParamCount;
            Object varArgArray = Array.newInstance(componentType, varArgCount);
            
            // Copy the varargs parameters into the array
            for (int i = 0; i < varArgCount; i++) {
                Array.set(varArgArray, i, args[regularParamCount + i]);
            }
            
            // Set the varargs array as the last parameter
            finalArgs[regularParamCount] = varArgArray;
        }
        else {
            finalArgs = method.getParameterTypes().length == 1 &&
                    method.getParameterTypes()[0].equals(Object[].class) ?
                    new Object[]{getArguments()} : getArguments();
        }
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
