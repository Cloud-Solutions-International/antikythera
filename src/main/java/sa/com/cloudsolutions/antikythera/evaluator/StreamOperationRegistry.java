package sa.com.cloudsolutions.antikythera.evaluator;

import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;

import java.lang.reflect.InvocationTargetException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Stream;

import static sa.com.cloudsolutions.antikythera.evaluator.StreamEvaluator.*;

/**
 * Registry for stream operation handlers following the Open/Closed Principle.
 *
 * <p>New stream operations can be added by calling {@link #registerIntermediate} or
 * {@link #registerTerminal} without modifying existing handler code.</p>
 */
final class StreamOperationRegistry {

    private static final String SORTED = "sorted";
    private static final String REDUCE = "reduce";

    private StreamOperationRegistry() {}

    @FunctionalInterface
    interface StreamOpHandler {
        Variable handle(Stream<?> stream, Object[] args)
                throws ReflectiveOperationException;
    }

    private static final Map<String, StreamOpHandler> intermediateOps = new HashMap<>();
    private static final Map<String, StreamOpHandler> terminalOps = new HashMap<>();

    static {
        registerDefaults();
    }

    static void registerIntermediate(String name, StreamOpHandler handler) {
        intermediateOps.put(name, handler);
    }

    static void registerTerminal(String name, StreamOpHandler handler) {
        terminalOps.put(name, handler);
    }

    static StreamEvaluator.IntermediateResult tryIntermediate(String methodName, Stream<?> stream, Object[] args)
            throws ReflectiveOperationException {
        StreamOpHandler handler = intermediateOps.get(methodName);
        if (handler == null) {
            return new StreamEvaluator.IntermediateResult(false, null);
        }
        return new StreamEvaluator.IntermediateResult(true, handler.handle(stream, args));
    }

    static Variable tryTerminal(String methodName, Stream<?> stream, Object[] args)
            throws ReflectiveOperationException {
        StreamOpHandler handler = terminalOps.get(methodName);
        if (handler == null) {
            return null;
        }
        return handler.handle(stream, args);
    }

