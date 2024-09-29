package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.type.ClassOrInterfaceType;

import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FixedValue;

public class DTOBuddy {

    protected DTOBuddy() {}

    public static Class<?> createDynamicDTO(ClassOrInterfaceType dtoType) throws Exception {
        String className = dtoType.getNameAsString();
        ByteBuddy byteBuddy = new ByteBuddy();

        DynamicType.Builder<?> builder = byteBuddy.subclass(Object.class).name(className);


        for (ResolvedFieldDeclaration field : dtoType.resolve().asReferenceType().getDeclaredFields()) {
            String fieldName = field.getName();
            String method = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            builder = builder.defineMethod(method, Object.class, net.bytebuddy.description.modifier.Visibility.PUBLIC)
                    .intercept(FixedValue.nullValue());
        }

        return builder.make()
                .load(DTOBuddy.class.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded();
    }

}
