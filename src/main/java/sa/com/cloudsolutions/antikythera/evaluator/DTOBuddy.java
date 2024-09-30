package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FieldAccessor;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Optional;


public class DTOBuddy {

    protected DTOBuddy() {}

    public static Object createDynamicDTO(ClassOrInterfaceType dtoType) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        String className = dtoType.getNameAsString();
        Class<?> clazz = createDynamicDTO(dtoType.resolve().asReferenceType().getDeclaredFields(), className);
        Object instance = clazz.getDeclaredConstructor().newInstance();
        setDefaults(dtoType.resolve().asReferenceType().getDeclaredFields(), instance);
        return instance;
    }

    public static Object createDynamicDTO(ClassOrInterfaceDeclaration dtoType) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        String className = dtoType.getNameAsString();
        Class<?> clazz = createDynamicDTO(dtoType.resolve().asReferenceType().getDeclaredFields(), className);
        Object instance = clazz.getDeclaredConstructor().newInstance();
        setDefaults(dtoType.resolve().asReferenceType().getDeclaredFields(), instance);
        return instance;
    }

    private static void setDefaults(Collection<ResolvedFieldDeclaration> fields, Object instance) throws NoSuchMethodException, ClassNotFoundException, InvocationTargetException, IllegalAccessException {
        Class<?> clazz = instance.getClass();

        for (ResolvedFieldDeclaration field : fields) {
            Optional<Node> a = field.toAst();
            Node node = a.get();
            if (node instanceof FieldDeclaration) {
                FieldDeclaration fieldDeclaration = (FieldDeclaration) node;
                if (fieldDeclaration.getAnnotationByName("Id").isPresent()) {
                    String fieldName = field.getName();
                    Type t = fieldDeclaration.getElementType();
                    String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

                    Method method = clazz.getMethod(setterName, Class.forName(field.getType().describe()));
                    method.invoke(instance, Long.valueOf("10"));
                    System.out.println(t);
                }
            }
            System.out.println(node);
        }
    }

    public static Class<?> createDynamicDTO(Collection<ResolvedFieldDeclaration> fields, String className) throws ClassNotFoundException {
        ByteBuddy byteBuddy = new ByteBuddy();
        DynamicType.Builder<?> builder = byteBuddy.subclass(Object.class).name(className);

        for (ResolvedFieldDeclaration field : fields) {
            String fieldName = field.getName();
            String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

            // Get the field type
            TypeDescription.Generic fieldType = TypeDescription.Generic.OfNonGenericType.ForLoadedType.of(Class.forName(field.getType().describe()));

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
                .load(DTOBuddy.class.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded();
    }
}
