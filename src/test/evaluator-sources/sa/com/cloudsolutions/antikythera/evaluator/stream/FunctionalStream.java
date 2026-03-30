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
@SuppressWarnings({"java:S4276", "unused","java:S1612","java:S1117","java:S1602","java:S3655","java:S1192"})
public class FunctionalStream {

    private static final String VALUE_A = "A";
    private static final String VALUE_B = "B";

    List<String> names = new ArrayList<>(List.of(VALUE_A, VALUE_B));
    List<Integer> numbers = new ArrayList<>(List.of(1, 2, 3, 4));

    // --- P1: intermediate operations ---

    private List<String> streamMap() {
        return names.stream().map(s -> s.toLowerCase()).toList();
    }

    private List<String> streamFilter() {
        return names.stream()
                .filter(s -> s.equals(VALUE_A))
                .toList();
    }

    private long streamCount() {
        return names.stream().count();
    }

    private String streamFindFirst() {
        Optional<String> s = names.stream().findFirst();
        return s.get();
    }

    private boolean streamAnyMatch() {
        return names.stream().anyMatch(s -> s.equals(VALUE_A));
    }

    private boolean streamAllMatch() {
        return names.stream().allMatch(s -> s.length() > 0);
    }

    private boolean streamNoneMatch() {
        return names.stream().noneMatch(s -> s.equals("Z"));
    }

    private String streamMin() {
        Optional<String> s = names.stream().min((a, b) -> a.compareTo(b));
        return s.get();
    }

    private String streamMax() {
        Optional<String> s = names.stream().max((a, b) -> a.compareTo(b));
        return s.get();
    }

    private Integer streamReduce() {
        Optional<Integer> result = numbers.stream().reduce((a, b) -> a + b);
        return result.get();
    }

    private Integer streamReduceWithIdentity() {
        return numbers.stream().reduce(0, (a, b) -> a + b);
    }

    private List<String> streamLimit() {
        return names.stream().limit(1).toList();
    }

    private List<String> streamSkip() {
        return names.stream().skip(1).toList();
    }

    private List<Integer> streamDistinct() {
        List<Integer> ints = new ArrayList<>(List.of(1, 2, 2, 3, 3, 3));
        return ints.stream().distinct().sorted().toList();
    }

    private List<String> streamFlatMap() {
        List<List<String>> groups = List.of(List.of(VALUE_A), List.of(VALUE_B));
        return groups.stream()
                .flatMap(g -> g.stream())
                .toList();
    }

    private List<String> streamSorted() {
        List<String> input = new ArrayList<>(List.of("C", VALUE_A, VALUE_B));
        return input.stream().sorted().toList();
    }

    private List<String> streamSortedWithComparator() {
        List<String> input = new ArrayList<>(List.of(VALUE_A, "C", VALUE_B));
        return input.stream().sorted((a, b) -> b.compareTo(a)).toList();
    }

    // --- P3: additional Collectors ---

    private int groupBy() {
        Map<Integer, List<String>> result = names.stream()
                .collect(Collectors.groupingBy(s -> s.length()));
        return result.size();
    }

    private Long groupByWithCount() {
        Map<Integer, Long> result = names.stream()
                .collect(Collectors.groupingBy(s -> s.length(), Collectors.counting()));
        return result.get(1);
    }

    private String partitionByPredicate() {
        Map<Boolean, List<String>> result = names.stream()
                .collect(Collectors.partitioningBy(s -> s.equals(VALUE_A)));
        return result.get(true).get(0);
    }

    private int collectToSet() {
        Set<String> result = names.stream().collect(Collectors.toSet());
        return result.size();
    }

    // --- P4: primitive specialised streams ---

    private int intStreamRange() {
        return IntStream.range(1, 5).sum();
    }

    private int mapToIntSum() {
        List<Integer> ints = List.of(10, 20);
        return ints.stream().mapToInt(n -> n).sum();
    }

    private long mapToLongSum() {
        List<Integer> ints = List.of(10, 20);
        return ints.stream().mapToInt(n -> n).asLongStream().sum();
    }

    private List<Integer> mapToIntBoxed() {
        List<Integer> ints = List.of(1, 2);
        return ints.stream().mapToInt(n -> n).boxed().toList();
    }
}
