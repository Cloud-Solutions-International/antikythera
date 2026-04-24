package sa.com.cloudsolutions.antikythera.evaluator;

import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Direct unit tests for {@link StreamEvaluator} dispatch methods and
 * type-adaptation helpers, providing coverage for the extracted stream
 * evaluation logic.
 */
class StreamEvaluatorTest {

    // ── handleStreamMethods ────────────────────────────────────────────

    @Test
    void handleStreamMethodsWithObjectStream() throws Exception {
        Variable v = new Variable(Stream.of("a", "b", "c"));
        v.setClazz(Stream.class);
        ReflectionArguments args = new ReflectionArguments("count", new Object[0], new Class<?>[0]);

        Variable result = StreamEvaluator.handleStreamMethods(v, args);
        assertNotNull(result);
        assertEquals(3L, result.getValue());
    }

    @Test
    void handleStreamMethodsWithIntStream() throws Exception {
        Variable v = new Variable(IntStream.of(1, 2, 3));
        v.setClazz(IntStream.class);
        ReflectionArguments args = new ReflectionArguments("sum", new Object[0], new Class<?>[0]);

        Variable result = StreamEvaluator.handleStreamMethods(v, args);
        assertNotNull(result);
        assertEquals(6, result.getValue());
    }

    @Test
    void handleStreamMethodsWithNonStream() throws Exception {
        Variable v = new Variable("not a stream");
        ReflectionArguments args = new ReflectionArguments("count", new Object[0], new Class<?>[0]);

        Variable result = StreamEvaluator.handleStreamMethods(v, args);
        assertNull(result);
    }

    // ── dispatchIntermediateOp ──────────────────────────────────────────

    @Test
    void intermediateFilter() throws Exception {
        Stream<String> stream = Stream.of("a", "bb", "ccc");
        UnaryOperator<Object> fn = x -> ((String) x).length() > 1;
        StreamEvaluator.IntermediateResult ir = StreamEvaluator.dispatchIntermediateOp("filter", stream, new Object[]{fn});
        assertTrue(ir.handled());
        assertNotNull(ir.result());
        Object val = ir.result().getValue();
        assertInstanceOf(Stream.class, val);
        assertEquals(List.of("bb", "ccc"), ((Stream<?>) val).toList());
    }

    @Test
    void intermediateMap() throws Exception {
        Stream<String> stream = Stream.of("a", "b");
        UnaryOperator<Object> fn = x -> ((String) x).toUpperCase();
        StreamEvaluator.IntermediateResult ir = StreamEvaluator.dispatchIntermediateOp("map", stream, new Object[]{fn});
        assertTrue(ir.handled());
        assertEquals(List.of("A", "B"), ((Stream<?>) ir.result().getValue()).toList());
    }

    @Test
    void intermediateFlatMap() throws Exception {
        Stream<String> stream = Stream.of("a,b", "c");
        UnaryOperator<Object> fn = x -> Stream.of(((String) x).split(","));
        StreamEvaluator.IntermediateResult ir = StreamEvaluator.dispatchIntermediateOp("flatMap", stream, new Object[]{fn});
        assertTrue(ir.handled());
        assertEquals(List.of("a", "b", "c"), ((Stream<?>) ir.result().getValue()).toList());
    }

    @Test
    void intermediatePeek() throws Exception {
        Stream<String> stream = Stream.of("x", "y");
        StringBuilder sb = new StringBuilder();
        Consumer<Object> consumer = x -> sb.append(x);
        StreamEvaluator.IntermediateResult ir = StreamEvaluator.dispatchIntermediateOp("peek", stream, new Object[]{consumer});
        assertTrue(ir.handled());
        // Consume the stream to trigger peek
        ((Stream<?>) ir.result().getValue()).toList();
        assertEquals("xy", sb.toString());
    }

    @Test
    void intermediateSorted() throws Exception {
        Stream<String> stream = Stream.of("c", "a", "b");
        StreamEvaluator.IntermediateResult ir = StreamEvaluator.dispatchIntermediateOp("sorted", stream, new Object[0]);
        assertTrue(ir.handled());
        assertEquals(List.of("a", "b", "c"), ((Stream<?>) ir.result().getValue()).toList());
    }

