package sa.com.cloudsolutions.antikythera.evaluator;


class NumericComparator {
    private NumericComparator() {
    }

    public static int compare(Object left, Object right) {
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            if (leftNumber instanceof Double || rightNumber instanceof Double) {
                return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue());
            } else if (leftNumber instanceof Float || rightNumber instanceof Float) {
                return Float.compare(leftNumber.floatValue(), rightNumber.floatValue());
            } else if (leftNumber instanceof Long || rightNumber instanceof Long) {
                return Long.compare(leftNumber.longValue(), rightNumber.longValue());
            } else {
                return Integer.compare(leftNumber.intValue(), rightNumber.intValue());
            }
        } else if (left instanceof String leftString && right instanceof String rightString) {
            return leftString.compareTo(rightString);
        } else if (left instanceof Comparable leftComparable && right instanceof Comparable rightComparable) {
            return leftComparable.compareTo(rightComparable);
        } else {
            throw new IllegalArgumentException("Cannot compare " + left + " and " + right);
        }
    }
}
