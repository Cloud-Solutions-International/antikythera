package sa.com.cloudsolutions.antikythera.evaluator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.depsolver.DepSolver;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

public class TestHelper {
    public static final String FAKE_ENTITY = "sa.com.cloudsolutions.antikythera.testhelper.model.FakeEntity";
    public static final String FAKE_REPOSITORY = "sa.com.cloudsolutions.antikythera.testhelper.repository.FakeRepository";

    protected Evaluator evaluator;

    protected final PrintStream standardOut = System.out;
    protected final ByteArrayOutputStream outContent = new ByteArrayOutputStream();

    @AfterEach
    void tearDown() {
        System.setOut(standardOut);
        AntikytheraRunTime.reset();
        AntikytheraRunTime.resetAutowires();
        MockingRegistry.reset();
        DepSolver.getNames().clear();
    }

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator.yml"));
    }
}
