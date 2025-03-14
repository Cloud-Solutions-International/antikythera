package sa.com.cloudsolutions.antikythera.evaluator.functional;

import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.WildcardType;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;

import java.util.function.Supplier;

public class SupplierEvaluator<T> extends FPEvaluator implements Supplier<T> {

    public SupplierEvaluator(String className) {
        super(className);
    }

    @Override
    public Type getType() {
        return new ClassOrInterfaceType()
                .setName("Consumer")
                .setTypeArguments(
                        new WildcardType()
                );
    }

    @Override
    public T get() {
        try {
            Variable v = executeMethod(methodDeclaration);
            return (T) v.getValue();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
