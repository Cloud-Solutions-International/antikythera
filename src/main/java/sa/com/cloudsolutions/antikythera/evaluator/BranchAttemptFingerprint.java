package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.Expression;

import java.util.Map;
import java.util.Set;

public final class BranchAttemptFingerprint {
    private BranchAttemptFingerprint() {
    }

    public static String fingerprintCombination(Map<Expression, Object> combination) {
        return combination.entrySet().stream()
                .sorted(Map.Entry.comparingByKey((a, b) -> a.toString().compareTo(b.toString())))
                .map(entry -> entry.getKey() + "=" + fingerprintValue(entry.getValue()))
                .reduce((left, right) -> left + "|" + right)
                .orElse("<empty>");
    }

    public static String fingerprintValue(Object value) {
        if (value instanceof Expression expression) {
            return expression.toString();
        }
        if (value instanceof java.util.List<?> list) {
            return list.stream().map(BranchAttemptFingerprint::fingerprintValue).toList().toString();
        }
        if (value instanceof Set<?> set) {
            return set.stream().map(BranchAttemptFingerprint::fingerprintValue).sorted().toList().toString();
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .map(entry -> fingerprintValue(entry.getKey()) + "->" + fingerprintValue(entry.getValue()))
                    .sorted()
                    .toList()
                    .toString();
        }
        return String.valueOf(value);
    }
}
