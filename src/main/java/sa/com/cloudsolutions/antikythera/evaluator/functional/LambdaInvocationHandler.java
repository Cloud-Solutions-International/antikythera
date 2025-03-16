package sa.com.cloudsolutions.antikythera.evaluator.functional;


import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public record LambdaInvocationHandler(Object invocationTarget,
                                       Class<?> lambdaClass) implements java.lang.reflect.InvocationHandler {

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(proxy, args);
        }

        Method[] methods = lambdaClass.getDeclaredMethods();
        for (Method m : methods) {
            if (!m.isDefault() && !Modifier.isStatic(m.getModifiers()) &&
                    m.getParameterCount() == method.getParameterCount()) {
                return m.invoke(invocationTarget, args);
            }
        }
        return null;
    }
}
