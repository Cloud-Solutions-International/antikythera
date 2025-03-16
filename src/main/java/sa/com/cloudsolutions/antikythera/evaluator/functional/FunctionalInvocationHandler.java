package sa.com.cloudsolutions.antikythera.evaluator.functional;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public record FunctionalInvocationHandler(FPEvaluator<?> evaluator) implements InvocationHandler {

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
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
            return s.get();
        }
        return null;
    }

    public FPEvaluator<?> getEvaluator() {
        return evaluator;
    }
}
