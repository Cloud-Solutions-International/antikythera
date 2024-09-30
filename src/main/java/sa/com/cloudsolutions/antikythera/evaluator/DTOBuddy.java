package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.type.ClassOrInterfaceType;

import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.StubMethod;

public class DTOBuddy {

    protected DTOBuddy() {}

    public static Class<?> createDynamicDTO(ClassOrInterfaceType dtoType) throws Exception {
        String className = dtoType.getNameAsString();
        ByteBuddy byteBuddy = new ByteBuddy();

        DynamicType.Builder<?> builder = byteBuddy.subclass(Object.class).name(className);

        for (ResolvedFieldDeclaration field : dtoType.resolve().asReferenceType().getDeclaredFields()) {
            String fieldName = field.getName();
            String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

            // Get the field type
            TypeDescription.Generic fieldType = TypeDescription.Generic.OfNonGenericType.ForLoadedType.of(Class.forName(field.getType().describe()));

            // Define getter
            builder = builder.defineMethod(getterName, fieldType, net.bytebuddy.description.modifier.Visibility.PUBLIC)
                    .intercept(FixedValue.nullValue());

            // Define setter
            builder = builder.defineMethod(setterName, void.class, net.bytebuddy.description.modifier.Visibility.PUBLIC)
                    .withParameter(fieldType)
                    .intercept(StubMethod.INSTANCE);
        }

        return builder.make()
                .load(DTOBuddy.class.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded();
    }

}
