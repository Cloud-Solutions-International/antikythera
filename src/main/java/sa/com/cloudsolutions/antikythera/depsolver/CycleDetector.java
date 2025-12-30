package sa.com.cloudsolutions.antikythera.depsolver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects cycles in a directed dependency graph using Tarjan's SCC algorithm.
 * Time complexity: O(V + E) where V = vertices, E = edges.
 *
 * <p>
 * A Strongly Connected Component (SCC) with more than one node indicates
 * a circular dependency.
 * </p>
 */
public class CycleDetector {

    private final Map<String, Set<String>> adjacencyList;
    private final Map<String, Integer> index = new HashMap<>();
    private final Map<String, Integer> lowlink = new HashMap<>();
    private final Set<String> onStack = new HashSet<>();
    private final Deque<String> stack = new ArrayDeque<>();
    private final List<Set<String>> sccs = new ArrayList<>();
    private int currentIndex = 0;

    /**
     * Create a cycle detector for the given adjacency list.
     *
     * @param adjacencyList Map from node to set of nodes it depends on
     */
    public CycleDetector(Map<String, Set<String>> adjacencyList) {
        this.adjacencyList = adjacencyList;
    }

    /**
     * Find all strongly connected components in the graph.
     *
     * @return List of SCCs, each SCC is a set of node names
     */
    public List<Set<String>> findSCCs() {
        sccs.clear();
        index.clear();
        lowlink.clear();
        onStack.clear();
        stack.clear();
        currentIndex = 0;

        for (String node : adjacencyList.keySet()) {
            if (!index.containsKey(node)) {
                strongConnect(node);
            }
        }
        return new ArrayList<>(sccs);
    }

    /**
     * Find only SCCs with more than one node (actual cycles).
     *
     * @return List of cyclic SCCs
     */
    public List<Set<String>> findCycles() {
        return findSCCs().stream()
                .filter(scc -> scc.size() > 1)
                .toList();
    }

    /**
     * Check if the graph contains any cycles.
     *
     * @return true if at least one cycle exists
     */
    public boolean hasCycles() {
        return !findCycles().isEmpty();
    }

    /**
     * Get the number of cycles (SCCs with >1 node).
     *
     * @return count of cyclic SCCs
     */
    public int getCycleCount() {
        return findCycles().size();
    }

    private void strongConnect(String v) {
        index.put(v, currentIndex);
        lowlink.put(v, currentIndex);
        currentIndex++;
        stack.push(v);
        onStack.add(v);

        Set<String> successors = adjacencyList.getOrDefault(v, Set.of());
        for (String w : successors) {
            if (!index.containsKey(w)) {
                // Successor w has not yet been visited; recurse
                strongConnect(w);
                lowlink.put(v, Math.min(lowlink.get(v), lowlink.get(w)));
            } else if (onStack.contains(w)) {
                // Successor w is on the stack and hence in the current SCC
                lowlink.put(v, Math.min(lowlink.get(v), index.get(w)));
            }
        }

        // If v is a root node, pop the stack and generate an SCC
        if (lowlink.get(v).equals(index.get(v))) {
            Set<String> scc = new HashSet<>();
            String w;
            do {
                w = stack.pop();
                onStack.remove(w);
                scc.add(w);
            } while (!w.equals(v));
            sccs.add(scc);
        }
    }
}
