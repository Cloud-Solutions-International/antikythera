package sa.com.cloudsolutions.antikythera.evaluator;


public class NumericComparator {
    private NumericComparator() {
    }

    public static int compare(Object left, Object right) {
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
