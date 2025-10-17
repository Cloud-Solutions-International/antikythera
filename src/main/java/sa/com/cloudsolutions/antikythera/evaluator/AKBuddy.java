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
 * Create fake objects using Byte Buddy for DTOs, Entities and possibly other types of classes.
 * This will be primarily used in mocking.
 */
public class AKBuddy {
    public static final String INSTANCE_INTERCEPTOR = "instanceInterceptor";
    private static final Map<String, Class<?>> registry = new HashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(AKBuddy.class);
    public static final String INTERCEPT = "intercept";

    protected AKBuddy() {
    }

    /**
     * <p>Dynamically create a class matching a given type declaration and then create an instance</p>
     * <p>
     * We will iterate through all the fields declared in the source code and make fake fields
     * accordingly so that they show up in reflective inspections. Similarly, we will add fake
     * methods for all the method declarations in the source code
     *
     * @param interceptor the MethodInterceptor to be used for the dynamic class.
     * @return an instance of the class that was faked.
     * @throws ClassNotFoundException If an error occurs during reflection operations.
     */
    public static Class<?> createDynamicClass(MethodInterceptor interceptor) throws ClassNotFoundException {
        EvaluationEngine eval = interceptor.getEvaluator();
        if (eval != null) {
            return createDynamicClassBasedOnSourceCode(interceptor, eval);
        } else {
            return createDynamicClassBasedOnByteCode(interceptor);
        }
    }

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
     * Add getter/setter methods for fields based on Lombok annotations (@Getter, @Setter, @Data)
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
    public static Object createInstance(Class<?> dynamicClass, MethodInterceptor interceptor) throws ReflectiveOperationException {
        Object instance = dynamicClass.getDeclaredConstructor().newInstance();
        Field icpt = instance.getClass().getDeclaredField(INSTANCE_INTERCEPTOR);
        icpt.setAccessible(true);
        icpt.set(instance, interceptor);
        interceptor.synchronizeFieldsToInstance(instance);
        return instance;
    }
}
