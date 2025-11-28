package sa.com.cloudsolutions.antikythera.depsolver;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterfaceSolverTest {
    @BeforeAll
    static void setup() throws IOException {
        DepSolver.reset();
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.preProcess();
    }

    @Test
    void testSerialiazble() throws IOException {
        InterfaceSolver solver = new InterfaceSolver();
        solver.compile(AbstractCompiler.classToPath("sa.com.cloudsolutions.antikythera.testhelper.evaluator.Employee.java"));
        assertEquals(3, AntikytheraRunTime.findImplementations("java.io.Serializable").size());

    }

    @Test
    void testClonable() throws IOException {
        InterfaceSolver solver = new InterfaceSolver();
        solver.compile(AbstractCompiler.classToPath("sa.com.cloudsolutions.antikythera.testhelper.evaluator.Hello.java"));
        assertEquals(1, AntikytheraRunTime.findImplementations("java.lang.Cloneable").size());
    }

    @Test
    void testDeep() throws IOException {
        AbstractCompiler.preProcess();
        Set<String> impl = AntikytheraRunTime.findImplementations("sa.com.cloudsolutions.antikythera.testhelper.evaluator.IPerson");
        assertEquals(2, impl.size());
        assertTrue(impl.contains("sa.com.cloudsolutions.antikythera.testhelper.evaluator.Contact"));
    }

    /**
     * Test that cyclic interface hierarchies don't cause infinite recursion.
     * The fixture has InterfaceA -> InterfaceB -> InterfaceC -> InterfaceA cycle.
     * This test verifies that the visited set prevents infinite recursion.
     */
    @Test
    void testCyclicInterfacesNoInfiniteRecursion() throws IOException {
        // Verify that implementations are registered for all interfaces in the hierarchy
        Set<String> implA = AntikytheraRunTime.findImplementations("sa.com.cloudsolutions.antikythera.depsolver.fixtures.InterfaceA");
        assertTrue(implA.contains("sa.com.cloudsolutions.antikythera.depsolver.fixtures.ConcreteImplementation"),
                "ConcreteImplementation should be registered as implementation of InterfaceA");

        // If we get here without a StackOverflowError or hanging, the recursion guard works
    }
}


