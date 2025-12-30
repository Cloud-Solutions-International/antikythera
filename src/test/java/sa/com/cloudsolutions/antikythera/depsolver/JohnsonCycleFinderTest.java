package sa.com.cloudsolutions.antikythera.depsolver;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JohnsonCycleFinder - enumerating all elementary cycles.
 */
class JohnsonCycleFinderTest {

    @Test
    void findSimpleCycle() {
        // A → B → A
        Map<String, Set<String>> graph = new HashMap<>();
        graph.put("A", Set.of("B"));
        graph.put("B", Set.of("A"));

        JohnsonCycleFinder finder = new JohnsonCycleFinder(graph);
        List<List<String>> cycles = finder.findAllCycles();

        assertEquals(1, cycles.size());
        // Cycle should be [A, B] or [B, A]
        assertEquals(2, cycles.get(0).size());
    }

    @Test
    void findTransitiveCycle() {
        // A → B → C → A
        Map<String, Set<String>> graph = new HashMap<>();
        graph.put("A", Set.of("B"));
        graph.put("B", Set.of("C"));
        graph.put("C", Set.of("A"));

        JohnsonCycleFinder finder = new JohnsonCycleFinder(graph);
        List<List<String>> cycles = finder.findAllCycles();

        assertEquals(1, cycles.size());
        assertEquals(3, cycles.get(0).size());
    }

    @Test
    void findMultipleCyclesInScc() {
        // A ↔ B ↔ C ↔ A (triangle with all bidirectional)
        // This creates 3 cycles: A→B→A, B→C→B, C→A→C, and A→B→C→A, etc.
        Map<String, Set<String>> graph = new HashMap<>();
        graph.put("A", Set.of("B", "C"));
        graph.put("B", Set.of("A", "C"));
        graph.put("C", Set.of("A", "B"));

        JohnsonCycleFinder finder = new JohnsonCycleFinder(graph);
        List<List<String>> cycles = finder.findAllCycles();

        // Should find multiple cycles
        assertTrue(cycles.size() > 1);
    }

    @Test
    void noCyclesInDag() {
        // A → B → C (no cycle)
        Map<String, Set<String>> graph = new HashMap<>();
        graph.put("A", Set.of("B"));
        graph.put("B", Set.of("C"));
        graph.put("C", Set.of());

        JohnsonCycleFinder finder = new JohnsonCycleFinder(graph);
        List<List<String>> cycles = finder.findAllCycles();

        assertTrue(cycles.isEmpty());
    }

    @Test
    void cyclePathOrder() {
        // A → B → C → A
        // Verify cycle path is in traversal order
        Map<String, Set<String>> graph = new HashMap<>();
        graph.put("A", Set.of("B"));
        graph.put("B", Set.of("C"));
        graph.put("C", Set.of("A"));

        JohnsonCycleFinder finder = new JohnsonCycleFinder(graph);
        List<List<String>> cycles = finder.findAllCycles();

        assertEquals(1, cycles.size());
        List<String> cycle = cycles.get(0);

        // Verify consecutive elements are connected
        for (int i = 0; i < cycle.size(); i++) {
            String from = cycle.get(i);
            String to = cycle.get((i + 1) % cycle.size());
            assertTrue(graph.get(from).contains(to),
                    "Edge " + from + " → " + to + " should exist");
        }
    }
}
