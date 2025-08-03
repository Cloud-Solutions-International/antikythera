package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingCall;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;
import sa.com.cloudsolutions.antikythera.parser.Callable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class MethodInterceptor {
    private Evaluator evaluator;
    private Class<?> wrappedClass = Object.class;

    public MethodInterceptor(Evaluator evaluator) {
        this.evaluator = evaluator;
    }
    public MethodInterceptor(Class<?> clazz) {
        wrappedClass = clazz;
    }

    /**
     * Intercept constructor calls with a ConstructorDeclaration.
     * This is used when we have source code available.
     */
    @RuntimeType
    public Object intercept(Constructor<?> constructor, Object[] args, ConstructorDeclaration constructorDecl) throws ReflectiveOperationException {
        if (evaluator != null) {
            // Push arguments onto stack in reverse order
            for (int i = args.length - 1; i >= 0; i--) {
                AntikytheraRunTime.push(new Variable(args[i]));
            }

            // Execute the constructor using source code evaluation
            evaluator.executeConstructor(constructorDecl);

            // For constructors, we need to return a new instance of the class
            try {
                return wrappedClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                // If we can't create an instance, return null
                return null;
            }
        }
        return intercept(constructor, args);
    }


    /**
     * Intercepts constructor calls without a ConstructorDeclaration.
     * This is used when we only have bytecode available.
     */
    @RuntimeType
    public Object intercept(@Origin Constructor<?> constructor, @AllArguments Object[] args) throws ReflectiveOperationException {
        if (wrappedClass != null) {
            try {
                Constructor<?> targetConstructor = wrappedClass.getDeclaredConstructor(constructor.getParameterTypes());
                return targetConstructor.newInstance(args);
            } catch (NoSuchMethodException e) {
                // Constructor not found in wrapped class, fall through to default behavior
            }
        }

        // For constructors, we need to return a new instance of the class
        try {
            return wrappedClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            // If we can't create an instance, return null
            return null;
        }
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
            if (result != null) {
                Object value = result.getValue();
                if (value instanceof Evaluator eval) {
                    MethodInterceptor interceptor = new MethodInterceptor(eval);
                    Class<?> clazz = AKBuddy.createDynamicClass(interceptor);
                    return AKBuddy.createInstance(clazz, interceptor);
                }
                return value;
            }
            return null;
        }
        return intercept(method, args);
    }

    @RuntimeType
    public Object intercept(@Origin Method method, @AllArguments Object[] args) throws ReflectiveOperationException {
        Callable callable = new Callable(method, null);
        MockingCall mc = MockingRegistry.getThen(method.getDeclaringClass().getName(), callable);
        if (mc != null) {
            return mc.getVariable().getValue();
        }

        if (wrappedClass != null && ! MockingRegistry.isMockTarget(wrappedClass.getName())) {
            try {
                Method targetMethod = wrappedClass.getMethod(method.getName(), method.getParameterTypes());
                // Create instance if the method is not static
                if (!java.lang.reflect.Modifier.isStatic(targetMethod.getModifiers())) {
                    Object instance = wrappedClass.getDeclaredConstructor().newInstance();
                    return targetMethod.invoke(instance, args);
                }
                return targetMethod.invoke(null, args);
            } catch (NoSuchMethodException e) {
                // Method not found in wrapped class, fall through to default behavior
            }
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

    public void setWrappedClass(Class<?> componentClass) {
        this.wrappedClass = componentClass;
    }

    public static class MethodDeclarationSupport {
        private final MethodDeclaration sourceMethod;

        public MethodDeclarationSupport(MethodDeclaration sourceMethod) {
            this.sourceMethod = sourceMethod;
        }

        @SuppressWarnings("java:S3011")
        @RuntimeType
        public Object intercept(@This Object instance, @Origin Method method, @AllArguments Object[] args) throws ReflectiveOperationException {
            Field f = instance.getClass().getDeclaredField(AKBuddy.INSTANCE_INTERCEPTOR);
            f.setAccessible(true);
            MethodInterceptor parent = (MethodInterceptor) f.get(instance);
            return parent.intercept(method, args, sourceMethod);
        }
    }

    public static class ConstructorDeclarationSupport {
        private final ConstructorDeclaration sourceConstructor;

        public ConstructorDeclarationSupport(ConstructorDeclaration sourceConstructor) {
            this.sourceConstructor = sourceConstructor;
        }

        @SuppressWarnings("java:S3011")
        @RuntimeType
        public Object intercept(@This Object instance, @Origin Constructor<?> constructor, @AllArguments Object[] args) throws ReflectiveOperationException {
            Field f = instance.getClass().getDeclaredField(AKBuddy.CONSTRUCTOR_INTERCEPTOR);
            f.setAccessible(true);
            MethodInterceptor parent = (MethodInterceptor) f.get(instance);
            return parent.intercept(constructor, args, sourceConstructor);
        }
    }
}
