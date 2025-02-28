package sa.com.cloudsolutions.antikythera.parser;

import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.constants.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class RestControllerParserTest {
    private RestControllerParser parser;
    private static String outputPath;

    @BeforeEach
    void setUp() throws IOException {
        Settings.loadConfigMap();
        outputPath = Settings.getProperty(Constants.OUTPUT_PATH).toString();

        String controllers = Settings.getProperty(Constants.CONTROLLERS).toString();
        parser = new RestControllerParser("sa.com.cloudsolutions.controller.ComplexController");
    }

    @Test
    void start_processesRestControllerSuccessfully() throws IOException, EvaluatorException {
        parser.start();

        File srcDirectory = new File(outputPath + "/src/main/java/");
        File testDirectory = new File(outputPath + "/src/test/java/");
        assertTrue(srcDirectory.exists() && srcDirectory.isDirectory());
        assertTrue(testDirectory.exists() && testDirectory.isDirectory());
    }
}
