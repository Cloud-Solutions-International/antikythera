package sa.com.cloudsolutions.antikythera.evaluator;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;

import java.lang.reflect.Method;

public class MethodInterceptor {
    private final Evaluator evaluator;

    public MethodInterceptor(Evaluator evaluator) {
        this.evaluator = evaluator;
    }

    @RuntimeType
    public Object intercept(@Origin Method method, @AllArguments Object[] args) throws ReflectiveOperationException {
        System.out.println("Intercepting method: " + method.getName());  //
        String name = method.getName();
        Class<?>[] parameterTypes = method.getParameterTypes();
        return null;
    }

    public Evaluator getEvaluator() {
        return evaluator;
    }
}
