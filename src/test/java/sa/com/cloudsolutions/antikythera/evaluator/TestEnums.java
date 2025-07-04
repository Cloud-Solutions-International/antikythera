package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestEnums extends TestHelper {
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
        assertEquals("OPEN", outContent.toString().trim());
    }

}
