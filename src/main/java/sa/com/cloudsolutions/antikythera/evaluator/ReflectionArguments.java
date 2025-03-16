package sa.com.cloudsolutions.antikythera.evaluator;

public class ReflectionArguments {
    /**
     * The name of the method that will have to be matched in a class for relfection.
     */
    private final String methodName;
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

    public Evaluator getEnclosure() {
        return enclosure;
    }

    public void setEnclosure(Evaluator enclosure) {
        this.enclosure = enclosure;
    }
}
