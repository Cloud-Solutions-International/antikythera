package sa.com.cloudsolutions.antikythera.evaluator;

public class ReflectionArguments {
    private String methodName;
    private Object[] args;
    private Class<?>[] paramTypes;

    public ReflectionArguments(String methodName, Object[] args, Class<?>[] paramTypes) {
        this.methodName = methodName;
        this.args = args;
        this.paramTypes = paramTypes;
    }

    public ReflectionArguments() {

    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
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

    public void setParamTypes(Class<?>[] paramTypes) {
        this.paramTypes = paramTypes;
    }
}
