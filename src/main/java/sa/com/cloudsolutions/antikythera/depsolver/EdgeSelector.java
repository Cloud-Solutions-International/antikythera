package sa.com.cloudsolutions.antikythera.depsolver;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Selects optimal edges to cut to break all cycles using a weighted greedy
 * algorithm.
 *
 * <p>
 * Since the Minimum Feedback Arc Set (MFAS) problem is NP-hard, we use a
 * greedy heuristic that prioritizes cutting edges with:
 * </p>
 * <ul>
 * <li>Low injection type weight (prefer FIELD over CONSTRUCTOR)</li>
 * <li>Low method count (easier interface extraction)</li>
 * <li>Low target in-degree (avoid cutting edges to hub beans)</li>
 * </ul>
 */
public class EdgeSelector {

    private final Map<String, Set<BeanDependency>> dependencies;
    private final Map<String, Integer> inDegree;

    /**
     * Create an edge selector for the given dependency graph.
     *
     * @param dependencies Map from bean FQN to set of its dependencies
     */
    public EdgeSelector(Map<String, Set<BeanDependency>> dependencies) {
        this.dependencies = dependencies;
        this.inDegree = computeInDegree();
    }

    /**
     * Select minimum-weight edges to break all cycles.
     *
     * <p>
     * Uses a greedy approach: repeatedly select the edge that breaks
     * the most remaining cycles with the lowest weight.
     * </p>
     *
     * @param cycles List of cycles (each cycle is a list of bean FQNs in order)
     * @return Set of edges to cut
     */
    public Set<BeanDependency> selectEdgesToCut(List<List<String>> cycles) {
        Set<BeanDependency> edgesToCut = new HashSet<>();
        Set<List<String>> remainingCycles = new HashSet<>(cycles);

        while (!remainingCycles.isEmpty()) {
            // Count how many remaining cycles each edge appears in
            Map<BeanDependency, Integer> edgeCounts = new HashMap<>();
            for (List<String> cycle : remainingCycles) {
                for (int i = 0; i < cycle.size(); i++) {
                    String from = cycle.get(i);
                    String to = cycle.get((i + 1) % cycle.size());

                    findEdge(from, to).ifPresent(edge -> edgeCounts.merge(edge, 1, Integer::sum));
                }
            }

            if (edgeCounts.isEmpty()) {
                break; // No more edges to cut
            }

            // Select edge with best score: high cycle coverage / low weight
            BeanDependency bestEdge = edgeCounts.keySet().stream()
                    .max(Comparator.comparingDouble(edge -> edgeCounts.get(edge) / computeWeight(edge)))
                    .orElseThrow();

            edgesToCut.add(bestEdge);

            // Remove cycles broken by this edge
            String from = bestEdge.fromBean();
            String to = bestEdge.targetBean();
            remainingCycles.removeIf(cycle -> containsEdge(cycle, from, to));
        }

        return edgesToCut;
    }

    /**
     * Compute weight for an edge. Lower weight = prefer to cut.
     */
    public double computeWeight(BeanDependency edge) {
        double weight = switch (edge.injectionType()) {
            case FIELD -> 1.0;
            case SETTER -> 2.0;
            case CONSTRUCTOR -> 3.0;
            case BEAN_METHOD -> 4.0;
        };

        // 2. In-degree: don't cut edges to hub beans (many dependents)
        int targetInDegree = inDegree.getOrDefault(edge.targetBean(), 0);
        return weight + targetInDegree * 0.5;
    }

    /**
     * Find the dependency edge from one bean to another.
     */
    public Optional<BeanDependency> findEdge(String from, String to) {
        Set<BeanDependency> deps = dependencies.get(from);
        if (deps == null) {
            return Optional.empty();
        }
        return deps.stream()
                .filter(d -> d.targetBean().equals(to))
                .findFirst();
    }

    /**
     * Compute in-degree (number of incoming edges) for each bean.
     * High in-degree = hub bean = should be preserved.
     */
    private Map<String, Integer> computeInDegree() {
        Map<String, Integer> degrees = new HashMap<>();

        for (Set<BeanDependency> deps : dependencies.values()) {
            for (BeanDependency dep : deps) {
                degrees.merge(dep.targetBean(), 1, Integer::sum);
            }
        }

        return degrees;
    }

    private boolean containsEdge(List<String> cycle, String from, String to) {
        for (int i = 0; i < cycle.size(); i++) {
            if (cycle.get(i).equals(from) && cycle.get((i + 1) % cycle.size()).equals(to)) {
                return true;
            }
        }
        return false;
    }
}
