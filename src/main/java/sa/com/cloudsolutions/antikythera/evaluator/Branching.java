package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.MethodDeclaration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;

public class Branching {
    private static final HashMap<MethodDeclaration, PriorityQueue<LineOfCode>> conditionals = new HashMap<>();
    private static final HashMap<Integer, LineOfCode> branches = new HashMap<>();

    private Branching() {
    }

    public static void clear() {
        branches.clear();
        conditionals.clear();
    }

    public static void add(LineOfCode lineOfCode) {
        PriorityQueue<LineOfCode> queue = conditionals.computeIfAbsent(
            lineOfCode.getMethodDeclaration(),
            k -> new PriorityQueue<>(new LineOfCodeComparator())
        );
        queue.add(lineOfCode);
        branches.putIfAbsent(lineOfCode.getStatement().hashCode(), lineOfCode);
    }

    public static LineOfCode get(int hashCode) {
        return branches.get(hashCode);
    }

    public static List<LineOfCode> get(MethodDeclaration methodDeclaration) {
        PriorityQueue<LineOfCode> queue = conditionals.get(methodDeclaration);
        if (queue == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(queue);
    }

    public static List<Precondition> getApplicableConditions(MethodDeclaration methodDeclaration) {
        List<Precondition> applicableConditions = new ArrayList<>();
        PriorityQueue<LineOfCode> queue = conditionals.getOrDefault(methodDeclaration, new PriorityQueue<>(new LineOfCodeComparator()));

        for (LineOfCode lineOfCode : queue) {
            if (lineOfCode.getPathTaken() != LineOfCode.BOTH_PATHS) {
                applicableConditions.addAll(lineOfCode.getPreconditions());
            }
        }
        return applicableConditions;
    }

    public static int size(MethodDeclaration methodDeclaration)
    {
        PriorityQueue<LineOfCode> queue = conditionals.get(methodDeclaration);
        return queue != null ? queue.size() : 0;
    }

    public static LineOfCode getHighestPriority(MethodDeclaration md) {
        PriorityQueue<LineOfCode> queue = conditionals.get(md);
        return queue != null ? queue.remove() : null;
    }

    static class LineOfCodeComparator implements Comparator<LineOfCode> {
        @Override
        public int compare(LineOfCode a, LineOfCode b) {
            // Compare path states first (lower ordinal = higher priority)
            int pathComparison = Integer.compare(a.getPathTaken(), b.getPathTaken());
            if (pathComparison != 0) {
                return pathComparison;
            }
            // If path states are equal, compare by children count (fewer = higher priority)
            return Integer.compare(a.getChildren().size(), b.getChildren().size());
        }
    }
}
