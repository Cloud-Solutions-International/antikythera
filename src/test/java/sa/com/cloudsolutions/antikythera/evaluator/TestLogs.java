package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.logging.LogRecorder;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TestLogs extends TestHelper {

    public static final String SAMPLE_CLASS = "sa.com.cloudsolutions.antikythera.testhelper.evaluator.Noisy";
    CompilationUnit cu;

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void each() {
        cu = AntikytheraRunTime.getCompilationUnit(SAMPLE_CLASS);
        evaluator = EvaluatorFactory.create(SAMPLE_CLASS, Evaluator.class);
        System.setOut(new PrintStream(outContent));
    }

    @ParameterizedTest
    @CsvSource({"noisyMethod, This is a noisy method!, INFO",
                "anotherNoisyMethod, This is another noisy method!, WARN"})
    void testNosy(String name, String result, String level) throws ReflectiveOperationException {
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals(name)).orElseThrow();

        /*
         * If the logger is not setup this will throw an exception.
         */
        evaluator.executeMethod(method);
        List<LogRecorder.LogEntry> entries =  LogRecorder.getLogEntries(SAMPLE_CLASS);

        assertEquals(result, outContent.toString().trim());
        assertFalse(entries.isEmpty());
        LogRecorder.LogEntry entry = entries.getLast();
        assertEquals(level, entry.level());
        assertEquals(result, entry.message());

    }
}
