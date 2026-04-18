package sa.com.cloudsolutions.antikythera.evaluator.functional;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public record FunctionalInvocationHandler(FPEvaluator<?> evaluator) implements InvocationHandler {

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (evaluator instanceof NAryFunctionEvaluator n) {
            return n.invoke(args);
        }
        if (evaluator instanceof NAryConsumerEvaluator n) {
            n.invoke(args);
            return null;
        }
        if (evaluator instanceof BiConsumer bc) {
            bc.accept(args[0], args[1]);
            return null;
        }
        if (evaluator instanceof Function f) {
            return f.apply(args[0]);
        }
        if (evaluator instanceof BiFunction bif) {
            return bif.apply(args[0], args[1]);
        }
        if (evaluator instanceof Consumer c) {
            c.accept(args[0]);
            return null;
        }
        if (evaluator instanceof Runnable r) {
            r.run();
            return null;
        }
        if (evaluator instanceof Supplier s) {
            Object result = s.get();
            if (result instanceof sa.com.cloudsolutions.antikythera.evaluator.Variable v) {
                return v.getValue();
            }
            return result;
        }
        if (method != null && method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }
        return null;
    }

    public FPEvaluator<?> getEvaluator() {
        return evaluator;
    }
}