    @Test
    void intermediateSortedWithComparator() throws Exception {
        Stream<String> stream = Stream.of("a", "b", "c");
        Comparator<Object> comp = (a, b) -> ((String) b).compareTo((String) a);
        StreamEvaluator.IntermediateResult ir = StreamEvaluator.dispatchIntermediateOp("sorted", stream, new Object[]{comp});
        assertTrue(ir.handled());
        assertEquals(List.of("c", "b", "a"), ((Stream<?>) ir.result().getValue()).toList());
    }

    @Test
    void intermediateDistinct() throws Exception {
        Stream<Integer> stream = Stream.of(1, 2, 2, 3);
        StreamEvaluator.IntermediateResult ir = StreamEvaluator.dispatchIntermediateOp("distinct", stream, new Object[0]);
        assertTrue(ir.handled());
        assertEquals(List.of(1, 2, 3), ((Stream<?>) ir.result().getValue()).toList());
    }

    @Test
    void intermediateLimit() throws Exception {
        Stream<Integer> stream = Stream.of(1, 2, 3, 4);
        StreamEvaluator.IntermediateResult ir = StreamEvaluator.dispatchIntermediateOp("limit", stream, new Object[]{2L});
        assertTrue(ir.handled());
        assertEquals(List.of(1, 2), ((Stream<?>) ir.result().getValue()).toList());
    }

    @Test
    void intermediateSkip() throws Exception {
        Stream<Integer> stream = Stream.of(1, 2, 3);
        StreamEvaluator.IntermediateResult ir = StreamEvaluator.dispatchIntermediateOp("skip", stream, new Object[]{1L});
        assertTrue(ir.handled());
        assertEquals(List.of(2, 3), ((Stream<?>) ir.result().getValue()).toList());
    }

    @Test
    void intermediateMapToInt() throws Exception {
        Stream<String> stream = Stream.of("a", "bb");
        UnaryOperator<Object> fn = x -> ((String) x).length();
        StreamEvaluator.IntermediateResult ir = StreamEvaluator.dispatchIntermediateOp("mapToInt", stream, new Object[]{fn});
        assertTrue(ir.handled());
        assertNotNull(ir.result().getValue());
    }

    @Test
    void intermediateMapToLong() throws Exception {
        Stream<String> stream = Stream.of("a", "bb");
        UnaryOperator<Object> fn = x -> (long) ((String) x).length();
        StreamEvaluator.IntermediateResult ir = StreamEvaluator.dispatchIntermediateOp("mapToLong", stream, new Object[]{fn});
        assertTrue(ir.handled());
        assertNotNull(ir.result().getValue());
    }

    @Test
    void intermediateMapToDouble() throws Exception {
        Stream<String> stream = Stream.of("a", "bb");
        UnaryOperator<Object> fn = x -> (double) ((String) x).length();
        StreamEvaluator.IntermediateResult ir = StreamEvaluator.dispatchIntermediateOp("mapToDouble", stream, new Object[]{fn});
        assertTrue(ir.handled());
        assertNotNull(ir.result().getValue());
    }

    @Test
    void intermediateTakeWhile() throws Exception {
        Stream<Integer> stream = Stream.of(1, 2, 3, 4, 5);
        UnaryOperator<Object> fn = x -> ((Integer) x) < 4;
        StreamEvaluator.IntermediateResult ir = StreamEvaluator.dispatchIntermediateOp("takeWhile", stream, new Object[]{fn});
        assertTrue(ir.handled());
        assertEquals(List.of(1, 2, 3), ((Stream<?>) ir.result().getValue()).toList());
    }

    @Test
    void intermediateDropWhile() throws Exception {
        Stream<Integer> stream = Stream.of(1, 2, 3, 4, 5);
        UnaryOperator<Object> fn = x -> ((Integer) x) < 3;
        StreamEvaluator.IntermediateResult ir = StreamEvaluator.dispatchIntermediateOp("dropWhile", stream, new Object[]{fn});
        assertTrue(ir.handled());
        assertEquals(List.of(3, 4, 5), ((Stream<?>) ir.result().getValue()).toList());
    }

