package sa.com.cloudsolutions.antikythera.depsolver;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CycleDetector using Tarjan's SCC algorithm.
 */
class CycleDetectorTest {

    @Test
    void detectSimpleCycle() {
        // A → B → A
        Map<String, Set<String>> graph = new HashMap<>();
        graph.put("A", Set.of("B"));
        graph.put("B", Set.of("A"));

        CycleDetector detector = new CycleDetector(graph);
        List<Set<String>> cycles = detector.findCycles();

        assertEquals(1, cycles.size());
        assertTrue(cycles.get(0).containsAll(Set.of("A", "B")));
    }

    @Test
    void detectTransitiveCycle() {
        // A → B → C → A
        Map<String, Set<String>> graph = new HashMap<>();
        graph.put("A", Set.of("B"));
        graph.put("B", Set.of("C"));
        graph.put("C", Set.of("A"));

        CycleDetector detector = new CycleDetector(graph);
        List<Set<String>> cycles = detector.findCycles();

        assertEquals(1, cycles.size());
        assertTrue(cycles.get(0).containsAll(Set.of("A", "B", "C")));
    }

    @Test
    void noCyclesInDAG() {
        // A → B → C (no cycle)
        Map<String, Set<String>> graph = new HashMap<>();
        graph.put("A", Set.of("B"));
        graph.put("B", Set.of("C"));
        graph.put("C", Set.of());

        CycleDetector detector = new CycleDetector(graph);
        List<Set<String>> cycles = detector.findCycles();

        assertTrue(cycles.isEmpty());
        assertFalse(detector.hasCycles());
    }

    @Test
    void multipleSeparateCycles() {
        // A ↔ B, C ↔ D (two separate cycles)
        Map<String, Set<String>> graph = new HashMap<>();
        graph.put("A", Set.of("B"));
        graph.put("B", Set.of("A"));
        graph.put("C", Set.of("D"));
        graph.put("D", Set.of("C"));

        CycleDetector detector = new CycleDetector(graph);
        List<Set<String>> cycles = detector.findCycles();

        assertEquals(2, cycles.size());
        assertEquals(2, detector.getCycleCount());
    }

    @Test
    void hubPatternWithMultipleCycles() {
        // Hub → A, Hub → B, Hub → C
        // A → Hub, B → A, C → Hub
        // Creates: Hub ↔ A, A ↔ B (indirectly through Hub), Hub ↔ C
        Map<String, Set<String>> graph = new HashMap<>();
        graph.put("Hub", Set.of("A", "B", "C"));
        graph.put("A", Set.of("Hub", "B"));
        graph.put("B", Set.of("A", "C"));
        graph.put("C", Set.of("Hub"));

        CycleDetector detector = new CycleDetector(graph);
        List<Set<String>> cycles = detector.findCycles();

        // All nodes form one big SCC
        assertEquals(1, cycles.size());
        assertTrue(cycles.get(0).containsAll(Set.of("Hub", "A", "B", "C")));
    }

    @Test
    void emptyGraph() {
        Map<String, Set<String>> graph = new HashMap<>();

        CycleDetector detector = new CycleDetector(graph);
        List<Set<String>> cycles = detector.findCycles();

        assertTrue(cycles.isEmpty());
    }

    @Test
    void selfLoop() {
        // A → A (self loop)
        Map<String, Set<String>> graph = new HashMap<>();
        graph.put("A", Set.of("A"));

        CycleDetector detector = new CycleDetector(graph);
        List<Set<String>> allSccs = detector.findSCCs();

        // Self-loop creates SCC of size 1, but findCycles() filters these out
        // since we consider cycles as >1 node
        List<Set<String>> cycles = detector.findCycles();
        assertTrue(cycles.isEmpty());
    }
}
