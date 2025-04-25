package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.generator.UnitTestGenerator;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestQueries extends TestHelper{
    private final String SAMPLE_CLASS = "sa.com.cloudsolutions.antikythera.evaluator.Queries";
    CompilationUnit cu;

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void each() throws AntikytheraException {
        cu = AntikytheraRunTime.getCompilationUnit(SAMPLE_CLASS);
        UnitTestGenerator gen = new UnitTestGenerator(cu);
        gen.mockFields();
        evaluator = EvaluatorFactory.create(SAMPLE_CLASS, SpringEvaluator.class);
        System.setOut(new PrintStream(outContent));
    }

    @Test
    void testScope() throws AntikytheraException, ReflectiveOperationException {
        ((SpringEvaluator)evaluator).setArgumentGenerator(new DummyArgumentGenerator());

        MethodDeclaration doStuff = cu
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("getById")).orElseThrow();
        evaluator.visit(doStuff);
        assertEquals("bada", outContent.toString());
    }
}
