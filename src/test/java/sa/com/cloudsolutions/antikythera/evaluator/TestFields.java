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
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestFields extends TestHelper {

    public static final String SAMPLE_CLASS = "sa.com.cloudsolutions.antikythera.evaluator.Employee";
    CompilationUnit cu;

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void each()  {
        cu = AntikytheraRunTime.getCompilationUnit(SAMPLE_CLASS);
        evaluator = new Evaluator(SAMPLE_CLASS);
        System.setOut(new PrintStream(outContent));
    }

    @Test
    void testPrintNumberField() throws  AntikytheraException, ReflectiveOperationException {
        MethodDeclaration ts = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("toString")).orElseThrow();
        Variable v = evaluator.executeMethod(ts);
        assertTrue(v.getValue().toString().contains("Hornblower"));
    }

    @Test
    void testAccessor() throws  AntikytheraException, ReflectiveOperationException {
        MethodDeclaration ts = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("simpleAccess")).orElseThrow();
        evaluator.executeMethod(ts);
        assertEquals("Hornblower\nnull\nColombo\n", outContent.toString() );
    }

    @Test
    void testPublic() throws  AntikytheraException, ReflectiveOperationException {
        MethodDeclaration ts = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("publicAccess")).orElseThrow();
        evaluator.executeMethod(ts);
        assertEquals("Hornblower\n", outContent.toString() );
    }

    @Test
    void testChains() throws AntikytheraException, ReflectiveOperationException {
        MethodDeclaration ts = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("chained")).orElseThrow();
        Variable v = evaluator.executeMethod(ts);
        assertNull(v.getValue());
        assertEquals("false\n", outContent.toString() );
    }
}