    @Test
    void intermediateUnknownReturnsNotHandled() throws Exception {
        Stream<String> stream = Stream.of("a");
        StreamEvaluator.IntermediateResult ir = StreamEvaluator.dispatchIntermediateOp("unknownOp", stream, new Object[0]);
        assertFalse(ir.handled());
        assertNull(ir.result());
    }

    // ── dispatchTerminalOp ─────────────────────────────────────────────

    @Test
    void terminalForEach() throws Exception {
        Stream<String> stream = Stream.of("x", "y");
        StringBuilder sb = new StringBuilder();
        Consumer<Object> action = x -> sb.append(x);
        Variable result = StreamEvaluator.dispatchTerminalOp("forEach", stream, new Object[]{action});
        assertNotNull(result);
        assertNull(result.getValue());
        assertEquals("xy", sb.toString());
    }

    @Test
    void terminalCount() throws Exception {
        Stream<String> stream = Stream.of("a", "b", "c");
        Variable result = StreamEvaluator.dispatchTerminalOp("count", stream, new Object[0]);
        assertNotNull(result);
        assertEquals(3L, result.getValue());
    }

    @Test
    void terminalToList() throws Exception {
        Stream<String> stream = Stream.of("a", "b");
        Variable result = StreamEvaluator.dispatchTerminalOp("toList", stream, new Object[0]);
        assertNotNull(result);
        assertEquals(List.of("a", "b"), result.getValue());
    }

    @Test
    void terminalFindFirst() throws Exception {
        Stream<String> stream = Stream.of("first", "second");
        Variable result = StreamEvaluator.dispatchTerminalOp("findFirst", stream, new Object[0]);
        assertNotNull(result);
        assertInstanceOf(Optional.class, result.getValue());
        assertEquals(Optional.of("first"), result.getValue());
    }

    @Test
    void terminalAnyMatch() throws Exception {
        Stream<String> stream = Stream.of("a", "bb");
        UnaryOperator<Object> fn = x -> ((String) x).length() > 1;
        Variable result = StreamEvaluator.dispatchTerminalOp("anyMatch", stream, new Object[]{fn});
        assertNotNull(result);
        assertEquals(true, result.getValue());
    }

    @Test
    void terminalAllMatch() throws Exception {
        Stream<String> stream = Stream.of("aa", "bb");
        UnaryOperator<Object> fn = x -> ((String) x).length() == 2;
        Variable result = StreamEvaluator.dispatchTerminalOp("allMatch", stream, new Object[]{fn});
        assertNotNull(result);
        assertEquals(true, result.getValue());
    }

    @Test
    void terminalNoneMatch() throws Exception {
        Stream<String> stream = Stream.of("a", "b");
        UnaryOperator<Object> fn = x -> ((String) x).length() > 5;
        Variable result = StreamEvaluator.dispatchTerminalOp("noneMatch", stream, new Object[]{fn});
        assertNotNull(result);
        assertEquals(true, result.getValue());
    }

    @Test
    void terminalMin() throws Exception {
        Stream<Integer> stream = Stream.of(3, 1, 2);
        Comparator<Object> comp = (a, b) -> ((Integer) a).compareTo((Integer) b);
        Variable result = StreamEvaluator.dispatchTerminalOp("min", stream, new Object[]{comp});
        assertNotNull(result);
        assertEquals(Optional.of(1), result.getValue());
    }

    @Test
    void terminalMax() throws Exception {
        Stream<Integer> stream = Stream.of(3, 1, 2);
        Comparator<Object> comp = (a, b) -> ((Integer) a).compareTo((Integer) b);
        Variable result = StreamEvaluator.dispatchTerminalOp("max", stream, new Object[]{comp});
        assertNotNull(result);
        assertEquals(Optional.of(3), result.getValue());
    }

    @Test
    void terminalReduceOneArg() throws Exception {
        Stream<Integer> stream = Stream.of(1, 2, 3);
        BinaryOperator<Object> op = (a, b) -> (Integer) a + (Integer) b;
        Variable result = StreamEvaluator.dispatchTerminalOp("reduce", stream, new Object[]{op});
        assertNotNull(result);
        assertEquals(Optional.of(6), result.getValue());
    }

