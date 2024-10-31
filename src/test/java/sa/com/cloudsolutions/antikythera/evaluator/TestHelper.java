package sa.com.cloudsolutions.antikythera.evaluator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

public class TestHelper {
    protected Evaluator evaluator;
    protected AbstractCompiler compiler;

    protected final PrintStream standardOut = System.out;
    protected final ByteArrayOutputStream outContent = new ByteArrayOutputStream();

    @AfterEach
    public void tearDown() {
        System.setOut(standardOut);
        AntikytheraRunTime.reset();
    }

    @BeforeAll
    public static void setup() throws IOException, ReflectiveOperationException {
        Settings.loadConfigMap();
    }
}
