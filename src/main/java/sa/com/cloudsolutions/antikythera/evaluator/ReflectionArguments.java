package sa.com.cloudsolutions.antikythera.evaluator;

public class ReflectionArguments {
    private final String methodName;
    private Object[] args;
    private final Class<?>[] paramTypes;

    public ReflectionArguments(String methodName, Object[] args, Class<?>[] paramTypes) {
        this.methodName = methodName;
        this.args = args;
        this.paramTypes = paramTypes;
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

    public Class<?>[] getParamTypes() {
        return paramTypes;
    }
}
