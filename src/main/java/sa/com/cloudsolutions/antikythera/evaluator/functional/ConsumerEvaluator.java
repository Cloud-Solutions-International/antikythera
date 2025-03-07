package sa.com.cloudsolutions.antikythera.evaluator.functional;

import com.github.javaparser.ast.expr.MethodCallExpr;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.FunctionalEvaluator;
import sa.com.cloudsolutions.antikythera.parser.MCEWrapper;

import java.util.function.Consumer;

public class ConsumerEvaluator<T> implements Consumer<T> {
    FunctionalEvaluator eval;

    @Override
    public void accept(T t) {
        try {
            eval.apply(t);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Consumer<T> andThen(Consumer<? super T> after) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void setEvaluator(FunctionalEvaluator eval) {
        this.eval = eval;
    }
}
