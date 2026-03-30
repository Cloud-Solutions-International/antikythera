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
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for stream operation evaluation using the self-contained FunctionalStream source file.
 * Does not depend on the external antikythera-test-helper repository.
 */
class TestFunctionalStream extends TestHelper {

    public static final String SAMPLE_CLASS =
            "sa.com.cloudsolutions.antikythera.evaluator.stream.FunctionalStream";
    CompilationUnit cu;

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-stream-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void each() {
        AntikytheraRunTime.reset();
        cu = AntikytheraRunTime.getCompilationUnit(SAMPLE_CLASS);
        evaluator = EvaluatorFactory.create(SAMPLE_CLASS, Evaluator.class);
        System.setOut(new PrintStream(outContent));
    }

    @ParameterizedTest
    @CsvSource(value = {
            // P1 — intermediate operations
            "streamMap; [a, b]",
            "streamFilter; [A]",
            "streamCount; 2",
            "streamFindFirst; A",
            "streamAnyMatch; true",
            "streamAllMatch; true",
            "streamNoneMatch; true",
            "streamMin; A",
            "streamMax; B",
            "streamReduce; 10",
            "streamReduceWithIdentity; 10",
            "streamLimit; [A]",
            "streamSkip; [B]",
            "streamDistinct; [1, 2, 3]",
            "streamFlatMap; [A, B]",
            "streamSorted; [A, B, C]",
            "streamSortedWithComparator; [C, B, A]",
            // P3 — additional Collectors
            "groupBy; 1",
            "groupByWithCount; 2",
            "partitionByPredicate; A",
            "collectToSet; 2",
            // P4 — primitive specialised streams
            "intStreamRange; 10",
            "mapToIntSum; 30",
            "mapToLongSum; 30",
            "mapToIntBoxed; [1, 2]"
    }, delimiter = ';')
    void testStreamOps(String name, String value) throws ReflectiveOperationException {
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals(name)).orElseThrow();
        evaluator = EvaluatorFactory.create(SAMPLE_CLASS, Evaluator.class);
        Variable v = evaluator.executeMethod(method);
        assertNull(v.getValue());
        assertEquals(value + "\n", outContent.toString());
    }
}
