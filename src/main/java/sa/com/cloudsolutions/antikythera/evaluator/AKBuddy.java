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
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Create fake objects using Byte Buddy for DTOs, Entities and possibly other types of classes.
 * This will be primarily used in mocking.
 */
public class AKBuddy {
    private static final Map<String, Class<?>> registry = new HashMap<>();
    private static final AKClassLoader loader = new AKClassLoader();
    public static final String INSTANCE_INTERCEPTOR = "instanceInterceptor";
    public static final String SET_INTERCEPTOR = "setInterceptor";

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
                .defineMethod(SET_INTERCEPTOR, MethodInterceptor.class, Modifier.PUBLIC)
                .withParameters(MethodInterceptor.class)
                .intercept(FieldAccessor.ofField(INSTANCE_INTERCEPTOR))
                .make()
                .load(loader, ClassLoadingStrategy.Default.INJECTION)
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
        TypeDeclaration<?> dtoType = AbstractCompiler.getMatchingType(cu, eval.getClassName()).orElseThrow();
        String className = eval.getClassName();

        List<FieldDeclaration> fields = dtoType.getFields();

        ByteBuddy byteBuddy = new ByteBuddy();
        DynamicType.Builder<?> builder = byteBuddy.subclass(interceptor.getWrappedClass()).name(className)
                .method(ElementMatchers.not(
                        ElementMatchers.isDeclaredBy(Object.class)
                                .or(ElementMatchers.isDeclaredBy(com.fasterxml.jackson.core.ObjectCodec.class))
                                .or(ElementMatchers.isDeclaredBy(com.fasterxml.jackson.databind.ObjectMapper.class))
                ))
                .intercept(MethodDelegation.withDefaultConfiguration()
                        .filter(ElementMatchers.named("intercept"))
                        .to(MethodInterceptor.Interceptor.class)
                        .andThen(FieldAccessor.ofField(INSTANCE_INTERCEPTOR)))
                .defineField(INSTANCE_INTERCEPTOR, MethodInterceptor.class, Visibility.PRIVATE)
                .defineMethod(SET_INTERCEPTOR, MethodInterceptor.class, Modifier.PUBLIC)
                .withParameters(MethodInterceptor.class)
                .intercept(FieldAccessor.ofField(INSTANCE_INTERCEPTOR));

        if (dtoType instanceof ClassOrInterfaceDeclaration cdecl) {
            for (ClassOrInterfaceType iface : cdecl.getImplementedTypes()) {
                TypeWrapper wrapper = AbstractCompiler.findType(eval.getCompilationUnit(), iface.getNameAsString());
                if (wrapper != null && wrapper.getClazz() != null) {
                    builder = builder.implement(wrapper.getClazz());
                }
            }
        }

        builder = addFields(fields, cu, builder);
        builder = addMethods(dtoType.getMethods(), cu, builder, interceptor);

        Class<?> clazz = builder.make()
                .load(loader, ClassLoadingStrategy.Default.INJECTION)
                .getLoaded();

        registry.put(eval.getClassName(), clazz);
        return clazz;
    }

    private static DynamicType.Builder<?> addMethods(List<MethodDeclaration> methods, CompilationUnit cu,
                                                     DynamicType.Builder<?> builder, MethodInterceptor interceptor)  {

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
                            .to(new MethodInterceptor.Interceptor(interceptor, method)));
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

            } else {
                // Handle non-array types as before
                if (p.getType().isPrimitiveType()) {
                    return Reflect.getComponentClass(p.getTypeAsString());
                } else {
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
                }
            }

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

            TypeDescription.Generic fieldType = null;
            if (vd.getType().isPrimitiveType()) {
                fieldType = TypeDescription.Generic.OfNonGenericType.ForLoadedType.of(
                        Reflect.getComponentClass(vd.getTypeAsString()));
            }
            else {
                try {
                    String fqn = AbstractCompiler.findFullyQualifiedName(cu, vd.getType().asString());
                    fieldType = TypeDescription.Generic.OfNonGenericType.ForLoadedType.of(Class.forName(fqn));
                } catch (ClassNotFoundException|NullPointerException cex) {
                    // TODO : user proper dynamic types here
                    fieldType = TypeDescription.Generic.OfNonGenericType.ForLoadedType.of(Object.class);
                }
            }

            // Define field
            builder = builder.defineField(fieldName, fieldType, net.bytebuddy.description.modifier.Visibility.PRIVATE);
        }
        return builder;
    }

    @SuppressWarnings("java:S3011")
    public static Object createInstance(Class<?> dynamicClass, MethodInterceptor interceptor) throws ReflectiveOperationException {
        Object instance = dynamicClass.getDeclaredConstructor().newInstance();
        Method m = dynamicClass.getDeclaredMethod(SET_INTERCEPTOR, MethodInterceptor.class);
        m.invoke(instance, interceptor);

        // Initialize fields
        for (Field field : instance.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            if (field.get(instance) == null && !field.getName().equals(INSTANCE_INTERCEPTOR)) {
                Variable value = interceptor.getEvaluator().getField(field.getName());
                if (value.getValue() instanceof Evaluator eval) {
                    Class<?> fieldClass = AKBuddy.createDynamicClass(new MethodInterceptor(eval));
                    field.set(instance, fieldClass.getDeclaredConstructor().newInstance());
                }
                else {
                    field.set(instance, value.getValue());
                }
            }
        }

        return instance;
    }

    public static class AKClassLoader extends ClassLoader {
        public AKClassLoader() {
            super(ClassLoader.getSystemClassLoader());
        }
    }
}
