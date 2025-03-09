package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
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
import static org.junit.jupiter.api.Assertions.assertNull;

class TestFunctional extends TestHelper{

    public static final String SAMPLE_CLASS = "sa.com.cloudsolutions.antikythera.evaluator.Functional";
    CompilationUnit cu;

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void each() {
        cu = AntikytheraRunTime.getCompilationUnit(SAMPLE_CLASS);
        evaluator = new Evaluator(SAMPLE_CLASS);
        evaluator.setupFields(cu);
        System.setOut(new PrintStream(outContent));
    }

    @ParameterizedTest
    @CsvSource({"greet1, Hello Ashfaloth", "greet2, Hello Ashfaloth", "greet3, Hello Thorin Oakenshield"})
    void testBiFunction(String name, String value) throws ReflectiveOperationException {
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals(name)).orElseThrow();
        Variable v = evaluator.executeMethod(method);
        assertNull(v.getValue());
        assertEquals(value + "\n", outContent.toString());

    }

    @Test
    void testAscending() throws ReflectiveOperationException {
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("sorting1")).orElseThrow();
        Variable v = evaluator.executeMethod(method);
        assertNull(v.getValue());
        assertEquals("0123456789\n", outContent.toString());
    }

    @Test
    void testDescending() throws ReflectiveOperationException {
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("sorting2")).orElseThrow();
        Variable v = evaluator.executeMethod(method);
        assertNull(v.getValue());
        assertEquals("9876543210\n", outContent.toString());
    }
}
