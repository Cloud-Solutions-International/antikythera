package sa.com.cloudsolutions.antikythera.evaluator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TestInheritance extends TestHelper {

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
        MockingRegistry.reset();

    }

    @BeforeEach
    void each() throws AntikytheraException {
        System.setOut(new PrintStream(outContent));
        evaluator = EvaluatorFactory.create("sa.com.cloudsolutions.antikythera.evaluator.PersonExt", Evaluator.class);
    }

    @Test
    void testFields() {
        assertEquals(6, evaluator.getFields().size());
        assertEquals(0, evaluator.getFields().get("id").getValue());
        assertNull(evaluator.getFields().get("name").getValue());
    }
}
