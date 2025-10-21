package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * <p>AKBuddy builds on Byte Buddy to generate dynamic classes from sources.</p>
 *
 * Responsibilities:
 * - When source code is available via JavaParser/Evaluator, it synthesizes a new class that:
 *   - Declares fields so that reflective inspections see them.
 *   - Declares methods mirroring MethodDeclarations found in sources.
 *   - Optionally declares Lombok-style accessors when Lombok annotations (@Getter/@Setter/@Data) are present.
 *   - Intercepts all methods and routes them to MethodInterceptor.
 * - When only bytecode is available, it creates a subclass that delegates all method calls to the provided
 *   MethodInterceptor while keeping the original constructors.
 *
 * The generated classes are cached in a simple in-memory registry keyed by the fully-qualified class name in order
 * to avoid regenerating the same type multiple times within the same run.
 *
 * Usage overview:
 * - Call createDynamicClass(interceptor) to build/load the dynamic type for a target.
 * - Then call createInstance(dynamicClass, interceptor) to obtain an instance with the interceptor injected so that
 *   calls can be handled by the evaluation engine/mocking subsystem.
 *
 * Note: This class focuses on code generation wiring. The runtime behavior of method and constructor calls is
 * owned by MethodInterceptor and the associated EvaluationEngine.
 */
public class AKBuddy {
    /** The field name used in generated classes to store the per-instance interceptor. */
    public static final String INSTANCE_INTERCEPTOR = "instanceInterceptor";
    /** Cache of generated classes keyed by the source/wrapped class name. */
    private static final Map<String, Class<?>> registry = new HashMap<>();
    /** Logger for diagnostic messages during class generation. */
    private static final Logger logger = LoggerFactory.getLogger(AKBuddy.class);
    /**
     * The method name on MethodInterceptor used as the entry point for delegation. We filter on this signature to
     * avoid accidentally matching other overloads.
     */
    public static final String INTERCEPT = "intercept";

    /** Utility class; not intended to be instantiated. */
    protected AKBuddy() {
    }

    /**
     * Builds (or retrieves from cache) a dynamic subclass for the target associated with the given interceptor.
     *
     * Behavior:
     * - If the interceptor has an EvaluationEngine, we generate the subtype based on parsed source declarations
     *   so fields and methods visible to reflection mirror source code. Lombok accessors may be synthesized.
     * - Otherwise, we fall back to a bytecode-only strategy that delegates all methods to the interceptor.
     *
     * The resulting class is cached per fully-qualified name to avoid duplicate generation.
     *
     * @param interceptor the MethodInterceptor that will receive all method and constructor invocations.
     * @return the generated Class object for the dynamic type.
     * @throws ClassNotFoundException if referenced, types cannot be resolved during source-based generation.
     */
    public static Class<?> createDynamicClass(MethodInterceptor interceptor) throws ClassNotFoundException {
        EvaluationEngine eval = interceptor.getEvaluator();
        if (eval != null) {
            return createDynamicClassBasedOnSourceCode(interceptor, eval);
        } else {
            return createDynamicClassBasedOnByteCode(interceptor);
        }
    }

