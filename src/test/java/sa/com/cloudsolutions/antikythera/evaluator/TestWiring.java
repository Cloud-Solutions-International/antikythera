package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TestWiring extends TestHelper{

    private static final String SAMPLE_CLASS = "sa.com.cloudsolutions.antikythera.evaluator.Wiring";
    CompilationUnit cu;

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void each() throws AntikytheraException {
        cu = AntikytheraRunTime.getCompilationUnit(SAMPLE_CLASS);
        evaluator = EvaluatorFactory.createLazily(SAMPLE_CLASS, SpringEvaluator.class);
        evaluator.setupFields();
        System.setOut(new PrintStream(outContent));
    }

    @Test
    void testInitializer() throws ReflectiveOperationException {
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("doStuff")).orElseThrow();

        Variable v = evaluator.executeMethod(method);
        assertNull(v.getValue());
        assertEquals("Fatty Bolger\n", outContent.toString());
    }
}
