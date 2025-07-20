package sa.com.cloudsolutions.antikythera.evaluator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestReturnValue extends TestHelper {
    private static final String SAMPLE_CLASS = "sa.com.cloudsolutions.antikythera.evaluator.ReturnValue";
    CompilationUnit cu;

    @BeforeAll
    static void setup() throws IOException {
        AntikytheraRunTime.resetAll();
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void each() throws AntikytheraException {
        cu = AntikytheraRunTime.getCompilationUnit(SAMPLE_CLASS);
        evaluator = EvaluatorFactory.create(SAMPLE_CLASS, Evaluator.class);
        System.setOut(new PrintStream(outContent));
    }

    @Test
    void testPrintName() throws AntikytheraException, ReflectiveOperationException {
        MethodDeclaration printName = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("printName")).orElseThrow();
        evaluator.executeMethod(printName);

        assertTrue(outContent.toString().contains("John"));
    }

    @ParameterizedTest
    @CsvSource(value = {"deepCalls,UPPER2","deepEnums,KARLA3"})
    void testDeepCalls(String name, String value) throws AntikytheraException, ReflectiveOperationException {
        MethodDeclaration printName = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals(name)).orElseThrow();
        evaluator.executeMethod(printName);

        assertTrue(outContent.toString().contains(value));
    }

    @Test
    void testPrintNumberField() throws  AntikytheraException, ReflectiveOperationException {
        MethodDeclaration printNumber = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("printNumberField")).orElseThrow();
        evaluator.executeMethod(printNumber);
        assertTrue(outContent.toString().contains("10"));
    }

    @Test
    void testConditionally() throws  AntikytheraException, ReflectiveOperationException {
        MethodDeclaration printNumber = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("returnConditionally")).orElseThrow();
        evaluator.executeMethod(printNumber);
        assertFalse(outContent.toString().contains("THIS SHOULD NOT BE PRINTED"));
    }


    @Test
    void testDeepReturn() throws  AntikytheraException, ReflectiveOperationException {
        MethodDeclaration printNumber = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("deepReturn")).orElseThrow();
        evaluator.executeMethod(printNumber);
        assertEquals("", outContent.toString());
    }
}
