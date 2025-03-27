package sa.com.cloudsolutions.antikythera.evaluator.functional;

import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.WildcardType;
import sa.com.cloudsolutions.antikythera.evaluator.EvaluatorFactory;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;

import java.util.function.Supplier;

public class SupplierEvaluator<T> extends FPEvaluator<T> implements Supplier<T> {

    public SupplierEvaluator(EvaluatorFactory.Context context) {
        super(context);
        this.enclosure = context.getEnclosure();
    }

    @Override
    public Type getType() {
        return new ClassOrInterfaceType()
                .setName("Supplier")
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
            throw new AntikytheraException(e);
        }
    }
}
