package sa.com.cloudsolutions.antikythera.evaluator.functional;

import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.WildcardType;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.EvaluatorFactory;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;

import java.util.function.BiConsumer;

public class BiConsumerEvaluator<T, U> extends FPEvaluator<T> implements BiConsumer<T, U> {

    public BiConsumerEvaluator(EvaluatorFactory.Context context) {
        super(context);
        this.enclosure = context.getEnclosure();
    }

    @Override
    public Type getType() {
        return new ClassOrInterfaceType()
                .setName("BiConsumer")
                .setTypeArguments(
                        new WildcardType(),
                        new WildcardType()
                );
    }

    @Override
    public void accept(T t, U u) {
        AntikytheraRunTime.push(new Variable(u));
        AntikytheraRunTime.push(new Variable(t));

        try {
            executeMethod(methodDeclaration);
        } catch (ReflectiveOperationException e) {
            throw new AntikytheraException(e);
        }
    }
}
