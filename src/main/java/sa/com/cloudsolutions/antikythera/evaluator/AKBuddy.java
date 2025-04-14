package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.Type;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.lang.reflect.Array;
import java.util.List;


/**
 * Create fake objects using Byte Buddy for DTOs, Entities and possibly other types of classes.
 * This will be primarily used in mocking.
 */
public class AKBuddy {

    protected AKBuddy() {}

    /**
     * <p>Dynamically create a class matching given type declaration and then create an instance</p>
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
            CompilationUnit cu = eval.getCompilationUnit();
            TypeDeclaration<?> dtoType = AbstractCompiler.getMatchingType(cu, eval.getClassName()).orElseThrow();
            String className = dtoType.getNameAsString();


            List<FieldDeclaration> fields = dtoType.getFields();

            ByteBuddy byteBuddy = new ByteBuddy();
            DynamicType.Builder<?> builder = byteBuddy.subclass(Object.class).name(className)
                    .method(ElementMatchers.not(
                        ElementMatchers.isDeclaredBy(Object.class)
                        .or(ElementMatchers.isDeclaredBy(com.fasterxml.jackson.core.ObjectCodec.class))
                        .or(ElementMatchers.isDeclaredBy(com.fasterxml.jackson.databind.ObjectMapper.class))
                    ))
                    .intercept(MethodDelegation.to(interceptor));

            builder = addFields(fields, cu, builder);
            builder = addMethods(dtoType.getMethods(), cu, builder, interceptor);

            return builder.make()
                    .load(Evaluator.class.getClassLoader())
                    .getLoaded();
        }
        else {
            Class<?> wrappedClass = interceptor.getWrappedClass();
            ByteBuddy byteBuddy = new ByteBuddy();
            return byteBuddy.subclass(wrappedClass)
                    .method(ElementMatchers.not(
                        ElementMatchers.isDeclaredBy(Object.class)
                        .or(ElementMatchers.isDeclaredBy(com.fasterxml.jackson.core.ObjectCodec.class))
                        .or(ElementMatchers.isDeclaredBy(com.fasterxml.jackson.databind.ObjectMapper.class))
                    ))
                    .intercept(MethodDelegation.to(interceptor))
                    .make()
                    .load(wrappedClass.getClassLoader())
                    .getLoaded();
        }
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
                    String fullName = AbstractCompiler.findFullyQualifiedName(cu, p.getType().asString());
                    return Reflect.getComponentClass(fullName);
                }
            }

        } catch (ClassNotFoundException e) {
            throw new AntikytheraException(e);
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
                } catch (ClassNotFoundException cex) {
                    continue;
                }
            }

            // Define field
            builder = builder.defineField(fieldName, fieldType, net.bytebuddy.description.modifier.Visibility.PRIVATE);
        }
        return builder;
    }

}
