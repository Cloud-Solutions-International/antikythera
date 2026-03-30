package sa.com.cloudsolutions.antikythera.evaluator.stream;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Self-contained test class for stream operation evaluation.
 * Uses only standard Java types; no dependency on antikythera-test-helper.
 */
@SuppressWarnings({"java:S106","java:S4276", "unused","java:S1612","java:S1117","java:S1602","java:S3655","java:S1192"})
public class FunctionalStream {

    private static final String VALUE_A = "A";
    private static final String VALUE_B = "B";

    List<String> names = new ArrayList<>(List.of(VALUE_A, VALUE_B));
    List<Integer> numbers = new ArrayList<>(List.of(1, 2, 3, 4));

    // --- P1: intermediate operations ---

    private void streamMap() {
        List<String> result = names.stream().map(s -> s.toLowerCase()).toList();
        System.out.println(result);
    }

    private void streamFilter() {
        List<String> result = names.stream()
                .filter(s -> s.equals(VALUE_A))
                .toList();
        System.out.println(result);
    }

    private void streamCount() {
        long count = names.stream().count();
        System.out.println(count);
    }

    private void streamFindFirst() {
        Optional<String> s = names.stream().findFirst();
        System.out.println(s.get());
    }

    private void streamAnyMatch() {
        boolean result = names.stream().anyMatch(s -> s.equals(VALUE_A));
        System.out.println(result);
    }

    private void streamAllMatch() {
        boolean result = names.stream().allMatch(s -> s.length() > 0);
        System.out.println(result);
    }

    private void streamNoneMatch() {
        boolean result = names.stream().noneMatch(s -> s.equals("Z"));
        System.out.println(result);
    }

    private void streamMin() {
        Optional<String> s = names.stream().min((a, b) -> a.compareTo(b));
        System.out.println(s.get());
    }

    private void streamMax() {
        Optional<String> s = names.stream().max((a, b) -> a.compareTo(b));
        System.out.println(s.get());
    }

    private void streamReduce() {
        Optional<Integer> result = numbers.stream().reduce((a, b) -> a + b);
        System.out.println(result.get());
    }

    private void streamReduceWithIdentity() {
        Integer result = numbers.stream().reduce(0, (a, b) -> a + b);
        System.out.println(result);
    }

    private void streamLimit() {
        List<String> result = names.stream().limit(1).toList();
        System.out.println(result);
    }

    private void streamSkip() {
        List<String> result = names.stream().skip(1).toList();
        System.out.println(result);
    }

    private void streamDistinct() {
        List<Integer> ints = new ArrayList<>(List.of(1, 2, 2, 3, 3, 3));
        List<Integer> result = ints.stream().distinct().sorted().toList();
        System.out.println(result);
    }

    private void streamFlatMap() {
        List<List<String>> groups = List.of(List.of(VALUE_A), List.of(VALUE_B));
        List<String> result = groups.stream()
                .flatMap(g -> g.stream())
                .toList();
        System.out.println(result);
    }

    private void streamSorted() {
        List<String> input = new ArrayList<>(List.of("C", VALUE_A, VALUE_B));
        List<String> result = input.stream().sorted().toList();
        System.out.println(result);
    }

    private void streamSortedWithComparator() {
        List<String> input = new ArrayList<>(List.of(VALUE_A, "C", VALUE_B));
        List<String> result = input.stream().sorted((a, b) -> b.compareTo(a)).toList();
        System.out.println(result);
    }

    // --- P3: additional Collectors ---

    private void groupBy() {
        Map<Integer, List<String>> result = names.stream()
                .collect(Collectors.groupingBy(s -> s.length()));
        System.out.println(result.size());
    }

    private void groupByWithCount() {
        Map<Integer, Long> result = names.stream()
                .collect(Collectors.groupingBy(s -> s.length(), Collectors.counting()));
        System.out.println(result.get(1));
    }

    private void partitionByPredicate() {
        Map<Boolean, List<String>> result = names.stream()
                .collect(Collectors.partitioningBy(s -> s.equals(VALUE_A)));
        System.out.println(result.get(true).get(0));
    }

    private void collectToSet() {
        Set<String> result = names.stream().collect(Collectors.toSet());
        System.out.println(result.size());
    }

    // --- P4: primitive specialised streams ---

    private void intStreamRange() {
        int sum = IntStream.range(1, 5).sum();
        System.out.println(sum);
    }

    private void mapToIntSum() {
        List<Integer> ints = List.of(10, 20);
        int sum = ints.stream().mapToInt(n -> n).sum();
        System.out.println(sum);
    }

    private void mapToLongSum() {
        List<Integer> ints = List.of(10, 20);
        long sum = ints.stream().mapToInt(n -> n).asLongStream().sum();
        System.out.println(sum);
    }

    private void mapToIntBoxed() {
        List<Integer> ints = List.of(1, 2);
        List<Integer> result = ints.stream().mapToInt(n -> n).boxed().toList();
        System.out.println(result);
    }
}
