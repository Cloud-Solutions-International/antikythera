package sa.com.cloudsolutions.antikythera.depsolver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Johnson's algorithm to find all elementary cycles in a directed graph.
 * Time complexity: O((c+1)(n+e)) where c = number of cycles, n = nodes, e =
 * edges.
 *
 * <p>
 * This algorithm enumerates all distinct cycles, providing the exact path
 * for each cycle. This is more detailed than Tarjan's SCC which only identifies
 * that cycles exist within a component.
 * </p>
 */
public class JohnsonCycleFinder {

    private final Map<String, Set<String>> adjacencyList;
    private final Set<String> blocked = new HashSet<>();
    private final Map<String, Set<String>> blockedMap = new HashMap<>();
    private final Deque<String> stack = new ArrayDeque<>();
    private final List<List<String>> cycles = new ArrayList<>();
    private String startNode;

    /**
     * Create a cycle finder for the given adjacency list.
     *
     * @param adjacencyList Map from node to set of nodes it depends on
     */
    public JohnsonCycleFinder(Map<String, Set<String>> adjacencyList) {
        this.adjacencyList = adjacencyList;
    }

    /**
     * Find all elementary cycles in the graph.
     *
     * @return List of cycles, each cycle is a list of nodes in traversal order
     */
    public List<List<String>> findAllCycles() {
        cycles.clear();

        // First find all SCCs (cycles only exist within SCCs)
        CycleDetector detector = new CycleDetector(adjacencyList);
        List<Set<String>> sccs = detector.findCycles();

        for (Set<String> scc : sccs) {
            // Build subgraph for this SCC
            Map<String, Set<String>> subgraph = buildSubgraph(scc);

            // Find cycles in subgraph
            List<String> nodes = new ArrayList<>(scc);
            for (int i = 0; i < nodes.size(); i++) {
                startNode = nodes.get(i);
                blocked.clear();
                blockedMap.clear();

                findCyclesFrom(startNode, subgraph);

                // Remove startNode from subgraph for next iteration
                subgraph.remove(startNode);
                for (Set<String> neighbors : subgraph.values()) {
                    neighbors.remove(startNode);
                }
            }
        }

        return new ArrayList<>(cycles);
    }

    /**
     * Find cycles starting from a specific node within an SCC.
     *
     * @param scc The strongly connected component containing potential cycles
     * @return List of cycles found within the SCC
     */
    public List<List<String>> findCyclesInSCC(Set<String> scc) {
        cycles.clear();
        Map<String, Set<String>> subgraph = buildSubgraph(scc);

        List<String> nodes = new ArrayList<>(scc);
        for (int i = 0; i < nodes.size(); i++) {
            startNode = nodes.get(i);
            blocked.clear();
            blockedMap.clear();

            findCyclesFrom(startNode, subgraph);

            subgraph.remove(startNode);
            for (Set<String> neighbors : subgraph.values()) {
                neighbors.remove(startNode);
            }
        }

        return new ArrayList<>(cycles);
    }

    private boolean findCyclesFrom(String node, Map<String, Set<String>> graph) {
        boolean foundCycle = false;
        stack.push(node);
        blocked.add(node);

        Set<String> neighbors = graph.getOrDefault(node, Set.of());
        for (String neighbor : neighbors) {
            if (neighbor.equals(startNode)) {
                // Found a cycle - record it
                List<String> cycle = new ArrayList<>(stack);
                Collections.reverse(cycle);
                cycles.add(cycle);
                foundCycle = true;
            } else if (!blocked.contains(neighbor)) {
                if (findCyclesFrom(neighbor, graph)) {
                    foundCycle = true;
                }
            }
        }

        if (foundCycle) {
            unblock(node);
        } else {
            for (String neighbor : neighbors) {
                blockedMap.computeIfAbsent(neighbor, k -> new HashSet<>()).add(node);
            }
        }

        stack.pop();
        return foundCycle;
    }

    private void unblock(String node) {
        blocked.remove(node);
        Set<String> blockedBy = blockedMap.remove(node);
        if (blockedBy != null) {
            for (String b : blockedBy) {
                if (blocked.contains(b)) {
                    unblock(b);
                }
            }
        }
    }

    private Map<String, Set<String>> buildSubgraph(Set<String> nodes) {
        Map<String, Set<String>> subgraph = new HashMap<>();
        for (String node : nodes) {
            Set<String> filtered = new HashSet<>();
            for (String neighbor : adjacencyList.getOrDefault(node, Set.of())) {
                if (nodes.contains(neighbor)) {
                    filtered.add(neighbor);
                }
            }
            if (!filtered.isEmpty()) {
                subgraph.put(node, filtered);
            }
        }
        return subgraph;
    }
}
