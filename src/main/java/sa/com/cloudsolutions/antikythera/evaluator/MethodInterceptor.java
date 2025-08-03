package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
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

    @RuntimeType
    public Object intercept(Constructor<?> constructor, Object[] args, ConstructorDeclaration constructorDecl) throws ReflectiveOperationException {
        if (evaluator != null && constructorDecl != null) {
            // Push arguments onto stack in reverse order
            for (int i = args.length - 1; i >= 0; i--) {
                AntikytheraRunTime.push(new Variable(args[i]));
            }

            // Execute the constructor using source code evaluation
            evaluator.executeConstructor(constructorDecl);
        }

        // The actual instance creation is handled by ByteBuddy's SuperMethodCall
        // We just need to set the interceptor field

            Field icpt = constructor.getDeclaringClass().getDeclaredField(AKBuddy.INSTANCE_INTERCEPTOR);
            icpt.setAccessible(true);
            icpt.set(this, this);

            // Initialize fields if needed
            if (evaluator != null) {
                TypeDeclaration<?> dtoType = AntikytheraRunTime.getTypeDeclaration(evaluator.getClassName()).orElseThrow();
                for (FieldDeclaration field : dtoType.getFields()) {
                    Field f = constructor.getDeclaringClass().getDeclaredField(field.getVariable(0).getNameAsString());
                    f.setAccessible(true);

                    Variable v = evaluator.getField(field.getVariable(0).getNameAsString());
                    if (v != null) {
                        Object value = v.getValue();
                        if (value instanceof Evaluator eval) {
                            MethodInterceptor interceptor1 = new MethodInterceptor(eval);
                            Class<?> c = AKBuddy.createDynamicClass(interceptor1);
                            f.set(this, AKBuddy.createInstance(c, interceptor1));
                        } else {
                            f.set(this, value);
                        }
                    }
                }
            }

        return null; // The actual instance is returned by SuperMethodCall
    }

    @RuntimeType
    public Object intercept(@Origin Constructor<?> constructor, @AllArguments Object[] args) throws ReflectiveOperationException {
        // For bytecode-only case, just create the instance and set the interceptor
        try {
            Object instance = constructor.newInstance(args);
            Field icpt = instance.getClass().getDeclaredField(AKBuddy.INSTANCE_INTERCEPTOR);
            icpt.setAccessible(true);
            icpt.set(instance, this);
            return instance;
        } catch (Exception e) {
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
            Field f = instance.getClass().getDeclaredField(AKBuddy.INSTANCE_INTERCEPTOR);
            f.setAccessible(true);
            MethodInterceptor parent = (MethodInterceptor) f.get(instance);
            return parent.intercept(constructor, args, sourceConstructor);
        }
    }
}
