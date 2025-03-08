package sa.com.cloudsolutions.antikythera.evaluator.functional;

import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;

import java.util.function.Consumer;

public class ConsumerEvaluator<T> extends FPEvaluator implements Consumer<T> {

    public ConsumerEvaluator(String className) {
        super(className);
    }

    @Override
    public void accept(T t) {
        AntikytheraRunTime.push(new Variable(t));
        try {
            executeMethod(methodDeclaration);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Consumer<T> andThen(Consumer<? super T> after) {
        throw new UnsupportedOperationException("Not implemented yet");
    }


}
