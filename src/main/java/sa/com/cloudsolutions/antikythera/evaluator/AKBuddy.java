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
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Create fake objects using Byte Buddy for DTOs, Entities and possibly other types of classes.
 * This will be primarily used in mocking.
 */
public class AKBuddy {
    private static final Map<String, Class<?>> registry = new HashMap<>();
    public static final String INSTANCE_INTERCEPTOR = "instanceInterceptor";

    protected AKBuddy() {}

    /**
     * <p>Dynamically create a class matching a given type declaration and then create an instance</p>
     *
     * We will iterate through all the fields declared in the source code and make fake fields
     * accordingly so that they show up in reflective inspections. Similarly, we will add fake
     * methods for all the method declarations in the source code
     *
     * @param interceptor the MethodInterceptor to be used for the dynamic class.
     * @return an instance of the class that was faked.
     * @throws ClassNotFoundException If an error occurs during reflection operations.
     */
    public static Class<?> createDynamicClass(MethodInterceptor interceptor) throws ClassNotFoundException {
        Evaluator eval = interceptor.getEvaluator();
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
                .method(ElementMatchers.not(
                        ElementMatchers.isDeclaredBy(Object.class)
                                .or(ElementMatchers.isDeclaredBy(com.fasterxml.jackson.core.ObjectCodec.class))
                                .or(ElementMatchers.isDeclaredBy(com.fasterxml.jackson.databind.ObjectMapper.class))
                ))
                .intercept(MethodDelegation.to(interceptor))
                .defineField(INSTANCE_INTERCEPTOR, MethodInterceptor.class, Visibility.PRIVATE)
                .make()
                .load(AbstractCompiler.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                .getLoaded();

        registry.put(wrappedClass.getName(), clazz);
        return clazz;
    }

    private static Class<?> createDynamicClassBasedOnSourceCode(MethodInterceptor interceptor, Evaluator eval) throws ClassNotFoundException {
        Class<?> existing = registry.get(eval.getClassName());
        if (existing != null) {
            return existing;
        }
        CompilationUnit cu = eval.getCompilationUnit();
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
                TypeWrapper wrapper = AbstractCompiler.findType(eval.getCompilationUnit(), iface.getNameAsString());
                if (wrapper != null && wrapper.getClazz() != null) {
                    builder = builder.implement(wrapper.getClazz());
                }
            }
        }

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
                                                     DynamicType.Builder<?> builder)  {

        for (MethodDeclaration method : methods) {
            String methodName = method.getNameAsString();

            // Get parameter types
            Class<?>[] parameterTypes = method.getParameters().stream()
                    .map(p -> getParameterType(cu, p))
                    .toArray(Class<?>[]::new);


            builder = builder.defineMethod(methodName,
                            Object.class,
                            net.bytebuddy.description.modifier.Visibility.PUBLIC)
                    .withParameters(parameterTypes)
                    .intercept(MethodDelegation.withDefaultConfiguration()
                            .filter(ElementMatchers.named("intercept"))
                            .to(new MethodInterceptor.Interceptor(method)));
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
            }
            else {
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

            if (vd.getType().isPrimitiveType()) {
                builder = builder.defineField(fieldName, Reflect.getComponentClass(vd.getTypeAsString()),
                        Visibility.PRIVATE);
            }
            else {
                TypeWrapper wrapper = AbstractCompiler.findType(cu, vd.getType());
                if (wrapper != null) {
                    if (wrapper.getClazz() != null) {
                        builder = builder.defineField(fieldName, wrapper.getClazz(), Visibility.PRIVATE);
                    }
                    else {
                        if (MockingRegistry.isMockTarget(wrapper.getFullyQualifiedName())) {
                            // todo : fix this
                        }
                        else {
                            Evaluator eval = EvaluatorFactory.create(wrapper.getFullyQualifiedName(), SpringEvaluator.class);
                            Class<?> clazz = AKBuddy.createDynamicClass(new MethodInterceptor(eval));
                            builder = builder.defineField(fieldName, clazz, Visibility.PRIVATE);
                        }
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

            // Only add getter if not already present
            if (needGetter && dtoType.getMethods().stream().noneMatch(m -> m.getNameAsString().equals(getterName))) {
                Class<?> returnType = getFieldType(cu, vd);
                builder = builder.defineMethod(getterName, returnType, Visibility.PUBLIC)
                        .intercept(net.bytebuddy.implementation.FieldAccessor.ofField(fieldName));
            }
            // Only add setter if not already present and field is not final
            if (needSetter && !field.isFinal() && dtoType.getMethods().stream().noneMatch(m -> m.getNameAsString().equals(setterName))) {
                Class<?> paramType = getFieldType(cu, vd);
                builder = builder.defineMethod(setterName, void.class, Visibility.PUBLIC)
                        .withParameters(paramType)
                        .intercept(net.bytebuddy.implementation.FieldAccessor.ofField(fieldName));
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

    @SuppressWarnings("java:S3011")
    public static Object createInstance(Class<?> dynamicClass, MethodInterceptor interceptor) throws ReflectiveOperationException {
        Object instance = dynamicClass.getDeclaredConstructor().newInstance();
        Field icpt = instance.getClass().getDeclaredField(INSTANCE_INTERCEPTOR);
        icpt.setAccessible(true);
        icpt.set(instance, interceptor);

        Evaluator evaluator = interceptor.getEvaluator();
        if (evaluator != null) {
            TypeDeclaration<?> dtoType = AntikytheraRunTime.getTypeDeclaration(evaluator.getClassName()).orElseThrow();
            for (FieldDeclaration field : dtoType.getFields()) {
                Field f = instance.getClass().getDeclaredField(field.getVariable(0).getNameAsString());
                f.setAccessible(true);

                Variable v = evaluator.getField(field.getVariable(0).getNameAsString());
                if(v != null) {
                    Object value = v.getValue();

                    if (value instanceof Evaluator eval) {
                        MethodInterceptor interceptor1 = new MethodInterceptor(eval);
                        Class<?> c = AKBuddy.createDynamicClass(interceptor1);
                        f.set(instance, AKBuddy.createInstance(c, interceptor1));
                    }
                    else {
                        f.set(instance, value);
                    }
                }
            }
        }

        return instance;
    }
}
