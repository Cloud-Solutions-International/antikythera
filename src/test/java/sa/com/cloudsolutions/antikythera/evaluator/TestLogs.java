package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class TestLogs extends TestHelper {

    public static final String SAMPLE_CLASS = "sa.com.cloudsolutions.antikythera.evaluator.Noisy";
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
    @CsvSource({"noisyMethod, This is a noisy method!",
                "anotherNoisyMethod, This is another noisy method!"})
    void testNosy(String name, String result) throws ReflectiveOperationException {
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals(name)).orElseThrow();

        /*
         * If the logger is not setup this will throw an exception.
         */
        evaluator.executeMethod(method);
        String[] s = outContent.toString().split("\n");
        assertEquals(2, s.length);
        assertEquals(result, s[1].trim());
        assertTrue(s[0].contains(result));
        assertTrue(s[0].contains(SAMPLE_CLASS));
    }
}
