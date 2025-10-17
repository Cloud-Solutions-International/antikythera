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

/**
 * Intercepts constructor and method invocations on Byte Buddy–generated instances and forwards them to
 * the Antikythera evaluation engine.
 * <p>
 * Responsibilities:
 * - Bridges between JavaParser AST declarations (MethodDeclaration/ConstructorDeclaration) and the binary classes.
 * - Keeps state consistent between the generated instance and the evaluator by synchronizing fields in both directions.
 * - Respects mocking/stubbing configured via MockingRegistry.
 * - Provides a fallback path that can invoke a real wrapped class when no evaluator is present.
 * <p>
 * Usage:
 * - For expression/test evaluation, construct with an EvaluationEngine. AKBuddy wires this interceptor into the
 * generated class and calls the appropriate intercept methods.
 * - For plain reflective fallback or library methods, construct with a wrapped Class to delegate to.
 * <p>
 * Thread-safety:
 * - This interceptor holds per-instance state only through the evaluator reference; synchronization operations use
 * reflection on the provided instance. It is not designed for concurrent mutation of the same instance/evaluator
 * without external synchronization.
 * <p>
 * See also: AKBuddy, EvaluationEngine, SpringEvaluator, MockingRegistry.
 */
public class MethodInterceptor {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MethodInterceptor.class);
    private final EvaluationEngine evaluator;
    private Class<?> wrappedClass = Object.class;

    /**
     * Creates an interceptor that forwards calls to the supplied evaluation engine.
     *
     * @param evaluator the evaluation engine used to execute constructors and methods and maintain field state
     */
    public MethodInterceptor(EvaluationEngine evaluator) {
        this.evaluator = evaluator;
    }

    /**
     * Creates an interceptor that delegates to a concrete wrapped class rather than to an evaluator.
     * This path is used when executing non-mocked methods directly via reflection.
     *
     * @param clazz the class to delegate method invocations to when no evaluator is present
     */
    public MethodInterceptor(Class<?> clazz) {
        this.evaluator = null;
        this.wrappedClass = clazz;
    }

    /**
     * Intercepts a constructor invocation on the generated instance and mirrors it through the evaluation engine.
     * <p>
     * Behavior when an evaluator is present:
     * - Pushes constructor arguments onto the Antikythera runtime stack.
     * - Executes the provided ConstructorDeclaration inside the evaluator.
     * - Synchronizes instance field values to the evaluator and then evaluator field values back to the instance,
     * ensuring both sides see a consistent state after construction.
     * <p>
     * Behavior when no evaluator is present:
     * - Returns null; the actual instance creation is typically handled by Byte Buddy (e.g., SuperMethodCall).
     *
     * @param instance        the newly created proxy/instance receiving the constructor call
     * @param constructor     the reflective constructor being invoked on the generated type
     * @param args            the constructor arguments
     * @param constructorDecl the JavaParser AST node for the source constructor (may be null)
     * @return always null because instance creation is performed by the underlying Byte Buddy implementation
     * @throws ReflectiveOperationException if reflection or evaluator execution fails
     */
    @RuntimeType
    public Object intercept(@This Object instance,
                            Constructor<?> constructor, Object[] args, ConstructorDeclaration constructorDecl) throws ReflectiveOperationException {
        if (evaluator != null) {
            if (constructorDecl != null) {
                pushArgs(args);
                evaluator.executeConstructor(constructorDecl);
            }

            // Synchronize both ways: instance -> evaluator and evaluator -> instance
            synchronizeInstanceToEvaluator(instance);
            synchronizeFieldsToInstance(instance);
        }

        return null; // The actual instance is returned by SuperMethodCall
    }

    /**
     * Intercepts an instance method invocation and executes it through the evaluation engine when available.
     * <p>
     * Optimization:
     * - Simple getters (getX()/isX()) fetch directly from the evaluator field map if present, otherwise fall back
     * to the actual instance field via reflection.
     * - Simple setters write through to both the instance field and the evaluator field map, and still execute the
     * method body when a MethodDeclaration is provided.
     * <p>
     * General path:
     * - Pushes arguments onto the Antikythera runtime stack and executes the method via the evaluator.
     * - Synchronizes evaluator field values back to the instance after the call.
     * - Wraps returned evaluator values as dynamic instances when a nested EvaluationEngine is encountered.
     *
     * @param instance   the proxy/instance receiving the call
     * @param method     the reflective method on the generated type
     * @param args       the invocation arguments
     * @param methodDecl the JavaParser AST node for the source method (may be null)
     * @return the returned value or null for void methods
     * @throws ReflectiveOperationException if reflection or evaluator execution fails
     */
    @RuntimeType
    @SuppressWarnings("java:S3011")
    public Object intercept(@This Object instance, Method method, Object[] args, MethodDeclaration methodDecl) throws ReflectiveOperationException {
        if (evaluator == null) {
            return intercept(method, args);
        }
        // For simple getters, read field value from evaluator's field map or instance
        if (args.length == 0 && methodDecl != null && isSimpleGetter(methodDecl)) {
            String fieldName = getFieldNameFromGetter(method.getName());

            // First try to get from evaluator
            Symbol fieldValue = evaluator.getField(fieldName);
            if (fieldValue != null) {
                return wrapIfEvaluator(fieldValue.getValue());
            }

            // Fallback: read directly from instance field
            try {
                Field field = instance.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(instance);
            } catch (NoSuchFieldException e) {
                logger.debug("Getter fallback: field '{}' not found on {}", fieldName, instance.getClass().getName());
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
                    pushArgs(args);
                    evaluator.executeMethod(methodDecl);
                }
                return null; // setters return void
            } catch (NoSuchFieldException e) {
                logger.debug("Setter fallback: field '{}' not found on {}", fieldName, instance.getClass().getName());
            }
        }

        pushArgs(args);

        Symbol result = evaluator.executeMethod(methodDecl);
        synchronizeFieldsToInstance(instance);

        if (result != null) {
            return wrapIfEvaluator(result.getValue());
        }
        return null;

    }

    /**
     * Synchronizes field values from the provided instance into the evaluator.
     * <p>
     * Only non-null instance fields are copied, and they overwrite missing/null values in the evaluator’s
     * symbol table. Fields are discovered using the JavaParser TypeDeclaration associated with the evaluator’s
     * class name, and values are read via reflection from the instance.
     *
     * @param instance the concrete instance whose field values should seed the evaluator
     * @throws ReflectiveOperationException if reflection access fails
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
                logger.debug("Sync to evaluator: field '{}' not found on {} (skipping)", fieldName, instance.getClass().getName());
            }
        }
    }

    /**
     * Synchronizes field changes from the evaluator back to the provided instance.
     * <p>
     * For each field declared in the parsed type, this reads the evaluator’s value and writes it to the instance via
     * reflection. If a field value is itself an EvaluationEngine (nested object), a dynamic proxy instance is created
     * via AKBuddy so callers interact with a concrete object while evaluation continues to be routed through the engine.
     *
     * @param instance the concrete instance to receive evaluator field values
     * @throws ReflectiveOperationException if reflection access fails
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
                    logger.debug("Sync to instance: field '{}' not found on {} (skipping)", fieldName, instance.getClass().getName());
                }
            }
        }
    }

    /**
     * Intercepts a method invocation when no evaluator has been provided and returns a value based on
     * stubbing, delegation, or defaulting:
     * <p>
     * Order of resolution:
     * 1) If a stub is registered in MockingRegistry for this callable, returns the stubbed value.
     * 2) If a wrappedClass is set and is not marked as a mock target, reflectively invokes the actual method
     * on a new instance (or statically for static methods).
     * 3) Otherwise, returns a default value for the return type (Reflect.getDefault) or null for void.
     *
     * @param method the reflective method being invoked on the generated type
     * @param args   the invocation arguments
     * @return the stubbed value, the delegated reflective result, or a default value
     * @throws ReflectiveOperationException if reflective invocation fails
     */
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
                logger.debug("Wrapped class '{}' does not declare method '{}' with given signature", wrappedClass.getName(), method.getName());
            }
        }

        Class<?> clazz = method.getReturnType();
        if (clazz.equals(void.class)) {
            return null;
        }
        return Reflect.getDefault(clazz);
    }

    /**
     * Returns the concrete class that this interceptor may delegate to when no evaluator is present.
     *
     * @return the wrapped class, or Object.class by default
     */
    public Class<?> getWrappedClass() {
        return wrappedClass;
    }

    /**
     * Sets the concrete class that this interceptor may delegate to when no evaluator is present.
     *
     * @param componentClass the class to delegate reflective invocations to
     */
    public void setWrappedClass(Class<?> componentClass) {
        this.wrappedClass = componentClass;
    }

    /**
     * Returns the evaluation engine used to execute constructors and methods and to hold field values.
     *
     * @return the EvaluationEngine instance, or null when this interceptor was constructed with a wrapped class
     */
    public EvaluationEngine getEvaluator() {
        return evaluator;
    }

    /**
     * Pushes the provided arguments onto the Antikythera runtime stack in reverse order so that
     * the callee pops them in the natural left-to-right order.
     *
     * @param args the invocation arguments; no-op when null or empty
     */
    private void pushArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return;
        }
        for (int i = args.length - 1; i >= 0; i--) {
            AntikytheraRunTime.push(new Variable(args[i]));
        }
    }

    /**
     * Wraps a nested EvaluationEngine return value in a dynamically generated class so callers
     * can interact with it as with a regular object while calls continue to be intercepted.
     *
     * @param value a value potentially produced by the evaluator
     * @return a concrete proxy instance if the value is an EvaluationEngine; otherwise the value itself
     * @throws ReflectiveOperationException if dynamic class creation or instantiation fails
     */
    private Object wrapIfEvaluator(Object value) throws ReflectiveOperationException {
        if (value instanceof EvaluationEngine eval) {
            MethodInterceptor interceptor = new MethodInterceptor(eval);
            Class<?> clazz = AKBuddy.createDynamicClass(interceptor);
            return AKBuddy.createInstance(clazz, interceptor);
        }
        return value;
    }

    /**
     * Heuristically determines whether the provided MethodDeclaration is a simple getter.
     * A simple getter is defined as a zero-argument method whose body consists solely of a return statement
     * that returns a field (either name or this.name), and whose name starts with get or is.
     *
     * @param methodDecl the JavaParser AST node for the method under inspection (non-null)
     * @return true if the method appears to be a simple getter; false otherwise
     */
    @SuppressWarnings("java:S3655")
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

    /**
     * Derives a field name from a JavaBean-style getter method name.
     * Examples: getName -> name, isActive -> active.
     *
     * @param methodName the method name starting with get or is
     * @return the inferred field name; if the name does not start with get/is, returns the original name
     */
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

    /**
     * Derives a field name from a JavaBean-style setter method name.
     * Example: setName -> name.
     *
     * @param methodName the method name starting with set
     * @return the inferred field name; if the name does not start with set, returns the original name
     */
    private String getFieldNameFromSetter(String methodName) {
        if (methodName.startsWith("set")) {
            String fieldName = methodName.substring(3);
            return Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
        }
        return methodName;
    }

    /**
     * Byte Buddy helper that carries a MethodDeclaration (AST) alongside the generated instance, so that
     * when the intercept method is triggered we can forward to the parent MethodInterceptor with full context.
     */
    public static class MethodDeclarationSupport {
        private final MethodDeclaration sourceMethod;

        public MethodDeclarationSupport(MethodDeclaration sourceMethod) {
            this.sourceMethod = sourceMethod;
        }

        /**
         * Byte Buddy entry point for instance methods when a MethodDeclaration is available.
         * It performs minimal setter handling to keep instance fields up to date, then delegates to the
         * parent MethodInterceptor with the AST node so the evaluator can execute the method body.
         *
         * @param instance the generated instance receiving the call
         * @param method   the reflective method being invoked
         * @param args     the invocation arguments
         * @return the result produced by the parent MethodInterceptor
         * @throws ReflectiveOperationException if reflective access fails
         */
        @RuntimeType
        @SuppressWarnings("java:S3011")
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
                    logger.debug("MethodDeclarationSupport: field '{}' not found on {} (setter path)", fieldName, instance.getClass().getName());
                }
            }

            Field f = instance.getClass().getDeclaredField(AKBuddy.INSTANCE_INTERCEPTOR);
            f.setAccessible(true);
            MethodInterceptor parent = (MethodInterceptor) f.get(instance);
            return parent.intercept(instance, method, args, sourceMethod);
        }
    }

    /**
     * Byte Buddy helper that carries a ConstructorDeclaration (AST) for constructor interception, allowing
     * the parent MethodInterceptor to execute constructor logic within the evaluator during instance creation.
     */
    public static class ConstructorDeclarationSupport {
        private final ConstructorDeclaration sourceConstructor;

        public ConstructorDeclarationSupport(ConstructorDeclaration sourceConstructor) {
            this.sourceConstructor = sourceConstructor;
        }

        /**
         * Byte Buddy entry point for constructors when a ConstructorDeclaration is available.
         * It initializes a new MethodInterceptor with a fresh evaluator and installs it on the instance,
         * then forwards to the parent’s constructor interception path to perform evaluation and synchronization.
         *
         * @param instance    the newly created proxy/instance
         * @param constructor the reflective constructor being invoked
         * @param args        the constructor arguments
         * @return always null; instance creation is handled by Byte Buddy’s constructor strategy
         * @throws ReflectiveOperationException if reflective access or evaluator wiring fails
         */
        @RuntimeType
        @SuppressWarnings("java:S3011")
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
