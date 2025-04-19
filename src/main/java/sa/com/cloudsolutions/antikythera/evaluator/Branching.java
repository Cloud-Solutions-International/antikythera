package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Branching {
    private static final List<LineOfCode> heap = new ArrayList<>();
    private static final Map<Integer, LineOfCode> hashes = new HashMap<>();

    private static int size = 0;

    private Branching() {
    }

    public static void clear() {
        heap.clear();
        size = 0;
    }

    public static void add(LineOfCode lineOfCode) {
        hashes.put(lineOfCode.getStatement().hashCode(), lineOfCode);
        if (size >= heap.size()) {
            heap.add(lineOfCode);
        } else {
            heap.set(size, lineOfCode);
        }
        bubbleUp(size++);
    }

    public static LineOfCode getNextUnvisited() {
        if (size == 0) return null;

        // Root will always have the lowest path state
        LineOfCode root = heap.get(0);
        return root.isUntravelled() ? root : null;
    }

    private static void bubbleUp(int index) {
        while (index > 0) {
            int parentIdx = (index - 1) / 2;
            if (compare(heap.get(index), heap.get(parentIdx)) >= 0) {
                break;
            }
            swap(index, parentIdx);
            index = parentIdx;
        }
    }

    public static void updateNode(LineOfCode node) {
        int index = heap.indexOf(node);
        if (index != -1) {
            bubbleDown(index);
            bubbleUp(index);
        }
    }

    private static void bubbleDown(int index) {
        while (true) {
            int smallest = index;
            int leftChild = 2 * index + 1;
            int rightChild = 2 * index + 2;

            if (leftChild < size && compare(heap.get(leftChild), heap.get(smallest)) < 0) {
                smallest = leftChild;
            }
            if (rightChild < size && compare(heap.get(rightChild), heap.get(smallest)) < 0) {
                smallest = rightChild;
            }

            if (smallest == index) break;
            swap(index, smallest);
            index = smallest;
        }
    }

    private static void swap(int i, int j) {
        LineOfCode temp = heap.get(i);
        heap.set(i, heap.get(j));
        heap.set(j, temp);
        updateRelationships(i);
        updateRelationships(j);
    }

    private static void updateRelationships(int index) {
        LineOfCode node = heap.get(index);
        int leftChild = 2 * index + 1;
        int rightChild = 2 * index + 2;

        if (leftChild < size) {
            heap.get(leftChild).setParent(node);
        }
        if (rightChild < size) {
            heap.get(rightChild).setParent(node);
        }
    }

    private static int compare(LineOfCode a, LineOfCode b) {
        return Integer.compare(a.getPathTaken(), b.getPathTaken());
    }

    public static List<Precondition> getApplicableConditions() {
        List<Precondition> applicableConditions = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            LineOfCode lineOfCode = heap.get(i);
            if (!lineOfCode.isFullyTravelled()) {
                applicableConditions.addAll(lineOfCode.getPreconditions());
            }
        }
        return applicableConditions;
    }

    public static LineOfCode getRoot() {
        return size > 0 ? heap.get(0) : null;
    }

    public static LineOfCode get(Statement statement) {
        return hashes.get(statement.hashCode());
    }

    public static List<Expression> getAllConditions(Statement statement) {
        List<Expression> path = new ArrayList<>();
        LineOfCode current = get(statement);

        if (current != null) {
            if (current.getStatement() instanceof IfStmt ifst) {
                path.add(ifst.getCondition());
                while (current.getParent() != null) {
                    current = current.getParent();
                    if (current.getStatement() instanceof IfStmt ifStmt) {
                        path.add(ifStmt.getCondition());
                    }
                }
            }
        }

        return path;
    }

    public static List<LineOfCode> getAllNodes() {
        return heap.subList(0, size);
    }

    public static List<LineOfCode> getPathToRoot(Statement statement) {
        List<LineOfCode> path = new ArrayList<>();
        LineOfCode current = get(statement);

        if (current != null) {
            path.add(current);
            while (current.getParent() != null) {
                current = current.getParent();
                path.add(current);
            }
        }

        return path;
    }
}
