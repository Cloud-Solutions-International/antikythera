package sa.com.cloudsolutions.antikythera.evaluator;

import org.junit.jupiter.api.BeforeAll;
import sa.com.cloudsolutions.antikythera.configuration.Settings;

import java.io.IOException;

public class TestSuiteEvaluatorIT {
    @BeforeAll
    static void setUp() throws IOException {
        Settings.loadConfigMap();
        Settings.setProperty(Settings.BASE_PATH, "src/test/");
    }
}
