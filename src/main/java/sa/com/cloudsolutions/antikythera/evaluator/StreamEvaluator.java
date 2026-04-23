package sa.com.cloudsolutions.antikythera.evaluator;

import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;

import java.lang.reflect.InvocationTargetException;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.LongFunction;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static sa.com.cloudsolutions.antikythera.evaluator.Evaluator.*;

/**
 * Handles all {@link Stream}, {@link IntStream}, {@link LongStream}, and
 * {@link DoubleStream} operations during symbolic evaluation by adapting
 * symbolic functional-interface representations to their JDK counterparts
 * and dispatching via reflection.
 *
 * <p>Extracted from {@link Evaluator} to reduce the size and responsibility
 * scope of the god-class evaluator.</p>
 */
class StreamEvaluator {

    private StreamEvaluator() {}

    /**
     * Entry point: routes stream method calls to the correct dispatcher based
     * on the concrete stream type.
     */
    @SuppressWarnings({"java:S3740"})
    static Variable handleStreamMethods(Variable v, ReflectionArguments reflectionArguments) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        String methodName = reflectionArguments.getMethodName();
        Object obj = v.getValue();
        Object[] finalArgs = reflectionArguments.getFinalArgs();

        if (obj instanceof IntStream || obj instanceof LongStream || obj instanceof DoubleStream) {
            return dispatchPrimitiveStreamOp(methodName, obj, finalArgs);
        } else if (obj instanceof Stream<?> stream) {
            IntermediateResult ir = dispatchIntermediateOp(methodName, stream, finalArgs);
            if (ir.handled()) {
                return ir.result();
            }
            return dispatchTerminalOp(methodName, stream, finalArgs);
        }
        return null;
    }

    // ── Intermediate operations ─────────────────────────────────────────

    record IntermediateResult(boolean handled, Variable result) {}

    @SuppressWarnings({"java:S3740", "java:S3776"})
    static IntermediateResult dispatchIntermediateOp(String methodName, Stream<?> stream, Object[] finalArgs) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Object result;
        switch (methodName) {
            case FILTER, TAKE_WHILE, DROP_WHILE -> {
                UnaryOperator<Object> fn = toStreamFunction(finalArgs[0]);
                Predicate<Object> pred = x -> Boolean.TRUE.equals(fn.apply(x));
                result = Stream.class.getMethod(methodName, Predicate.class).invoke(stream, pred);
            }
            case MAP -> {
                UnaryOperator<Object> fn = toStreamFunction(finalArgs[0]);
                result = Stream.class.getMethod(MAP, Function.class).invoke(stream, fn);
            }
            case FLAT_MAP -> {
                UnaryOperator<Object> fn = toStreamFunction(finalArgs[0]);
                result = Stream.class.getMethod(FLAT_MAP, Function.class).invoke(stream, fn);
            }
            case PEEK -> {
                Consumer<Object> consumer = toStreamConsumer(finalArgs[0]);
                result = Stream.class.getMethod(PEEK, Consumer.class).invoke(stream, consumer);
            }
            case SORTED -> {
                if (finalArgs.length == 0 || finalArgs[0] == null) {
                    result = Stream.class.getMethod(SORTED).invoke(stream);
                } else {
                    Comparator<Object> comp = toStreamComparator(finalArgs[0]);
                    result = Stream.class.getMethod(SORTED, Comparator.class).invoke(stream, comp);
                }
            }
            case DISTINCT -> result = Stream.class.getMethod(DISTINCT).invoke(stream);
            case LIMIT -> {
                long n = ((Number) finalArgs[0]).longValue();
                result = Stream.class.getMethod(LIMIT, long.class).invoke(stream, n);
            }
            case SKIP -> {
                long n = ((Number) finalArgs[0]).longValue();
                result = Stream.class.getMethod(SKIP, long.class).invoke(stream, n);
            }
            case MAP_TO_INT -> {
                UnaryOperator<Object> fn = toStreamFunction(finalArgs[0]);
                ToIntFunction<Object> toIntFn = x -> ((Number) fn.apply(x)).intValue();
                result = Stream.class.getMethod(MAP_TO_INT, ToIntFunction.class).invoke(stream, toIntFn);
            }
            case MAP_TO_LONG -> {
                UnaryOperator<Object> fn = toStreamFunction(finalArgs[0]);
                ToLongFunction<Object> toLongFn = x -> ((Number) fn.apply(x)).longValue();
                result = Stream.class.getMethod(MAP_TO_LONG, ToLongFunction.class).invoke(stream, toLongFn);
            }
            case MAP_TO_DOUBLE -> {
                UnaryOperator<Object> fn = toStreamFunction(finalArgs[0]);
                ToDoubleFunction<Object> toDblFn = x -> ((Number) fn.apply(x)).doubleValue();
                result = Stream.class.getMethod(MAP_TO_DOUBLE, ToDoubleFunction.class).invoke(stream, toDblFn);
            }
            default -> { return new IntermediateResult(false, null); }
        }
        Variable rv = new Variable(result);
        rv.setClazz(result != null ? result.getClass() : Stream.class);
        return new IntermediateResult(true, rv);
    }

    // ── Terminal operations ─────────────────────────────────────────────

    @SuppressWarnings({"unchecked", "java:S3740", "java:S3776"})
    static Variable dispatchTerminalOp(String methodName, Stream<?> stream, Object[] finalArgs) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Object result;
        switch (methodName) {
            case FOR_EACH -> {
                Consumer<Object> action = toStreamConsumer(finalArgs[0]);
                stream.forEach(action);
                return new Variable(null);
            }
            case COLLECT -> {
                if (finalArgs.length == 1) {
                    Collector<Object, ?, Object> col = (Collector<Object, ?, Object>) finalArgs[0];
                    result = Stream.class.getMethod(COLLECT, Collector.class).invoke(stream, col);
                } else if (finalArgs.length == 3) {
                    @SuppressWarnings("unchecked")
                    Stream<Object> objectStream = (Stream<Object>) stream;
                    result = objectStream.collect(
                            (java.util.function.Supplier<Object>) finalArgs[0],
                            (java.util.function.BiConsumer<Object, Object>) finalArgs[1],
                            (java.util.function.BiConsumer<Object, Object>) finalArgs[2]);
                } else {
                    throw new AntikytheraException("Unsupported collect overload with " + finalArgs.length + " arguments");
                }
            }
            case COUNT -> result = Stream.class.getMethod(COUNT).invoke(stream);
            case TO_LIST -> result = Stream.class.getMethod(TO_LIST).invoke(stream);
            case TO_ARRAY -> result = Stream.class.getMethod(TO_ARRAY).invoke(stream);
            case FIND_FIRST -> result = Stream.class.getMethod(FIND_FIRST).invoke(stream);
            case FIND_ANY -> result = Stream.class.getMethod(FIND_ANY).invoke(stream);
            case ANY_MATCH, ALL_MATCH, NONE_MATCH -> {
                UnaryOperator<Object> fn = toStreamFunction(finalArgs[0]);
                Predicate<Object> pred = x -> Boolean.TRUE.equals(fn.apply(x));
                result = Stream.class.getMethod(methodName, Predicate.class).invoke(stream, pred);
            }
            case MIN, MAX -> {
                Comparator<Object> comp = toStreamComparator(finalArgs[0]);
                result = Stream.class.getMethod(methodName, Comparator.class).invoke(stream, comp);
            }
            case REDUCE -> {
                if (finalArgs.length == 1) {
                    BinaryOperator<Object> op = toStreamBinaryOperator(finalArgs[0]);
                    result = Stream.class.getMethod(REDUCE, BinaryOperator.class).invoke(stream, op);
                } else if (finalArgs.length == 2) {
                    BinaryOperator<Object> op = toStreamBinaryOperator(finalArgs[1]);
                    result = Stream.class.getMethod(REDUCE, Object.class, BinaryOperator.class)
                            .invoke(stream, finalArgs[0], op);
                } else if (finalArgs.length == 3) {
                    @SuppressWarnings("unchecked")
                    Stream<Object> objectStream = (Stream<Object>) stream;
                    result = objectStream.reduce(
                            finalArgs[0],
                            (java.util.function.BiFunction<Object, Object, Object>) finalArgs[1],
                            toStreamBinaryOperator(finalArgs[2]));
                } else {
                    throw new AntikytheraException("Unsupported reduce overload with " + finalArgs.length + " arguments");
                }
            }
            default -> { return null; }
        }
        Variable rv = new Variable(result);
        if (result instanceof Optional) {
            rv.setClazz(Optional.class);
        } else if (result != null) {
            rv.setClazz(result.getClass());
        }
        return rv;
    }

    // ── Primitive stream operations ─────────────────────────────────────

    @SuppressWarnings({"java:S3740", "java:S3776", "java:S6541"})
    static Variable dispatchPrimitiveStreamOp(String methodName, Object stream, Object[] finalArgs) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class<?> iface;
        if (stream instanceof IntStream) {
            iface = IntStream.class;
        } else if (stream instanceof LongStream) {
            iface = LongStream.class;
        } else {
            iface = DoubleStream.class;
        }
        Object result;
        switch (methodName) {
            case "sum" -> result = iface.getMethod("sum").invoke(stream);
            case COUNT -> result = iface.getMethod(COUNT).invoke(stream);
            case "average" -> result = iface.getMethod("average").invoke(stream);
            case "min" -> result = iface.getMethod("min").invoke(stream);
            case "max" -> result = iface.getMethod("max").invoke(stream);
            case "summaryStatistics" -> result = iface.getMethod("summaryStatistics").invoke(stream);
            case "boxed" -> result = iface.getMethod("boxed").invoke(stream);
            case TO_LIST -> result = iface.getMethod(TO_LIST).invoke(stream);
            case TO_ARRAY -> result = iface.getMethod(TO_ARRAY).invoke(stream);
            case "asLongStream" -> result = IntStream.class.getMethod("asLongStream").invoke(stream);
            case AS_DOUBLE_STREAM -> {
                if (stream instanceof IntStream) {
                    result = IntStream.class.getMethod(AS_DOUBLE_STREAM).invoke(stream);
                } else {
                    result = LongStream.class.getMethod(AS_DOUBLE_STREAM).invoke(stream);
                }
            }
            case "filter" -> {
                UnaryOperator<Object> fn = toStreamFunction(finalArgs[0]);
                result = switch (stream) {
                    case IntStream is -> is.filter(n -> Boolean.TRUE.equals(fn.apply(n)));
                    case LongStream ls -> ls.filter(n -> Boolean.TRUE.equals(fn.apply(n)));
                    case DoubleStream ds -> ds.filter(n -> Boolean.TRUE.equals(fn.apply(n)));
                    default -> throw new AntikytheraException("Unsupported primitive stream type for filter: "
                            + stream.getClass().getName());
                };
            }
            case "map" -> {
                UnaryOperator<Object> fn = toStreamFunction(finalArgs[0]);
                result = switch (stream) {
                    case IntStream is -> is.map(n -> ((Number) fn.apply(n)).intValue());
                    case LongStream ls -> ls.map(n -> ((Number) fn.apply(n)).longValue());
                    case DoubleStream ds -> ds.map(n -> ((Number) fn.apply(n)).doubleValue());
                    default -> throw new AntikytheraException("Unsupported primitive stream type for map: "
                            + stream.getClass().getName());
                };
            }
            case SORTED -> result = iface.getMethod(SORTED).invoke(stream);
            case DISTINCT -> result = iface.getMethod(DISTINCT).invoke(stream);
            case LIMIT -> {
                long n = ((Number) finalArgs[0]).longValue();
                result = iface.getMethod(LIMIT, long.class).invoke(stream, n);
            }
            case "skip" -> {
                long n = ((Number) finalArgs[0]).longValue();
                result = iface.getMethod("skip", long.class).invoke(stream, n);
            }
            case REDUCE -> {
                if (finalArgs.length == 1) {
                    BinaryOperator<Object> op = toStreamBinaryOperator(finalArgs[0]);
                    result = switch (stream) {
                        case IntStream is -> is.reduce((a, b) -> ((Number) op.apply(a, b)).intValue());
                        case LongStream ls -> ls.reduce((a, b) -> ((Number) op.apply(a, b)).longValue());
                        case DoubleStream ds -> ds.reduce((a, b) -> ((Number) op.apply(a, b)).doubleValue());
                        default -> throw new AntikytheraException("Unsupported primitive stream type for reduce: "
                                + stream.getClass().getName());
                    };
                } else {
                    BinaryOperator<Object> op = toStreamBinaryOperator(finalArgs[1]);
                    result = switch (stream) {
                        case IntStream is -> is.reduce(((Number) finalArgs[0]).intValue(),
                                (a, b) -> ((Number) op.apply(a, b)).intValue());
                        case LongStream ls -> ls.reduce(((Number) finalArgs[0]).longValue(),
                                (a, b) -> ((Number) op.apply(a, b)).longValue());
                        case DoubleStream ds -> ds.reduce(((Number) finalArgs[0]).doubleValue(),
                                (a, b) -> ((Number) op.apply(a, b)).doubleValue());
                        default -> throw new AntikytheraException("Unsupported primitive stream type for reduce: "
                                + stream.getClass().getName());
                    };
                }
            }
            case "forEach" -> {
                Consumer<Object> consumer = toStreamConsumer(finalArgs[0]);
                switch (stream) {
                    case IntStream is -> is.forEach(consumer::accept);
                    case LongStream ls -> ls.forEach(consumer::accept);
                    case DoubleStream ds -> ds.forEach(consumer::accept);
                    default -> throw new AntikytheraException("Unsupported primitive stream type for forEach: "
                            + stream.getClass().getName());
                }
                return new Variable(null);
            }
            case MAP_TO_OBJ -> {
                UnaryOperator<Object> fn = toStreamFunction(finalArgs[0]);
                result = switch (stream) {
                    case IntStream is -> IntStream.class.getMethod(MAP_TO_OBJ, IntFunction.class)
                            .invoke(is, (IntFunction<Object>) fn::apply);
                    case LongStream ls -> LongStream.class.getMethod(MAP_TO_OBJ, LongFunction.class)
                            .invoke(ls, (LongFunction<Object>) fn::apply);
                    case DoubleStream ds -> DoubleStream.class.getMethod(MAP_TO_OBJ, DoubleFunction.class)
                            .invoke(ds, (DoubleFunction<Object>) fn::apply);
                    default -> throw new AntikytheraException("Unsupported primitive stream type for mapToObj: "
                            + stream.getClass().getName());
                };
            }
            default -> { return null; }
        }
        Variable rv = new Variable(result);
        if (result != null) {
            rv.setClazz(result.getClass());
        }
        return rv;
    }

    // ── Type-adaptation helpers ─────────────────────────────────────────

    @SuppressWarnings("unchecked")
    static UnaryOperator<Object> toStreamFunction(Object arg) {
        if (arg instanceof UnaryOperator<?> uo) {
            return (UnaryOperator<Object>) uo;
        }
        if (arg instanceof Function<?, ?> f) {
            Function<Object, Object> fnRaw = (Function<Object, Object>) f;
            return fnRaw::apply;
        }
        throw new AntikytheraException("Expected Function for stream operation but got: "
                + (arg == null ? "null" : arg.getClass().getName()));
    }

    @SuppressWarnings("unchecked")
    static Consumer<Object> toStreamConsumer(Object arg) {
        if (arg instanceof Consumer<?> c) {
            return (Consumer<Object>) c;
        }
        if (arg instanceof Function<?, ?>) {
            UnaryOperator<Object> fn = toStreamFunction(arg);
            return fn::apply;
        }
        throw new AntikytheraException("Expected Consumer for stream operation but got: "
                + (arg == null ? "null" : arg.getClass().getName()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Comparator<Object> toStreamComparator(Object arg) {
        if (arg instanceof Comparator<?> c) {
            return (Comparator<Object>) c;
        }
        if (arg instanceof BiFunction<?, ?, ?> bf) {
            BiFunction bfRaw = bf;
            return (a, b) -> {
                Object r = bfRaw.apply(a, b);
                return r instanceof Number n ? n.intValue() : 0;
            };
        }
        throw new AntikytheraException("Expected Comparator for stream operation but got: "
                + (arg == null ? "null" : arg.getClass().getName()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static BinaryOperator<Object> toStreamBinaryOperator(Object arg) {
        if (arg instanceof BinaryOperator<?> bo) {
            return (BinaryOperator<Object>) bo;
        }
        if (arg instanceof BiFunction<?, ?, ?> bf) {
            BiFunction bfRaw = bf;
            return bfRaw::apply;
        }
        throw new AntikytheraException("Expected BinaryOperator for stream operation but got: "
                + (arg == null ? "null" : arg.getClass().getName()));
    }
}
