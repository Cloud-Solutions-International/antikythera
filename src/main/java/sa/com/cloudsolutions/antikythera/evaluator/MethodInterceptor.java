package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.MethodDeclaration;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;

import java.lang.reflect.Method;

public class MethodInterceptor {
    private Evaluator evaluator;
    private Class<?> wrappedClass;

    public MethodInterceptor(Evaluator evaluator) {
        this.evaluator = evaluator;
    }
    public MethodInterceptor(Class<?> clazz) {
        wrappedClass = clazz;
    }

    @RuntimeType
    public Object intercept(Method method, Object[] args, MethodDeclaration methodDecl) throws ReflectiveOperationException {
        if (evaluator != null) {
            // Push arguments onto stack in reverse order
            for (int i = args.length - 1; i >= 0; i--) {
                AntikytheraRunTime.push(new Variable(args[i]));
            }

            // Execute the method using source code evaluation
            Variable result = evaluator.executeMethod(methodDecl);

            // Return the actual value from the Variable wrapper
            return result != null ? result.getValue() : null;
        }
        Class<?> clazz = method.getReturnType();
        if (clazz.equals(void.class)) {
            return null;
        }

        return Reflect.getDefault(clazz);
    }

    public Class<?> getWrappedClass() {
        return wrappedClass;
    }

    public Evaluator getEvaluator() {
        return evaluator;
    }

    public static class Interceptor {
        private final MethodInterceptor parent;
        private final MethodDeclaration sourceMethod;

        public Interceptor(MethodInterceptor parent, MethodDeclaration sourceMethod) {
            this.parent = parent;
            this.sourceMethod = sourceMethod;
        }

        @RuntimeType
        public Object intercept(@Origin Method method, @AllArguments Object[] args) throws ReflectiveOperationException {
            return parent.intercept(method, args, sourceMethod);
        }
    }
}
