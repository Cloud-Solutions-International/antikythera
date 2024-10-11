package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedTypeDeclaration;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.StubMethod;
import net.bytebuddy.implementation.SuperMethodCall;


import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Optional;

/**
 * Create fake objects using DTO Buddy for DTOs, Entities and possibly other types of classes
 */
public class DTOBuddy {

    protected DTOBuddy() {}

    /**
     * Dynamically create a class matching the given type declaration and then create an instance.
     *
     * @param dtoType The ClassOrInterfaceType from which to build our byte buddy
     * @param constructorArgs The arguments to be passed to the constructor.
     * @return An instance of the created class.
     * @throws ReflectiveOperationException If an error occurs during reflection operations.
     */
    public static Object createDynamicDTO(ClassOrInterfaceType dtoType, Object ...constructorArgs)
            throws ReflectiveOperationException {
        String className = dtoType.resolve().asReferenceType().getQualifiedName();

        Class<?> clazz = createDynamicDTO(dtoType.resolve().asReferenceType().getDeclaredFields(), className, constructorArgs);
        Object instance = clazz.getDeclaredConstructor(getConstructorParameterTypes(constructorArgs)).newInstance(constructorArgs);
        setDefaults(dtoType.resolve().asReferenceType().getDeclaredFields(), instance);
        return instance;
    }

    /**
     * Dynamically create a class matching the given type declaration and then create an instance.
     *
     * @param dtoType The ClassOrInterfaceType from which to build our byte buddy
     * @return an instance of the class that was faked.
     * @throws ReflectiveOperationException If an error occurs during reflection operations.
     */
    public static Object createDynamicDTO(ClassOrInterfaceDeclaration dtoType)
            throws ReflectiveOperationException {
        String className = dtoType.getNameAsString();
        Class<?> clazz = createDynamicDTO(dtoType.resolve().asReferenceType().getDeclaredFields(), className);
        Object instance = clazz.getDeclaredConstructor().newInstance();
        setDefaults(dtoType.resolve().asReferenceType().getDeclaredFields(), instance);
        return instance;
    }

    /**
     * Dynamically create a class matching the given type declaration and then create an instance.
     *
     * @param qualifiedName This is a fully qualified name for the class
     * @param dtoType The ResolvedTypeDeclaration representing the DTO.
     * @return The created DTO instance.
     * @throws ReflectiveOperationException If an error occurs during reflection operations.
     */
    public static Object createDynamicDTO(String qualifiedName, ResolvedTypeDeclaration dtoType) throws ReflectiveOperationException {
        Class<?> clazz = createDynamicDTO(dtoType.asReferenceType().getDeclaredFields(), qualifiedName);
        Object instance = clazz.getDeclaredConstructor().newInstance();
        setDefaults(dtoType.asReferenceType().getDeclaredFields(), instance);
        return instance;
    }

    /**
     * Sets default values for fields annotated with @Id.
     *
     * @param fields The collection of fields that were discovered by java parser
     * @param instance The instance of the DTO.
     * @throws ReflectiveOperationException If an error occurs during reflection operations.
     */
    private static void setDefaults(Collection<ResolvedFieldDeclaration> fields, Object instance) throws ReflectiveOperationException {
        Class<?> clazz = instance.getClass();

        for (ResolvedFieldDeclaration field : fields) {
            Optional<Node> a = field.toAst();
            if (a.isPresent()) {
                Node node = a.get();
                if (node instanceof FieldDeclaration fieldDeclaration) {
                    if (fieldDeclaration.getAnnotationByName("Id").isPresent()) {
                        String fieldName = field.getName();
                        Type t = fieldDeclaration.getElementType();
                        String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

                        Method method = clazz.getMethod(setterName, Class.forName(field.getType().describe()));
                        method.invoke(instance, Long.valueOf("10"));
                    }
                }
            }
        }
    }

    /**
     * Create a fake class using byte buddy based on the given class name, constructor and fields
     *
     * @param fields The collection of ResolvedFieldDeclaration representing the fields.
     * @param className The name of the class to be created.
     * @param constructorArgs The arguments to be passed to the constructor.
     * @return The created DTO class.
     */
    public static Class<?> createDynamicDTO(Collection<ResolvedFieldDeclaration> fields, String className, Object... constructorArgs) throws ReflectiveOperationException {
        ByteBuddy byteBuddy = new ByteBuddy();
        DynamicType.Builder<?> builder = byteBuddy.subclass(Object.class).name(className);

        // Define constructor with specific parameters and call super()
        builder = builder.defineConstructor(Visibility.PUBLIC)
                .withParameters(getConstructorParameterTypes(constructorArgs))
                .intercept(MethodCall.invoke(Object.class.getDeclaredConstructor()));

        for (ResolvedFieldDeclaration field : fields) {
            String fieldName = field.getName();
            String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

            // Get the field type
            TypeDescription.Generic fieldType = null;
            if (field.getType().isPrimitive()) {
                fieldType = TypeDescription.Generic.OfNonGenericType.ForLoadedType.of(resolvePrimitiveType(field.getType().describe()));
            }
            else {
                try {
                    fieldType = TypeDescription.Generic.OfNonGenericType.ForLoadedType.of(Class.forName(field.getType().describe()));
                } catch (ClassNotFoundException cex) {
                    continue;
                }
            }

            // Define field
            builder = builder.defineField(fieldName, fieldType, net.bytebuddy.description.modifier.Visibility.PRIVATE);

            // Define getter
            builder = builder.defineMethod(getterName, fieldType, net.bytebuddy.description.modifier.Visibility.PUBLIC)
                    .intercept(FieldAccessor.ofField(fieldName));

            // Define setter
            builder = builder.defineMethod(setterName, void.class, net.bytebuddy.description.modifier.Visibility.PUBLIC)
                    .withParameter(fieldType)
                    .intercept(FieldAccessor.ofField(fieldName));
        }

        return builder.make()
                .load(DTOBuddy.class.getClassLoader(), ClassLoadingStrategy.Default.CHILD_FIRST)
                .getLoaded();
    }

    /**
     * Resolves the primitive type from its name.
     *
     * @param typeName The name of the primitive type.
     * @return The Class object representing the primitive type.
     */
    private static Class<?> resolvePrimitiveType(String typeName) {
        switch (typeName) {
            case "boolean": return boolean.class;
            case "byte": return byte.class;
            case "char": return char.class;
            case "short": return short.class;
            case "int": return int.class;
            case "long": return long.class;
            case "float": return float.class;
            case "double": return double.class;
            default: throw new IllegalArgumentException("Unknown primitive type: " + typeName);
        }
    }

    /**
     * Gets the parameter types for the constructor from the provided arguments.
     *
     * @param constructorArgs The arguments to be passed to the constructor.
     * @return An array of Class objects representing the parameter types.
     */
    private static Class<?>[] getConstructorParameterTypes(Object... constructorArgs) {
        Class<?>[] parameterTypes = new Class<?>[constructorArgs.length];
        for (int i = 0; i < constructorArgs.length; i++) {
            parameterTypes[i] = constructorArgs[i].getClass();
        }
        return parameterTypes;
    }
}
