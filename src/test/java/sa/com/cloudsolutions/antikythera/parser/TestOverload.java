package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.EvaluatorFactory;
import sa.com.cloudsolutions.antikythera.evaluator.TestHelper;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestOverlord extends TestHelper {
    private static final String CLASS_NAME = "sa.com.cloudsolutions.antikythera.evaluator.Overlord";
    private CompilationUnit compilationUnit;
    @BeforeAll
    static void setUp() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();

    }

    @BeforeEach
    void each() {
        compilationUnit = AntikytheraRunTime.getCompilationUnit(CLASS_NAME);
        evaluator = EvaluatorFactory.createLazily(CLASS_NAME, Evaluator.class);
        System.setOut(new PrintStream(outContent));
    }

    /**
     * Test finding the method declaration when type arguments are not present.
     */
    @Test
    void testFindMethodDeclaration() {
        List<MethodDeclaration> mds = compilationUnit.findAll(MethodDeclaration.class);
        assertEquals(9, mds.size());

        List<MethodCallExpr> methodCalls = compilationUnit.findAll(MethodCallExpr.class);

        /*
         * Should be able to locate 10 method calls in the class, finding the corresponding
         * methodDeclaration is another matter
         */
        assertEquals(17, methodCalls.size());
    }

    /**
     * Test finding the method declaration when type arguments are present.
     */
    @ParameterizedTest
    @CsvSource({"p1, String input: a", "p2, Int input: 1",
            "p3, First int: 1 Second int: 2", "p4, 1 2 3"})
    void testCalls(String name, String value) throws AntikytheraException, ReflectiveOperationException {
        MethodDeclaration md = compilationUnit.findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals(name)).orElseThrow();

        evaluator.executeMethod(md);
        assertEquals(outContent.toString().strip(), value);
    }
}