    @Test
    void terminalReduceTwoArgs() throws Exception {
        Stream<Integer> stream = Stream.of(1, 2, 3);
        BinaryOperator<Object> op = (a, b) -> (Integer) a + (Integer) b;
        Variable result = StreamEvaluator.dispatchTerminalOp("reduce", stream, new Object[]{0, op});
        assertNotNull(result);
        assertEquals(6, result.getValue());
    }

    @Test
    void terminalToArray() throws Exception {
        Stream<String> stream = Stream.of("a", "b");
        Variable result = StreamEvaluator.dispatchTerminalOp("toArray", stream, new Object[0]);
        assertNotNull(result);
        assertArrayEquals(new Object[]{"a", "b"}, (Object[]) result.getValue());
    }

    @Test
    void terminalFindAny() throws Exception {
        Stream<String> stream = Stream.of("only");
        Variable result = StreamEvaluator.dispatchTerminalOp("findAny", stream, new Object[0]);
        assertNotNull(result);
        assertInstanceOf(Optional.class, result.getValue());
        assertEquals(Optional.of("only"), result.getValue());
    }

    @Test
    void terminalCollectThreeArgs() throws Exception {
        Stream<String> stream = Stream.of("a", "b", "c");
        Supplier<Object> supplier = () -> new ArrayList<>();
        BiConsumer<Object, Object> accumulator = (list, item) -> ((List<Object>) list).add(item);
        BiConsumer<Object, Object> combiner = (list1, list2) -> ((List<Object>) list1).addAll((List<Object>) list2);
        Variable result = StreamEvaluator.dispatchTerminalOp("collect", stream, new Object[]{supplier, accumulator, combiner});
        assertNotNull(result);
        assertEquals(List.of("a", "b", "c"), result.getValue());
    }

    @Test
    void terminalReduceThreeArgs() throws Exception {
        Stream<Integer> stream = Stream.of(1, 2, 3);
        BiFunction<Object, Object, Object> accumulator = (a, b) -> (Integer) a + (Integer) b;
        BinaryOperator<Object> combiner = (a, b) -> (Integer) a + (Integer) b;
        Variable result = StreamEvaluator.dispatchTerminalOp("reduce", stream, new Object[]{0, accumulator, combiner});
        assertNotNull(result);
        assertEquals(6, result.getValue());
    }

    @Test
    void terminalUnknownReturnsNull() throws Exception {
        Stream<String> stream = Stream.of("a");
        Variable result = StreamEvaluator.dispatchTerminalOp("unknownTerminal", stream, new Object[0]);
        assertNull(result);
    }

    // ── dispatchPrimitiveStreamOp ──────────────────────────────────────

    @Test
    void primitiveIntStreamSum() throws Exception {
        Variable result = StreamEvaluator.dispatchPrimitiveStreamOp("sum", IntStream.of(1, 2, 3), new Object[0]);
        assertNotNull(result);
        assertEquals(6, result.getValue());
    }

    @Test
    void primitiveLongStreamSum() throws Exception {
        Variable result = StreamEvaluator.dispatchPrimitiveStreamOp("sum", LongStream.of(10L, 20L), new Object[0]);
        assertNotNull(result);
        assertEquals(30L, result.getValue());
    }

    @Test
    void primitiveDoubleStreamSum() throws Exception {
        Variable result = StreamEvaluator.dispatchPrimitiveStreamOp("sum", DoubleStream.of(1.5, 2.5), new Object[0]);
        assertNotNull(result);
        assertEquals(4.0, result.getValue());
    }

    @Test
    void primitiveIntStreamCount() throws Exception {
        Variable result = StreamEvaluator.dispatchPrimitiveStreamOp("count", IntStream.of(1, 2), new Object[0]);
        assertNotNull(result);
        assertEquals(2L, result.getValue());
    }

    @Test
    void primitiveIntStreamBoxed() throws Exception {
        Variable result = StreamEvaluator.dispatchPrimitiveStreamOp("boxed", IntStream.of(1, 2), new Object[0]);
        assertNotNull(result);
        assertInstanceOf(Stream.class, result.getValue());
    }

