package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestEnums extends TestHelper {
    static final String SAMPLE_CLASS = "sa.com.cloudsolutions.antikythera.evaluator.Status";
    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void each()  {
        System.setOut(new PrintStream(outContent));
    }

    @Test
    void testMain() throws ReflectiveOperationException {
        evaluator = EvaluatorFactory.create(SAMPLE_CLASS, Evaluator.class);
        MethodDeclaration md = evaluator.getCompilationUnit().findFirst(
                MethodDeclaration.class, m -> m.getNameAsString().equals("main")).orElseThrow();
        Variable v = new Variable(new String[]{});
        AntikytheraRunTime.push(v);
        evaluator.executeMethod(md);
        assertEquals("OPEN!", outContent.toString().trim());
    }

    @Test
    void printStatus() throws ReflectiveOperationException {
        evaluator = EvaluatorFactory.create(SAMPLE_CLASS, Evaluator.class);
        MethodDeclaration md = evaluator.getCompilationUnit().findFirst(
                MethodDeclaration.class, m -> m.getNameAsString().equals("printStatus")).orElseThrow();
        evaluator.executeMethod(md);
        assertEquals("OPEN", outContent.toString().trim());
    }

    @ParameterizedTest
    @CsvSource({"cmp1, OPEN!CLOSED!", "cmp2, OPEN!CLOSED!"})
    void cmp(String name, String value) throws ReflectiveOperationException {
        evaluator = EvaluatorFactory.create(SAMPLE_CLASS, SpringEvaluator.class);
        ((SpringEvaluator)evaluator).setArgumentGenerator(new DummyArgumentGenerator());

        MethodDeclaration method = evaluator.getCompilationUnit().findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals(name)).orElseThrow();
        evaluator.visit(method);
        String s = outContent.toString();
        assertEquals(value,s);
    }
}
