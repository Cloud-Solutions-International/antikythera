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
        solver.compile(AbstractCompiler.classToPath("sa.com.cloudsolutions.antikythera.evaluator.Employee.java"));
        assertEquals(3, AntikytheraRunTime.findImplementations("java.io.Serializable").size());

    }

    @Test
    void testClonable() throws IOException {
        InterfaceSolver solver = new InterfaceSolver();
        solver.compile(AbstractCompiler.classToPath("sa.com.cloudsolutions.antikythera.evaluator.Hello.java"));
        assertEquals(1, AntikytheraRunTime.findImplementations("java.lang.Cloneable").size());
    }

    @Test
    void testDeep() throws IOException {
        AbstractCompiler.preProcess();
        Set<String> impl = AntikytheraRunTime.findImplementations("sa.com.cloudsolutions.antikythera.evaluator.IPerson");
        assertEquals(2, impl.size());
        assertTrue(impl.contains("sa.com.cloudsolutions.antikythera.evaluator.Contact"));
    }
}


