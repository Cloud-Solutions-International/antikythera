package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestRepository extends TestHelper {
    public static final String SAMPLE_CLASS = "sa.com.cloudsolutions.antikythera.testhelper.evaluator.FakeService";
    CompilationUnit cu;

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.preProcess();
        MockingRegistry.reset();
    }

    @BeforeEach
    void each() {
        MockingRegistry.markAsMocked("sa.com.cloudsolutions.antikythera.testhelper.evaluator.FakeRepository");
        cu = AntikytheraRunTime.getCompilationUnit(SAMPLE_CLASS);
        evaluator = EvaluatorFactory.create(SAMPLE_CLASS, SpringEvaluator.class);
        System.setOut(new PrintStream(outContent));
    }

    @ParameterizedTest
    @CsvSource({"searchByName, Found 1 matches!No Matches!", "findById, Found!Not Found!",
            "countItems, No items!Found 1 item!"})
    void testSearchByName(String name, String value) throws ReflectiveOperationException {
        ((SpringEvaluator)evaluator).setArgumentGenerator(new DummyArgumentGenerator());

        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals(name)).orElseThrow();

        evaluator.visit(method);
        assertEquals(value, outContent.toString());
    }
}
