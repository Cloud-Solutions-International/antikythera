package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.ConstructorDeclaration;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Intercepts constructor calls in dynamic classes created by AKBuddy.
 * Similar to MethodInterceptor but for constructors.
 */
public class ConstructorInterceptor {
    private Evaluator evaluator;
    private Class<?> wrappedClass = Object.class;

    public ConstructorInterceptor(Evaluator evaluator) {
        this.evaluator = evaluator;
    }

    public ConstructorInterceptor(Class<?> clazz) {
        wrappedClass = clazz;
    }

    /**
     * Intercepts constructor calls with a ConstructorDeclaration.
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

    public Class<?> getWrappedClass() {
        return wrappedClass;
    }

    public Evaluator getEvaluator() {
        return evaluator;
    }

    public void setWrappedClass(Class<?> componentClass) {
        this.wrappedClass = componentClass;
    }

    /**
     * Interceptor for constructors in dynamic classes.
     * This is used by ByteBuddy to delegate constructor calls to the parent ConstructorInterceptor.
     */
    public static class Interceptor {
        private final ConstructorDeclaration sourceConstructor;

        public Interceptor(ConstructorDeclaration sourceConstructor) {
            this.sourceConstructor = sourceConstructor;
        }

        @SuppressWarnings("java:S3011")
        @RuntimeType
        public Object intercept(@This Object instance, @Origin Constructor<?> constructor, @AllArguments Object[] args) throws ReflectiveOperationException {
            Field f = instance.getClass().getDeclaredField(AKBuddy.CONSTRUCTOR_INTERCEPTOR);
            f.setAccessible(true);
            ConstructorInterceptor parent = (ConstructorInterceptor) f.get(instance);
            return parent.intercept(constructor, args, sourceConstructor);
        }
    }
}
