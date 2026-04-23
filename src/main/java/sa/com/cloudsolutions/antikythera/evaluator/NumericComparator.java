package sa.com.cloudsolutions.antikythera.evaluator;


/**
 * Provides type-aware comparison of numeric, string, and {@link Comparable} values
 * with correct widening semantics and null handling mirroring JVM behavior.
 */
public class NumericComparator {
    private NumericComparator() {
    }

    /**
     * Mirrors JVM comparison semantics when one operand is null. In particular,
     * {@code LocalDateTime.compareTo(null)} throws {@link NullPointerException}; the previous
     * implementation fell through to {@code "Cannot compare"} and threw
     * {@link IllegalArgumentException} because {@code null instanceof Comparable} is false.
     */
    public static int compare(Object left, Object right) {
        if (left == null && right == null) {
            return 0;
        }
        if (right == null && left instanceof Comparable<?> lc) {
            @SuppressWarnings("unchecked")
            Comparable<Object> lcObj = (Comparable<Object>) left;
            return lcObj.compareTo(null);
        }
        if (left == null && right instanceof Comparable<?> rc) {
            @SuppressWarnings("unchecked")
            Comparable<Object> rcObj = (Comparable<Object>) right;
            return -rcObj.compareTo(null);
        }
        if (left == null || right == null) {
            throw new IllegalArgumentException("Cannot compare " + left + " and " + right);
        }
        return switch (left) {
            case Number leftNumber when right instanceof Number rightNumber -> {
                if (leftNumber instanceof Double || rightNumber instanceof Double) {
                    yield Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue());
                } else if (leftNumber instanceof Float || rightNumber instanceof Float) {
                    yield Float.compare(leftNumber.floatValue(), rightNumber.floatValue());
                } else if (leftNumber instanceof Long || rightNumber instanceof Long) {
                    yield Long.compare(leftNumber.longValue(), rightNumber.longValue());
                } else {
                    yield Integer.compare(leftNumber.intValue(), rightNumber.intValue());
                }
            }
            case String leftString when right instanceof String rightString ->
                leftString.compareTo(rightString);
            case Comparable leftComparable when right instanceof Comparable rightComparable ->
                leftComparable.compareTo(rightComparable);
            default -> throw new IllegalArgumentException("Cannot compare " + left + " and " + right);
        };
    }
}
