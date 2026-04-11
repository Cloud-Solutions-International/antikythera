package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.stream.Collectors;

public class Branching {
    private static final HashMap<CallableDeclaration<?>, PriorityQueue<LineOfCode>> conditionals = new HashMap<>();
    private static final HashMap<Integer, LineOfCode> branches = new HashMap<>();
    private static final BranchAttemptPlanner PLANNER = new BranchAttemptPlanner();

    private Branching() {
    }

    public static void clear() {
        branches.clear();
        conditionals.clear();
        PLANNER.clear();
    }

    public static void add(LineOfCode lineOfCode) {
        attachPredecessors(lineOfCode);
        if (lineOfCode.shouldSchedule()) {
            PriorityQueue<LineOfCode> queue = conditionals.computeIfAbsent(
                lineOfCode.getCallableDeclaration(),
                k -> new PriorityQueue<>(new LineOfCodeComparator())
            );
            queue.add(lineOfCode);
        }
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
        return getBranchAttempt(methodDeclaration, null).applicableConditions();
    }

    public static BranchAttempt getBranchAttempt(CallableDeclaration<?> methodDeclaration, LineOfCode target) {
        List<LineOfCode> relevantBranches = branches.values().stream()
                .filter(lineOfCode -> lineOfCode.getCallableDeclaration().equals(methodDeclaration))
                .collect(Collectors.toList());
        return PLANNER.plan(methodDeclaration, target, relevantBranches);
    }

    public static BranchAttempt selectTargetAttempt(LineOfCode target, BranchSide side,
                                                    List<java.util.Map<com.github.javaparser.ast.expr.Expression, Object>> combinations) {
        return PLANNER.selectNextAttempt(target, side, combinations);
    }

    private static void attachPredecessors(LineOfCode lineOfCode) {
        if (lineOfCode.getParent() != null) {
            lineOfCode.addPredecessor(lineOfCode.getParent());
            lineOfCode.getParent().getPredecessors().forEach(lineOfCode::addPredecessor);
        }

        LineOfCode priorSibling = findNearestPriorSibling(lineOfCode);
        if (priorSibling != null) {
            lineOfCode.addPredecessor(priorSibling);
            priorSibling.getPredecessors().forEach(lineOfCode::addPredecessor);
        }
    }

    private static LineOfCode findNearestPriorSibling(LineOfCode lineOfCode) {
        BlockStmt targetBlock = findEnclosingBlock(lineOfCode);
        if (targetBlock == null) {
            return null;
        }

        int targetOrder = getSourceOrder(lineOfCode);
        LineOfCode best = null;
        int bestOrder = Integer.MIN_VALUE;
        for (LineOfCode candidate : branches.values()) {
            if (candidate.equals(lineOfCode)) {
                continue;
            }
            if (!candidate.getCallableDeclaration().equals(lineOfCode.getCallableDeclaration())) {
                continue;
            }
            if (candidate.getParent() != lineOfCode.getParent()) {
                continue;
            }
            if (!targetBlock.equals(findEnclosingBlock(candidate))) {
                continue;
            }
            int candidateOrder = getSourceOrder(candidate);
            if (candidateOrder < targetOrder && candidateOrder > bestOrder) {
                best = candidate;
                bestOrder = candidateOrder;
            }
        }
        return best;
    }

    private static BlockStmt findEnclosingBlock(LineOfCode lineOfCode) {
        return lineOfCode.getStatement().findAncestor(BlockStmt.class).orElse(null);
    }

    private static int getSourceOrder(LineOfCode lineOfCode) {
        Node node = lineOfCode.getStatement();
        return node.getBegin()
                .map(position -> position.line * 10_000 + position.column)
                .orElse(Integer.MAX_VALUE);
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
