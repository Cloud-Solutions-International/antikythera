package sa.com.cloudsolutions.antikythera.evaluator.functional;

import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.WildcardType;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.EvaluatorFactory;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;

import java.util.function.BiFunction;

public class BiFunctionEvaluator<T, U, R> extends FPEvaluator<T> implements BiFunction<T, U, R> {

    public BiFunctionEvaluator(EvaluatorFactory.Context context) {
        super(context);
        this.enclosure = context.getEnclosure();
    }

    @Override
    public Type getType() {
        return new ClassOrInterfaceType()
                .setName("BiFunction")
                .setTypeArguments(
                        new WildcardType(),new WildcardType(),new WildcardType()
                );
    }

    @Override
    public R apply(T t, U u) {
        AntikytheraRunTime.push(new Variable(u));
        AntikytheraRunTime.push(new Variable(t));

        try {
            Variable v = executeMethod(methodDeclaration);
            return (R) v.getValue();
        } catch (ReflectiveOperationException e) {
            throw new AntikytheraException(e);
        }
    }
}
