package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FieldAccessor;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.util.List;


/**
 * Create fake objects using DTO Buddy for DTOs, Entities and possibly other types of classes
 */
public class DTOBuddy {

    protected DTOBuddy() {}

    /**
     * Dynamically create a class matching the given type declaration and then create an instance.
     *
     * We will iterate through all the fields declared in the source code and make fake fields
     *  accordingly so that they show up in reflective inspections.
     *
     * @param dtoType The ClassOrInterfaceType from which to build our byte buddy
     * @return an instance of the class that was faked.
     * @throws ReflectiveOperationException If an error occurs during reflection operations.
     */
    public static Object createDynamicDTO(ClassOrInterfaceDeclaration dtoType)
            throws ReflectiveOperationException {
        String className = dtoType.getNameAsString();
        CompilationUnit cu = dtoType.findCompilationUnit().orElseThrow();
        List<FieldDeclaration> fields = dtoType.getFields();

        ByteBuddy byteBuddy = new ByteBuddy();
        DynamicType.Builder<?> builder = byteBuddy.subclass(Object.class).name(className);

        for (FieldDeclaration field : fields) {
            VariableDeclarator vd = field.getVariable(0);
            String fieldName = vd.getNameAsString();
            String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);


            TypeDescription.Generic fieldType = null;
            if (vd.getType().isPrimitiveType()) {
                fieldType = TypeDescription.Generic.OfNonGenericType.ForLoadedType.of(
                        Reflect.getComponentClass(vd.getTypeAsString()));
            }
            else {
                try {
                    String fqn = AbstractCompiler.findFullyQualifiedName(cu, vd.getType().asString());
                    fieldType = TypeDescription.Generic.OfNonGenericType.ForLoadedType.of(Class.forName(fqn));
                } catch (ClassNotFoundException cex) {
                    // This field has a class that's not coming from an external library, but it's only available
                    // as source code. We need to create a dynamic class for it.

                    // then again there are cycles!

//                    String qualifiedName = field.getType().asReferenceType().getQualifiedName();
//                    if (qualifiedName.startsWith("java.util")) {
//                        if (qualifiedName.equals("java.util.List")) {
//                            fieldType = TypeDescription.Generic.Builder.parameterizedType(List.class, Object.class).build();
//                        } else {
//                            Class<?> clazz = Class.forName(qualifiedName);
//                            fieldType = TypeDescription.Generic.OfNonGenericType.ForLoadedType.of(clazz);
//                        }
//                    } else {
//                        Object o = DTOBuddy.createDynamicDTO(qualifiedName, field.getType().asReferenceType().getTypeDeclaration().get());
//                        fieldType = TypeDescription.Generic.OfNonGenericType.ForLoadedType.of(o.getClass());
//                    }
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

        Class<?> clazz = builder.make()
                .load(DTOBuddy.class.getClassLoader(), ClassLoadingStrategy.Default.CHILD_FIRST)
                .getLoaded();
        return clazz.getDeclaredConstructor().newInstance();
    }

}