    @Test
    void primitiveIntStreamFilter() throws Exception {
        UnaryOperator<Object> fn = x -> ((Number) x).intValue() > 1;
        Variable result = StreamEvaluator.dispatchPrimitiveStreamOp("filter", IntStream.of(1, 2, 3), new Object[]{fn});
        assertNotNull(result);
        assertInstanceOf(IntStream.class, result.getValue());
    }

    @Test
    void primitiveIntStreamMap() throws Exception {
        UnaryOperator<Object> fn = x -> ((Number) x).intValue() * 2;
        Variable result = StreamEvaluator.dispatchPrimitiveStreamOp("map", IntStream.of(1, 2), new Object[]{fn});
        assertNotNull(result);
        assertInstanceOf(IntStream.class, result.getValue());
    }

    @Test
    void primitiveIntStreamSorted() throws Exception {
        Variable result = StreamEvaluator.dispatchPrimitiveStreamOp("sorted", IntStream.of(3, 1, 2), new Object[0]);
        assertNotNull(result);
        assertInstanceOf(IntStream.class, result.getValue());
    }

    @Test
    void primitiveIntStreamDistinct() throws Exception {
        Variable result = StreamEvaluator.dispatchPrimitiveStreamOp("distinct", IntStream.of(1, 1, 2), new Object[0]);
        assertNotNull(result);
        assertInstanceOf(IntStream.class, result.getValue());
    }

    @Test
    void primitiveIntStreamLimit() throws Exception {
        Variable result = StreamEvaluator.dispatchPrimitiveStreamOp("limit", IntStream.of(1, 2, 3), new Object[]{2L});
        assertNotNull(result);
        assertInstanceOf(IntStream.class, result.getValue());
    }

    @Test
    void primitiveIntStreamSkip() throws Exception {
        Variable result = StreamEvaluator.dispatchPrimitiveStreamOp("skip", IntStream.of(1, 2, 3), new Object[]{1L});
        assertNotNull(result);
        assertInstanceOf(IntStream.class, result.getValue());
    }

    @Test
    void primitiveIntStreamReduceOneArg() throws Exception {
        BinaryOperator<Object> op = (a, b) -> (Integer) a + (Integer) b;
        Variable result = StreamEvaluator.dispatchPrimitiveStreamOp("reduce", IntStream.of(1, 2, 3), new Object[]{op});
        assertNotNull(result);
    }

    @Test
    void primitiveIntStreamReduceTwoArgs() throws Exception {
        BinaryOperator<Object> op = (a, b) -> (Integer) a + (Integer) b;
        Variable result = StreamEvaluator.dispatchPrimitiveStreamOp("reduce", IntStream.of(1, 2, 3), new Object[]{0, op});
        assertNotNull(result);
        assertEquals(6, result.getValue());
    }

    @Test
    void primitiveIntStreamForEach() throws Exception {
        StringBuilder sb = new StringBuilder();
        Consumer<Object> consumer = x -> sb.append(x);
        Variable result = StreamEvaluator.dispatchPrimitiveStreamOp("forEach", IntStream.of(1, 2), new Object[]{consumer});
        assertNotNull(result);
        assertNull(result.getValue());
        assertEquals("12", sb.toString());
    }

    @Test
    void primitiveIntStreamMapToObj() throws Exception {
        UnaryOperator<Object> fn = x -> "v" + x;
        Variable result = StreamEvaluator.dispatchPrimitiveStreamOp("mapToObj", IntStream.of(1, 2), new Object[]{fn});
        assertNotNull(result);
        assertInstanceOf(Stream.class, result.getValue());
    }

    @Test
    void primitiveIntStreamAsLongStream() throws Exception {
        Variable result = StreamEvaluator.dispatchPrimitiveStreamOp("asLongStream", IntStream.of(1), new Object[0]);
        assertNotNull(result);
        assertInstanceOf(LongStream.class, result.getValue());
    }

    @Test
    void primitiveIntStreamAsDoubleStream() throws Exception {
        Variable result = StreamEvaluator.dispatchPrimitiveStreamOp("asDoubleStream", IntStream.of(1), new Object[0]);
        assertNotNull(result);
        assertInstanceOf(DoubleStream.class, result.getValue());
    }

