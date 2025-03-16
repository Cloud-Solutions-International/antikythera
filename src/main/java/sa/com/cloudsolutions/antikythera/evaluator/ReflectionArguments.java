package sa.com.cloudsolutions.antikythera.evaluator;

public class ReflectionArguments {
    private final String methodName;
    private Object[] args;
    private final Class<?>[] argumentTypes;
    private Evaluator enclosure;
    private Variable scope;

    public ReflectionArguments(String methodName, Object[] args, Class<?>[] argumentTypes) {
        this.methodName = methodName;
        this.args = args;
        this.argumentTypes = argumentTypes;
    }

    public String getMethodName() {
        return methodName;
    }

    public Object[] getArgs() {
        return args;
    }

    public void setArgs(Object[] args) {
        this.args = args;
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
