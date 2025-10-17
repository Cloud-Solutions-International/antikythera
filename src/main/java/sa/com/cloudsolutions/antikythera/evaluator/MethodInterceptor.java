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

@SuppressWarnings({"java:S3011", "unused"})
public class MethodInterceptor {
    private EvaluationEngine evaluator;
    private Class<?> wrappedClass = Object.class;

    public MethodInterceptor(EvaluationEngine evaluator) {
        this.evaluator = evaluator;
    }

    public MethodInterceptor(Class<?> clazz) {
        wrappedClass = clazz;
    }

    @RuntimeType
    public Object intercept(@This Object instance,
                            Constructor<?> constructor, Object[] args, ConstructorDeclaration constructorDecl) throws ReflectiveOperationException {
        if (evaluator != null ) {
            if (constructorDecl != null) {
                for (int i = args.length - 1; i >= 0; i--) {
                    AntikytheraRunTime.push(new Variable(args[i]));
                }
                evaluator.executeConstructor(constructorDecl);
            }

            // Synchronize both ways: instance -> evaluator and evaluator -> instance
            synchronizeInstanceToEvaluator(instance);
            synchronizeFieldsToInstance(instance);
        }

        return null; // The actual instance is returned by SuperMethodCall
    }

    @RuntimeType
    public Object intercept(@This Object instance, Method method, Object[] args, MethodDeclaration methodDecl) throws ReflectiveOperationException {
        if (evaluator != null) {
            // For simple getters, read field value from evaluator's field map or instance
            if (args.length == 0 && isSimpleGetter(methodDecl)) {
                String fieldName = getFieldNameFromGetter(method.getName());
                
                // First try to get from evaluator
                Symbol fieldValue = evaluator.getField(fieldName);
                if (fieldValue != null) {
                    Object value = fieldValue.getValue();
                    if (value instanceof EvaluationEngine eval) {
                        MethodInterceptor interceptor = new MethodInterceptor(eval);
                        Class<?> clazz = AKBuddy.createDynamicClass(interceptor);
                        return AKBuddy.createInstance(clazz, interceptor);
                    }
                    return value;
                }
                
                // Fallback: read directly from instance field
                try {
                    Field field = instance.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field.get(instance);
                } catch (NoSuchFieldException e) {
                    // Fall through to normal execution
                }
            }
            
            // For setters, write directly to both instance and evaluator  
            if (args.length == 1 && method.getName().startsWith("set") && 
                (method.getReturnType().equals(void.class) || method.getReturnType().equals(Void.class))) {
                String fieldName = getFieldNameFromSetter(method.getName());
                try {
                    // Update instance field
                    Field field = instance.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    field.set(instance, args[0]);
                    
                    // Update evaluator field
                    evaluator.setField(fieldName, new Variable(args[0]));
                    
                    // Still execute the method body in case there's additional logic
                    if (methodDecl != null) {
                        for (int i = args.length - 1; i >= 0; i--) {
                            AntikytheraRunTime.push(new Variable(args[i]));
                        }
                        evaluator.executeMethod(methodDecl);
                    }
                    return null; // setters return void
                } catch (NoSuchFieldException e) {
                    // Fall through to normal execution
                }
            }
            
            for (int i = args.length - 1; i >= 0; i--) {
                AntikytheraRunTime.push(new Variable(args[i]));
            }

            Symbol result = evaluator.executeMethod(methodDecl);
            synchronizeFieldsToInstance(instance);

            if (result != null) {
                Object value = result.getValue();
                if (value instanceof EvaluationEngine eval) {
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

    /**
     * Synchronizes field values from instance to evaluator
     */
    @SuppressWarnings("java:S3011")
    public void synchronizeInstanceToEvaluator(Object instance) throws ReflectiveOperationException {
        if (evaluator == null) {
            return;
        }

        TypeDeclaration<?> dtoType = AntikytheraRunTime.getTypeDeclaration(evaluator.getClassName()).orElse(null);
        if (dtoType == null) {
            return;
        }

        // Iterate through all fields in the type declaration
        for (FieldDeclaration field : dtoType.getFields()) {
            String fieldName = field.getVariable(0).getNameAsString();
            try {
                Field instanceField = instance.getClass().getDeclaredField(fieldName);
                instanceField.setAccessible(true);
                Object value = instanceField.get(instance);
                
                // Only update if instance has a non-null value and evaluator doesn't
                if (value != null) {
                    Symbol existingValue = evaluator.getField(fieldName);
                    if (existingValue == null || existingValue.getValue() == null) {
                        evaluator.setField(fieldName, new Variable(value));
                    }
                }
            } catch (NoSuchFieldException e) {
                // Field doesn't exist in the dynamic class, skip it
            }
        }
    }
    
    /**
     * Synchronizes field changes from the evaluator back to the specific instance
     */
    @SuppressWarnings("java:S3011")
    public void synchronizeFieldsToInstance(Object instance) throws ReflectiveOperationException {
        if (evaluator == null) {
            return;
        }

        TypeDeclaration<?> dtoType = AntikytheraRunTime.getTypeDeclaration(evaluator.getClassName()).orElse(null);
        if (dtoType == null) {
            return;
        }

        // Iterate through all fields in the type declaration
        for (FieldDeclaration field : dtoType.getFields()) {
            String fieldName = field.getVariable(0).getNameAsString();
            Symbol evaluatorFieldValue = evaluator.getField(fieldName);

            if (evaluatorFieldValue != null) {
                try {
                    Field instanceField = instance.getClass().getDeclaredField(fieldName);
                    instanceField.setAccessible(true);

                    Object value = evaluatorFieldValue.getValue();
                    if (value instanceof EvaluationEngine eval) {
                        // Handle nested evaluator objects
                        MethodInterceptor nestedInterceptor = new MethodInterceptor(eval);
                        Class<?> nestedClass = AKBuddy.createDynamicClass(nestedInterceptor);
                        Object nestedInstance = AKBuddy.createInstance(nestedClass, nestedInterceptor);
                        instanceField.set(instance, nestedInstance);
                    } else {
                        instanceField.set(instance, value);
                    }
                } catch (NoSuchFieldException e) {
                    // Field doesn't exist in the dynamic class, skip it
                }
            }
        }
    }

    @RuntimeType
    public Object intercept(@Origin Method method, @AllArguments Object[] args) throws ReflectiveOperationException {
        Callable callable = new Callable(method, null);
        MockingCall mc = MockingRegistry.getThen(method.getDeclaringClass().getName(), callable);
        if (mc != null) {
            return mc.getVariable().getValue();
        }

        if (wrappedClass != null && !MockingRegistry.isMockTarget(wrappedClass.getName())) {
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

    public void setWrappedClass(Class<?> componentClass) {
        this.wrappedClass = componentClass;
    }

    public EvaluationEngine getEvaluator() {
        return evaluator;
    }
    
    private boolean isSimpleGetter(MethodDeclaration methodDecl) {
        String methodName = methodDecl.getNameAsString();
        if (!methodName.startsWith("get") && !methodName.startsWith("is")) {
            return false;
        }
        // Check if method body just returns a field
        if (methodDecl.getBody().isEmpty()) {
            return false;
        }
        var statements = methodDecl.getBody().get().getStatements();
        if (statements.size() != 1) {
            return false;
        }
        var stmt = statements.get(0);
        if (!stmt.isReturnStmt()) {
            return false;
        }
        var returnStmt = stmt.asReturnStmt();
        if (returnStmt.getExpression().isEmpty()) {
            return false;
        }
        var expr = returnStmt.getExpression().get();
        // Check if it's returning a field (either "name" or "this.name")
        if (expr.isNameExpr()) {
            return true;
        }
        if (expr.isFieldAccessExpr()) {
            var fae = expr.asFieldAccessExpr();
            return fae.getScope().toString().equals("this");
        }
        return false;
    }
    
    private String getFieldNameFromGetter(String methodName) {
        if (methodName.startsWith("get")) {
            String fieldName = methodName.substring(3);
            return Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
        } else if (methodName.startsWith("is")) {
            String fieldName = methodName.substring(2);
            return Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
        }
        return methodName;
    }
    
    private boolean isSimpleSetter(MethodDeclaration methodDecl) {
        String methodName = methodDecl.getNameAsString();
        if (!methodName.startsWith("set")) {
            return false;
        }
        // Check if method body just sets a field
        if (methodDecl.getBody().isEmpty()) {
            return false;
        }
        var statements = methodDecl.getBody().get().getStatements();
        if (statements.size() != 1) {
            return false;
        }
        var stmt = statements.get(0);
        if (!stmt.isExpressionStmt()) {
            return false;
        }
        var expr = stmt.asExpressionStmt().getExpression();
        if (!expr.isAssignExpr()) {
            return false;
        }
        return true;
    }
    
    private String getFieldNameFromSetter(String methodName) {
        if (methodName.startsWith("set")) {
            String fieldName = methodName.substring(3);
            return Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
        }
        return methodName;
    }

    public static class MethodDeclarationSupport {
        private final MethodDeclaration sourceMethod;

        public MethodDeclarationSupport(MethodDeclaration sourceMethod) {
            this.sourceMethod = sourceMethod;
        }

        @RuntimeType
        public Object intercept(@This Object instance, @Origin Method method, @AllArguments Object[] args) throws ReflectiveOperationException {
            // Handle setters directly first to ensure instance fields are always updated
            if (args.length == 1 && method.getName().startsWith("set") && method.getReturnType().equals(void.class)) {
                String fieldName = method.getName().substring(3);
                fieldName = Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
                try {
                    Field field = instance.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    field.set(instance, args[0]);
                } catch (NoSuchFieldException e) {
                    // Field might not exist, continue to normal processing
                }
            }
            
            Field f = instance.getClass().getDeclaredField(AKBuddy.INSTANCE_INTERCEPTOR);
            f.setAccessible(true);
            MethodInterceptor parent = (MethodInterceptor) f.get(instance);
            return parent.intercept(instance, method, args, sourceMethod);
        }
    }

    public static class ConstructorDeclarationSupport {
        private final ConstructorDeclaration sourceConstructor;

        public ConstructorDeclarationSupport(ConstructorDeclaration sourceConstructor) {
            this.sourceConstructor = sourceConstructor;
        }

        @RuntimeType
        public Object intercept(@This Object instance, @Origin Constructor<?> constructor, @AllArguments Object[] args) throws ReflectiveOperationException {
            Field f = instance.getClass().getDeclaredField(AKBuddy.INSTANCE_INTERCEPTOR);
            f.setAccessible(true);
            Evaluator eval = EvaluatorFactory.create(constructor.getDeclaringClass().getName(), SpringEvaluator.class);
            MethodInterceptor parent = new MethodInterceptor(eval);
            f.set(instance, parent);
            return parent.intercept(instance, constructor, args, sourceConstructor);
        }
    }
}