    @Test
    void primitiveLongStreamAsDoubleStream() throws Exception {
        Variable result = StreamEvaluator.dispatchPrimitiveStreamOp("asDoubleStream", LongStream.of(1L), new Object[0]);
        assertNotNull(result);
        assertInstanceOf(DoubleStream.class, result.getValue());
    }

    @Test
    void primitiveUnknownReturnsNull() throws Exception {
        Variable result = StreamEvaluator.dispatchPrimitiveStreamOp("unknownPrimitiveOp", IntStream.of(1), new Object[0]);
        assertNull(result);
    }

    // ── Type-adaptation helpers ────────────────────────────────────────

    @Test
    void toStreamFunctionWithUnaryOperator() {
        UnaryOperator<Object> uo = x -> x.toString().toUpperCase();
        UnaryOperator<Object> result = StreamEvaluator.toStreamFunction(uo);
        assertEquals("HELLO", result.apply("hello"));
    }

    @Test
    void toStreamFunctionWithFunction() {
        Function<Object, Object> fn = x -> x.toString().length();
        UnaryOperator<Object> result = StreamEvaluator.toStreamFunction(fn);
        assertEquals(5, result.apply("hello"));
    }

    @Test
    void toStreamFunctionWithInvalidArgThrows() {
        assertThrows(AntikytheraException.class, () -> StreamEvaluator.toStreamFunction("not a function"));
    }

    @Test
    void toStreamFunctionWithNullArgThrows() {
        assertThrows(AntikytheraException.class, () -> StreamEvaluator.toStreamFunction(null));
    }

    @Test
    void toStreamConsumerWithConsumer() {
        StringBuilder sb = new StringBuilder();
        Consumer<Object> c = x -> sb.append(x);
        Consumer<Object> result = StreamEvaluator.toStreamConsumer(c);
        result.accept("test");
        assertEquals("test", sb.toString());
    }

    @Test
    void toStreamConsumerWithFunction() {
        Function<Object, Object> fn = x -> x;
        Consumer<Object> result = StreamEvaluator.toStreamConsumer(fn);
        assertNotNull(result);
    }

    @Test
    void toStreamConsumerWithInvalidArgThrows() {
        assertThrows(AntikytheraException.class, () -> StreamEvaluator.toStreamConsumer("not a consumer"));
    }

    @Test
    void toStreamComparatorWithComparator() {
        Comparator<Object> comp = (a, b) -> ((String) a).compareTo((String) b);
        Comparator<Object> result = StreamEvaluator.toStreamComparator(comp);
        assertTrue(result.compare("a", "b") < 0);
    }

    @Test
    void toStreamComparatorWithBiFunction() {
        BiFunction<Object, Object, Object> bf = (a, b) -> ((String) a).compareTo((String) b);
        Comparator<Object> result = StreamEvaluator.toStreamComparator(bf);
        assertTrue(result.compare("a", "b") < 0);
    }

    @Test
    void toStreamComparatorWithInvalidArgThrows() {
        assertThrows(AntikytheraException.class, () -> StreamEvaluator.toStreamComparator("not a comparator"));
    }

    @Test
    void toStreamBinaryOperatorWithBinaryOperator() {
        BinaryOperator<Object> bo = (a, b) -> (Integer) a + (Integer) b;
        BinaryOperator<Object> result = StreamEvaluator.toStreamBinaryOperator(bo);
        assertEquals(3, result.apply(1, 2));
    }

    @Test
    void toStreamBinaryOperatorWithBiFunction() {
        BiFunction<Object, Object, Object> bf = (a, b) -> (Integer) a + (Integer) b;
        BinaryOperator<Object> result = StreamEvaluator.toStreamBinaryOperator(bf);
        assertEquals(3, result.apply(1, 2));
    }

    @Test
    void toStreamBinaryOperatorWithInvalidArgThrows() {
        assertThrows(AntikytheraException.class, () -> StreamEvaluator.toStreamBinaryOperator("not an operator"));
    }
}