    @SuppressWarnings({"java:S3740", "unchecked"})
    private static void registerDefaults() {
        // ── Intermediate operations ──

        registerIntermediate("filter", (s, a) -> wrap(
                Stream.class.getMethod("filter", Predicate.class)
                        .invoke(s, toPredicate(a[0]))));

        registerIntermediate("takeWhile", (s, a) -> wrap(
                Stream.class.getMethod("takeWhile", Predicate.class)
                        .invoke(s, toPredicate(a[0]))));

        registerIntermediate("dropWhile", (s, a) -> wrap(
                Stream.class.getMethod("dropWhile", Predicate.class)
                        .invoke(s, toPredicate(a[0]))));

        registerIntermediate("map", (s, a) -> wrap(
                Stream.class.getMethod("map", java.util.function.Function.class)
                        .invoke(s, toStreamFunction(a[0]))));

        registerIntermediate("flatMap", (s, a) -> wrap(
                Stream.class.getMethod("flatMap", java.util.function.Function.class)
                        .invoke(s, toStreamFunction(a[0]))));

        registerIntermediate("peek", (s, a) -> wrap(
                Stream.class.getMethod("peek", Consumer.class)
                        .invoke(s, toStreamConsumer(a[0]))));

        registerIntermediate(SORTED, (s, a) -> {
            if (a.length == 0 || a[0] == null) {
                return wrap(Stream.class.getMethod(SORTED).invoke(s));
            }
            Comparator<Object> comp = toStreamComparator(a[0]);
            return wrap(Stream.class.getMethod(SORTED, Comparator.class).invoke(s, comp));
        });

        registerIntermediate("distinct", (s, a) -> wrap(
                Stream.class.getMethod("distinct").invoke(s)));

        registerIntermediate("limit", (s, a) -> wrap(
                Stream.class.getMethod("limit", long.class)
                        .invoke(s, ((Number) a[0]).longValue())));

        registerIntermediate("skip", (s, a) -> wrap(
                Stream.class.getMethod("skip", long.class)
                        .invoke(s, ((Number) a[0]).longValue())));

        registerIntermediate("mapToInt", (s, a) -> {
            UnaryOperator<Object> fn = toStreamFunction(a[0]);
            ToIntFunction<Object> toIntFn = x -> ((Number) fn.apply(x)).intValue();
            return wrap(Stream.class.getMethod("mapToInt", ToIntFunction.class).invoke(s, toIntFn));
        });

        registerIntermediate("mapToLong", (s, a) -> {
            UnaryOperator<Object> fn = toStreamFunction(a[0]);
            ToLongFunction<Object> toLongFn = x -> ((Number) fn.apply(x)).longValue();
            return wrap(Stream.class.getMethod("mapToLong", ToLongFunction.class).invoke(s, toLongFn));
        });

        registerIntermediate("mapToDouble", (s, a) -> {
            UnaryOperator<Object> fn = toStreamFunction(a[0]);
            ToDoubleFunction<Object> toDblFn = x -> ((Number) fn.apply(x)).doubleValue();
            return wrap(Stream.class.getMethod("mapToDouble", ToDoubleFunction.class).invoke(s, toDblFn));
        });

        // ── Terminal operations ──

        registerTerminal("forEach", (s, a) -> {
            Consumer<Object> action = toStreamConsumer(a[0]);
            s.forEach(action);
            return new Variable(null);
        });

        registerTerminal("collect", (s, a) -> {
            if (a.length == 1) {
                Collector<Object, ?, Object> col = (Collector<Object, ?, Object>) a[0];
                return wrap(Stream.class.getMethod("collect", Collector.class).invoke(s, col));
            } else if (a.length == 3) {
                Stream<Object> objectStream = (Stream<Object>) s;
                Object result = objectStream.collect(
                        (java.util.function.Supplier<Object>) a[0],
                        (java.util.function.BiConsumer<Object, Object>) a[1],
                        (java.util.function.BiConsumer<Object, Object>) a[2]);
                return wrap(result);
            }
            throw new AntikytheraException("Unsupported collect overload with " + a.length + " arguments");
        });

        registerTerminal("count", (s, a) -> wrap(Stream.class.getMethod("count").invoke(s)));
        registerTerminal("toList", (s, a) -> wrap(Stream.class.getMethod("toList").invoke(s)));
        registerTerminal("toArray", (s, a) -> wrap(Stream.class.getMethod("toArray").invoke(s)));
        registerTerminal("findFirst", (s, a) -> wrapOptional(Stream.class.getMethod("findFirst").invoke(s)));
        registerTerminal("findAny", (s, a) -> wrapOptional(Stream.class.getMethod("findAny").invoke(s)));

        registerTerminal("anyMatch", (s, a) -> wrap(
                Stream.class.getMethod("anyMatch", Predicate.class).invoke(s, toPredicate(a[0]))));
        registerTerminal("allMatch", (s, a) -> wrap(
                Stream.class.getMethod("allMatch", Predicate.class).invoke(s, toPredicate(a[0]))));
        registerTerminal("noneMatch", (s, a) -> wrap(
                Stream.class.getMethod("noneMatch", Predicate.class).invoke(s, toPredicate(a[0]))));

        registerTerminal("min", (s, a) -> wrapOptional(
                Stream.class.getMethod("min", Comparator.class).invoke(s, toStreamComparator(a[0]))));
        registerTerminal("max", (s, a) -> wrapOptional(
                Stream.class.getMethod("max", Comparator.class).invoke(s, toStreamComparator(a[0]))));

        registerTerminal(REDUCE, (s, a) -> {
            if (a.length == 1) {
                BinaryOperator<Object> op = toStreamBinaryOperator(a[0]);
                return wrapOptional(Stream.class.getMethod(REDUCE, BinaryOperator.class).invoke(s, op));
            } else if (a.length == 2) {
                BinaryOperator<Object> op = toStreamBinaryOperator(a[1]);
                return wrap(Stream.class.getMethod(REDUCE, Object.class, BinaryOperator.class)
                        .invoke(s, a[0], op));
            } else if (a.length == 3) {
                Stream<Object> objectStream = (Stream<Object>) s;
                Object result = objectStream.reduce(
                        a[0],
                        (java.util.function.BiFunction<Object, Object, Object>) a[1],
                        toStreamBinaryOperator(a[2]));
                return wrap(result);
            }
            throw new AntikytheraException("Unsupported reduce overload with " + a.length + " arguments");
        });
    }

    private static Predicate<Object> toPredicate(Object arg) {
        UnaryOperator<Object> fn = toStreamFunction(arg);
        return x -> Boolean.TRUE.equals(fn.apply(x));
    }

    private static Variable wrap(Object result) {
        Variable rv = new Variable(result);
        if (result != null) {
            rv.setClazz(result.getClass());
        }
        return rv;
    }

    private static Variable wrapOptional(Object result) {
        Variable rv = new Variable(result);
        if (result instanceof Optional) {
            rv.setClazz(Optional.class);
        } else if (result != null) {
            rv.setClazz(result.getClass());
        }
        return rv;
    }
}
