package sa.com.cloudsolutions.antikythera.depsolver;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InterfaceSolverTest {
    @BeforeAll
    public static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
    }

    @Test
    void testSerialiazble() throws IOException {
        InterfaceSolver solver = new InterfaceSolver();
        solver.compile(AbstractCompiler.classToPath("sa.com.cloudsolutions.antikythera.evaluator.Employee.java"));
        assertEquals(2, AntikytheraRunTime.findImplementations("java.io.Serializable").size());

    }

    @Test
    void testClonable() throws IOException {
        InterfaceSolver solver = new InterfaceSolver();
        solver.compile(AbstractCompiler.classToPath("sa.com.cloudsolutions.antikythera.evaluator.Hello.java"));
        assertEquals(1, AntikytheraRunTime.findImplementations("java.lang.Cloneable").size());

    }
}