    /**
     * Generate a subclass using only bytecode information from the wrapped class.
     * All methods are intercepted and delegated to the provided MethodInterceptor; constructors
     * are preserved, and a private INSTANCE_INTERCEPTOR field is defined for per-instance routing.
     *
     * @param interceptor the interceptor bound to the generated subclass.
     * @return the generated and loaded class (cached on subsequent calls).
     */
    private static Class<?> createDynamicClassBasedOnByteCode(MethodInterceptor interceptor) {
        Class<?> wrappedClass = interceptor.getWrappedClass();
        Class<?> existing = registry.get(wrappedClass.getName());
        if (existing != null) {
            return existing;
        }

        ByteBuddy byteBuddy = new ByteBuddy();

        Class<?> clazz = byteBuddy.subclass(wrappedClass)
                .method(ElementMatchers.any())
                .intercept(MethodDelegation.withDefaultConfiguration()
                        .filter(ElementMatchers.named(INTERCEPT)
                                .and(ElementMatchers.takesArguments(Method.class, Object[].class)))
                        .to(interceptor))
                .constructor(ElementMatchers.any())
                .intercept(net.bytebuddy.implementation.SuperMethodCall.INSTANCE)
                .defineField(INSTANCE_INTERCEPTOR, MethodInterceptor.class, Visibility.PRIVATE)
                .make()
                .load(AbstractCompiler.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                .getLoaded();

        registry.put(wrappedClass.getName(), clazz);
        return clazz;
    }

    /**
     * Adds constructor interception for the target type.
     * <ul>
     *   <li>If no constructors are declared in source, the implicit default constructor is intercepted.</li>
     *   <li>For explicitly declared constructors, signatures are resolved and individually intercepted.</li>
     * </ul>
     * Interception delegates to MethodInterceptor.ConstructorDeclarationSupport so tests/mocking can
     * observe construction.
     *
     * @param dtoType the source-level type declaration.
     * @param cu the compilation unit for type/method resolution.
     * @param builder the current Byte Buddy builder chain.
     * @return updated builder with constructor interception applied.
     */
    private static DynamicType.Builder<?> addConstructors(TypeDeclaration<?> dtoType, CompilationUnit cu,
                                                          DynamicType.Builder<?> builder) {
        List<com.github.javaparser.ast.body.ConstructorDeclaration> constructors = dtoType.getConstructors();

        // If no constructors are explicitly declared, we need to intercept the implicit default constructor
        if (constructors.isEmpty()) {
            // Create a synthetic default constructor declaration for the interceptor
            com.github.javaparser.ast.body.ConstructorDeclaration defaultConstructor =
                    new com.github.javaparser.ast.body.ConstructorDeclaration();
            defaultConstructor.setName(dtoType.getNameAsString());

            // Don't define a new constructor, just intercept the existing default one
            builder = interceptConstructor(builder, defaultConstructor);
        } else {
            // Handle explicitly declared constructors
            for (com.github.javaparser.ast.body.ConstructorDeclaration constructor : constructors) {
                Class<?>[] parameterTypes = constructor.getParameters().stream()
                        .map(p -> getParameterType(cu, p))
                        .toArray(Class<?>[]::new);

                builder = interceptConstructor(builder, constructor, parameterTypes);
            }
        }

        return builder;
    }

    /**
     * Helper to apply constructor interception for a specific constructor signature.
     * If no parameter types are supplied, the existing default constructor is intercepted; otherwise a
     * new constructor with the supplied signature is defined and intercepted.
     *
     * @param builder current builder chain.
     * @param constructor the source constructor declaration this interception represents.
     * @param parameterTypes resolved parameter types; empty for default constructor.
     * @return updated builder with interception configured.
     * @throws AntikytheraException if base Object constructor cannot be resolved (should not happen).
     */
    private static DynamicType.Builder<?> interceptConstructor(DynamicType.Builder<?> builder,
                                                               com.github.javaparser.ast.body.ConstructorDeclaration constructor,
                                                               Class<?>... parameterTypes) {
        try {
            if (parameterTypes == null || parameterTypes.length == 0) {
                return builder.constructor(ElementMatchers.takesArguments(0))
                        .intercept(MethodCall.invoke(Object.class.getDeclaredConstructor()).andThen(
                                MethodDelegation.to(new MethodInterceptor.ConstructorDeclarationSupport(constructor))));
            } else {
                return builder.defineConstructor(Visibility.PUBLIC)
                        .withParameters(parameterTypes)
                        .intercept(MethodCall.invoke(Object.class.getDeclaredConstructor()).andThen(
                                MethodDelegation.to(new MethodInterceptor.ConstructorDeclarationSupport(constructor))));
            }
        } catch (NoSuchMethodException e) {
            throw new AntikytheraException(e);
        }
    }

    /**
     * Defines a public method on the Byte Buddy builder and wires it to delegate to the provided
     * MethodDeclarationSupport instance through MethodInterceptor.
     *
     * @param builder the current builder chain.
     * @param methodName name of the method to define.
     * @param returnType resolved return type.
     * @param support adapter that encapsulates the JavaParser MethodDeclaration for delegation.
     * @param parameterTypes zero or more parameter types for the method.
     * @return updated builder with the defined method.
     */
    private static DynamicType.Builder<?> defineInterceptedMethod(DynamicType.Builder<?> builder,
                                                                  String methodName,
                                                                  Class<?> returnType,
                                                                  MethodInterceptor.MethodDeclarationSupport support,
                                                                  Class<?>... parameterTypes) {
        var methodDef = builder.defineMethod(methodName, returnType, Visibility.PUBLIC);
        var implDef = (parameterTypes != null && parameterTypes.length > 0)
                ? methodDef.withParameters(parameterTypes)
                : methodDef;
        return implDef.intercept(MethodDelegation.withDefaultConfiguration()
                .filter(ElementMatchers.named(INTERCEPT))
                .to(support));
    }

    /**
     * Generates a subclass based on parsed source code for the target type. Fields, methods, implemented interfaces,
     * constructors, and Lombok-derived accessors are synthesized so reflective consumers see a shape that matches
     * the source. All invocations delegate to the provided MethodInterceptor.
     *
     * @param interceptor the interceptor that will handle all invocations.
     * @param eval evaluation engine holding the CompilationUnit and class meta.
     * @return the generated and loaded class (cached).
     * @throws ClassNotFoundException if referenced types in source cannot be resolved.
     */
    private static Class<?> createDynamicClassBasedOnSourceCode(MethodInterceptor interceptor, EvaluationEngine eval) throws ClassNotFoundException {
        Class<?> existing = registry.get(eval.getClassName());
        if (existing != null) {
            return existing;
        }
        CompilationUnit cu = ((Evaluator) eval).getCompilationUnit();
        TypeDeclaration<?> dtoType = AntikytheraRunTime.getTypeDeclaration(eval.getClassName()).orElseThrow();
        String className = eval.getClassName();

        List<FieldDeclaration> fields = dtoType.getFields();

        ByteBuddy byteBuddy = new ByteBuddy();
        DynamicType.Builder<?> builder = byteBuddy.subclass(interceptor.getWrappedClass()).name(className)
                .method(ElementMatchers.any())
                .intercept(MethodDelegation.to(interceptor))
                .defineField(INSTANCE_INTERCEPTOR, MethodInterceptor.class, Visibility.PRIVATE);

        if (dtoType instanceof ClassOrInterfaceDeclaration cdecl) {
            for (ClassOrInterfaceType iface : cdecl.getImplementedTypes()) {
                TypeWrapper wrapper = AbstractCompiler.findType(((Evaluator) eval).getCompilationUnit(), iface.getNameAsString());
                if (wrapper != null && wrapper.getClazz() != null) {
                    builder = builder.implement(wrapper.getClazz());
                }
            }
        }

        builder = addConstructors(dtoType, cu, builder); // Add this line
        builder = addFields(fields, cu, builder);
        builder = addMethods(dtoType.getMethods(), cu, builder);
        builder = addLombokAccessors(dtoType, cu, builder);

        DynamicType.Unloaded<?> unloaded = builder.make();

        try {
            Class<?> clazz = unloaded.load(AbstractCompiler.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                    .getLoaded();
            registry.put(eval.getClassName(), clazz);
            return clazz;
        } catch (IllegalStateException e) {
            Class<?> clazz = AbstractCompiler.loadClass(eval.getClassName());
            registry.put(eval.getClassName(), clazz);
            return clazz;
        }
    }

    /**
     * Defines methods discovered in the source TypeDeclaration, wiring each to the interceptor.
     * Parameter and return types are resolved against the CompilationUnit.
     *
     * @param methods list of source-level method declarations.
     * @param cu compilation unit used for type resolution.
     * @param builder the current Byte Buddy builder chain.
     * @return updated builder with defined methods.
     */
    private static DynamicType.Builder<?> addMethods(List<MethodDeclaration> methods, CompilationUnit cu,
                                                     DynamicType.Builder<?> builder) {

        for (MethodDeclaration method : methods) {
            String methodName = method.getNameAsString();

            // Get parameter types
            Class<?>[] parameterTypes = method.getParameters().stream()
                    .map(p -> getParameterType(cu, p))
                    .toArray(Class<?>[]::new);

            // Get return type
            Class<?> returnType = getReturnType(cu, method);

            builder = defineInterceptedMethod(builder,
                            methodName,
                            returnType,
                            new MethodInterceptor.MethodDeclarationSupport(method),
                            parameterTypes);
        }
        return builder;
    }

    /**
     * Resolves a JavaParser Parameter to a runtime Class, including arrays and primitives.
     * Falls back to Object.class if resolution fails to keep generation resilient.
     *
     * @param cu compilation unit for name resolution.
     * @param p the parameter to resolve.
     * @return the resolved Class for the parameter type, or Object.class on failure.
     */
    private static Class<?> getParameterType(CompilationUnit cu, Parameter p) {
        try {
            if (p.getType().isArrayType()) {
                // Get the element type without [] suffix
                Type elementType = p.getType().asArrayType().getElementType();

                Class<?> componentType;
                if (elementType.isPrimitiveType()) {
                    componentType = Reflect.getComponentClass(elementType.asString());
                } else {
                    String fullName = AbstractCompiler.findFullyQualifiedName(cu, elementType.asString());
                    componentType = Reflect.getComponentClass(fullName);
                }

                // Create an empty array of the correct type
                return Array.newInstance(componentType, 0).getClass();

            }

            if (p.getType().isPrimitiveType()) {
                return Reflect.getComponentClass(p.getTypeAsString());
            }

            TypeWrapper t;
            if (p.getType() instanceof ClassOrInterfaceType ctype && ctype.getTypeArguments().isPresent()) {
                t = AbstractCompiler.findType(cu, ctype.getNameAsString());
            } else {
                t = AbstractCompiler.findType(cu, p.getType().asString());
            }
            if (t.getClazz() != null) {
                return t.getClazz();
            }
            return Reflect.getComponentClass(t.getFullyQualifiedName());
        } catch (ClassNotFoundException e) {
            /*
             * TODO : fix this temporary hack.
             * Lots of functions will actually fail to evaluate due to returning an object.class however
             * the program will not crash.
             */
            return Object.class;
        }
    }

    /**
     * Defines fields from the source TypeDeclaration so that reflection can see them on the generated class.
     * Field annotations (except Lombok) are copied when available in binary form.
     *
     * @param fields source-level fields to define.
     * @param cu compilation unit used for type resolution.
     * @param builder current Byte Buddy builder chain.
     * @return updated builder with defined fields.
     * @throws ClassNotFoundException if a field type cannot be resolved.
     */
    private static DynamicType.Builder<?> addFields(List<FieldDeclaration> fields, CompilationUnit cu, DynamicType.Builder<?> builder) throws ClassNotFoundException {
        for (FieldDeclaration field : fields) {
            VariableDeclarator vd = field.getVariable(0);
            String fieldName = vd.getNameAsString();

            Class<?> fieldType;
            if (vd.getType().isPrimitiveType()) {
                fieldType = Reflect.getComponentClass(vd.getTypeAsString());
            } else {
                TypeWrapper wrapper = AbstractCompiler.findType(cu, vd.getType());
                if (wrapper != null && wrapper.getClazz() != null) {
                    fieldType = wrapper.getClazz();
                } else {
                    fieldType = Object.class;
                }
            }

            builder = addFieldAnnotations(cu, builder, field, fieldName, fieldType);
        }
        return builder;
    }

    @SuppressWarnings("unchecked")
    /**
     * Applies non-Lombok field annotations to the defined field when the annotation types are available at runtime.
     * Invalid/unsupported annotations are skipped with a warning rather than failing generation.
     *
     * @param cu compilation unit for annotation type resolution.
     * @param builder current builder chain.
     * @param field the source field.
     * @param fieldName simple name of the field.
     * @param fieldType resolved field type.
     * @return updated builder possibly with annotations applied.
     */
    private static DynamicType.Builder<?> addFieldAnnotations(CompilationUnit cu, DynamicType.Builder<?> builder, FieldDeclaration field, String fieldName, Class<?> fieldType) {
        DynamicType.Builder.FieldDefinition.Optional.Valuable<?> fieldDef = builder.defineField(fieldName, fieldType, Visibility.PRIVATE);
        builder = fieldDef;
        // Add binary annotations except Lombok
        for (var ann : field.getAnnotations()) {
            TypeWrapper annType = AbstractCompiler.findType(cu, ann.getNameAsString());
            if (annType != null && annType.getClazz() != null) {
                String annFqn = annType.getFullyQualifiedName();
                if (annFqn != null && !annFqn.startsWith("lombok.")) {
                    try {
                        builder = fieldDef.annotateField(
                                AnnotationDescription.Builder.ofType((Class<? extends Annotation>) annType.getClazz()).build()
                        );
                    } catch (IllegalStateException e) {
                        logger.warn("Could not add annotation {} to field {}: {}",
                                annType.getFullyQualifiedName(), fieldName, e.getMessage());
                    }
                }
            }
        }
        return builder;
    }

    /**
     * Adds Lombok-derived getters and setters when the class/field has @Getter, @Setter or @Data annotations
     * and no explicit method with the same name exists. These synthetic methods are delegated through the
     * same interception pipeline as regular methods.
     *
     * @param dtoType the source-level type declaration.
     * @param cu compilation unit for resolving field types.
     * @param builder the current Byte Buddy builder chain.
     * @return updated builder with Lombok-style accessors defined when applicable.
     */
    private static DynamicType.Builder<?> addLombokAccessors(TypeDeclaration<?> dtoType, CompilationUnit cu, DynamicType.Builder<?> builder) {
        boolean classHasGetter = dtoType.getAnnotationByName("Getter").isPresent();
        boolean classHasSetter = dtoType.getAnnotationByName("Setter").isPresent();
        boolean classHasData = dtoType.getAnnotationByName("Data").isPresent();

        for (FieldDeclaration field : dtoType.getFields()) {
            VariableDeclarator vd = field.getVariable(0);
            String fieldName = vd.getNameAsString();
            String capitalized = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            String getterName = (vd.getType().asString().equals("boolean") ? "is" : "get") + capitalized;
            String setterName = "set" + capitalized;
            boolean fieldHasGetter = field.getAnnotationByName("Getter").isPresent();
            boolean fieldHasSetter = field.getAnnotationByName("Setter").isPresent();
            boolean needGetter = classHasGetter || classHasData || fieldHasGetter;
            boolean needSetter = classHasSetter || classHasData || fieldHasSetter;

            // Only add Lombok getter if not already present in explicit methods
            // Explicit methods use MethodDeclarationSupport which goes through the interceptor
            if (needGetter && dtoType.getMethods().stream().noneMatch(m -> m.getNameAsString().equals(getterName))) {
                Class<?> returnType = getFieldType(cu, vd);
                // Create a synthetic MethodDeclaration for Lombok getters
                com.github.javaparser.ast.body.MethodDeclaration syntheticGetter = 
                        new com.github.javaparser.ast.body.MethodDeclaration();
                syntheticGetter.setName(getterName);
                syntheticGetter.setType(vd.getType());
                com.github.javaparser.ast.stmt.BlockStmt body = new com.github.javaparser.ast.stmt.BlockStmt();
                body.addStatement("return this." + fieldName + ";");
                syntheticGetter.setBody(body);
                
                builder = defineInterceptedMethod(builder,
                        getterName,
                        returnType,
                        new MethodInterceptor.MethodDeclarationSupport(syntheticGetter));
            }
            // Only add Lombok setter if not already present and field is not final
            if (needSetter && !field.isFinal() && dtoType.getMethods().stream().noneMatch(m -> m.getNameAsString().equals(setterName))) {
                Class<?> paramType = getFieldType(cu, vd);
                // Create a synthetic MethodDeclaration for Lombok setters
                com.github.javaparser.ast.body.MethodDeclaration syntheticSetter = 
                        new com.github.javaparser.ast.body.MethodDeclaration();
                syntheticSetter.setName(setterName);
                syntheticSetter.setType(new com.github.javaparser.ast.type.VoidType());
                syntheticSetter.addParameter(vd.getType(), fieldName);
                com.github.javaparser.ast.stmt.BlockStmt setterBody = new com.github.javaparser.ast.stmt.BlockStmt();
                setterBody.addStatement("this." + fieldName + " = " + fieldName + ";");
                syntheticSetter.setBody(setterBody);
                
                builder = defineInterceptedMethod(builder,
                        setterName,
                        void.class,
                        new MethodInterceptor.MethodDeclarationSupport(syntheticSetter),
                        paramType);
            }
        }
        return builder;
    }

    /**
     * Resolves a field's declared type to a runtime Class, defaulting to Object.class if resolution fails.
     *
     * @param cu compilation unit used for resolving type names.
     * @param vd the variable declarator representing the field.
     * @return resolved field type or Object.class when unavailable.
     */
    private static Class<?> getFieldType(CompilationUnit cu, VariableDeclarator vd) {
        try {
            if (vd.getType().isPrimitiveType()) {
                return Reflect.getComponentClass(vd.getTypeAsString());
            }
            TypeWrapper wrapper = AbstractCompiler.findType(cu, vd.getType());
            if (wrapper != null && wrapper.getClazz() != null) {
                return wrapper.getClazz();
            }
            return Object.class;
        } catch (Exception e) {
            return Object.class;
        }
    }

    /**
     * Resolves a method's declared return type to a runtime Class, including primitives and void.
     * Defaults to Object.class when resolution fails to keep generation tolerant of missing types.
     *
     * @param cu compilation unit used for resolving type names.
     * @param method the source-level method declaration.
     * @return resolved return type class, void.class for void, or Object.class on failure.
     */
    private static Class<?> getReturnType(CompilationUnit cu, MethodDeclaration method) {
        try {
            Type returnType = method.getType();
            if (returnType.isVoidType()) {
                return void.class;
            }
            if (returnType.isPrimitiveType()) {
                return Reflect.getComponentClass(returnType.asString());
            }
            TypeWrapper wrapper = AbstractCompiler.findType(cu, returnType);
            if (wrapper != null && wrapper.getClazz() != null) {
                return wrapper.getClazz();
            }
            return Object.class;
        } catch (Exception e) {
            return Object.class;
        }
    }

    @SuppressWarnings("java:S3011")
    /**
     * Creates an instance of the previously generated class and injects the provided MethodInterceptor into
     * the private INSTANCE_INTERCEPTOR field so that subsequent calls are routed correctly. Also synchronizes
     * seed field values from the interceptor to the instance.
     *
     * @param dynamicClass a class previously created by createDynamicClass.
     * @param interceptor the interceptor to attach to the new instance.
     * @return a new instance with the interceptor injected.
     * @throws ReflectiveOperationException if the default constructor or field injection fails.
     */
    public static Object createInstance(Class<?> dynamicClass, MethodInterceptor interceptor) throws ReflectiveOperationException {
        Object instance = dynamicClass.getDeclaredConstructor().newInstance();
        Field icpt = instance.getClass().getDeclaredField(INSTANCE_INTERCEPTOR);
        icpt.setAccessible(true);
        icpt.set(instance, interceptor);
        interceptor.synchronizeFieldsToInstance(instance);
        return instance;
    }
}
