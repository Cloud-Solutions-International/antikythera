package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import com.github.javaparser.ast.body.MethodDeclaration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test that basic arithmetic works.
 */
class TestArithmetic extends  TestHelper {

    public static final String SAMPLE_CLASS = "sa.com.cloudsolutions.antikythera.testhelper.evaluator.Arithmetic";
    CompilationUnit cu;

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
        MockingRegistry.reset();
    }

    @BeforeEach
    void each() throws IOException {
        evaluator = EvaluatorFactory.create(SAMPLE_CLASS, Evaluator.class);
        cu = AntikytheraRunTime.getCompilationUnit(SAMPLE_CLASS);
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    void tearDown() {
        System.setOut(standardOut);
    }

    @Test
    void testAssignments() throws Exception {
        MethodDeclaration doStuff = cu
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("assignments")).orElseThrow();
        evaluator.executeMethod(doStuff);

        String output = outContent.toString();
        assertEquals("110\n", output);
    }

    @Test
    void testPrints20() throws Exception {
        MethodDeclaration doStuff = cu
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("doStuff")).orElseThrow();

        AntikytheraRunTime.push(new Variable(evaluator));

        evaluator.executeMethod(doStuff);

        String output = outContent.toString();
        assertTrue(output.contains("20"));
    }


    @Test
    void testSimpleAddition() throws Exception {
        MethodDeclaration doStuff = cu
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("simpleAddition")).orElseThrow();

        evaluator.executeMethod(doStuff);

        String output = outContent.toString();
        assertTrue(output.contains("30"));
    }


    @Test
    void testAdditionViaStrings() throws Exception {
        MethodDeclaration doStuff = cu
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("additionViaStrings")).orElseThrow();
        evaluator.executeMethod(doStuff);

        String output = outContent.toString();
        assertTrue(output.contains("30"));
    }
}
