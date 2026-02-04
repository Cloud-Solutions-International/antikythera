package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.CallableDeclaration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;

public class Branching {
    private static final HashMap<CallableDeclaration<?>, PriorityQueue<LineOfCode>> conditionals = new HashMap<>();
    private static final HashMap<Integer, LineOfCode> branches = new HashMap<>();

    private Branching() {
    }

    public static void clear() {
        branches.clear();
        conditionals.clear();
    }

    public static void add(LineOfCode lineOfCode) {
        PriorityQueue<LineOfCode> queue = conditionals.computeIfAbsent(
            lineOfCode.getCallableDeclaration(),
            k -> new PriorityQueue<>(new LineOfCodeComparator())
        );
        queue.add(lineOfCode);
        branches.putIfAbsent(lineOfCode.getStatement().hashCode(), lineOfCode);
    }

    public static LineOfCode get(int hashCode) {
        return branches.get(hashCode);
    }

    public static List<LineOfCode> get(CallableDeclaration<?> methodDeclaration) {
        PriorityQueue<LineOfCode> queue = conditionals.get(methodDeclaration);
        if (queue == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(queue);
    }

    public static List<Precondition> getApplicableConditions(CallableDeclaration<?> methodDeclaration) {
        List<Precondition> applicableConditions = new ArrayList<>();

        for (LineOfCode lineOfCode : branches.values()) {
            if (lineOfCode.getPathTaken() != LineOfCode.BOTH_PATHS && lineOfCode.getCallableDeclaration().equals(methodDeclaration)) {
                applicableConditions.addAll(lineOfCode.getPreconditions());
            }
        }
        return applicableConditions;
    }

    public static int size(CallableDeclaration<?> methodDeclaration)
    {
        PriorityQueue<LineOfCode> queue = conditionals.get(methodDeclaration);
        return queue != null ? queue.size() : 0;
    }

    public static LineOfCode getHighestPriority(CallableDeclaration<?> md) {
        PriorityQueue<LineOfCode> queue = conditionals.get(md);
        return queue != null ? queue.remove() : null;
    }

    static class LineOfCodeComparator implements Comparator<LineOfCode> {
        @Override
        public int compare(LineOfCode a, LineOfCode b) {
            // BOTH_PATHS gets lowest priority
            if (a.getPathTaken() == LineOfCode.BOTH_PATHS && b.getPathTaken() != LineOfCode.BOTH_PATHS) return 1;
            if (b.getPathTaken() == LineOfCode.BOTH_PATHS && a.getPathTaken() != LineOfCode.BOTH_PATHS) return -1;

            // Compare by children count (fewer = higher priority)
            int childrenComparison = Integer.compare(a.getChildren().size(), b.getChildren().size());
            if (childrenComparison != 0) {
                return childrenComparison;
            }

            // Break ties by path state priority: FALSE > TRUE > UNTRAVELLED
            int aValue = getPathPriority(a.getPathTaken());
            int bValue = getPathPriority(b.getPathTaken());
            return Integer.compare(aValue, bValue);
        }

        private int getPathPriority(int pathTaken) {
            return switch (pathTaken) {
                case LineOfCode.FALSE_PATH -> 0;   // Highest
                case LineOfCode.TRUE_PATH -> 1;    // Second
                case LineOfCode.UNTRAVELLED -> 2;  // Third
                default -> 3;                      // BOTH_PATHS and others lowest
            };
        }
    }
}
