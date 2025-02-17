package sa.com.cloudsolutions.antikythera.evaluator;

import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test that basic arithmetic works.
 */
class TestArithmetic extends  TestHelper {
    @BeforeAll
    public static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
    }

    @BeforeEach
    public void each() throws IOException {
        compiler = new ArithmeticCompiler();
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    public void tearDown() {
        System.setOut(standardOut);
    }

    class ArithmeticCompiler extends AbstractCompiler {
        protected ArithmeticCompiler() throws IOException {
            compile(AbstractCompiler.classToPath("sa.com.cloudsolutions.antikythera.evaluator.Arithmetic"));

            evaluator = new Evaluator("sa.com.cloudsolutions.antikythera.evaluator.Arithmetic");
            evaluator.setupFields();
        }
    }


    @Test
    void testAssignments() throws Exception {
        MethodDeclaration doStuff = compiler.getCompilationUnit()
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("assignments")).orElseThrow();

        Arithmetic arithmetic = new Arithmetic();
        AntikytheraRunTime.push(new Variable(arithmetic));

        evaluator.executeMethod(doStuff);

        String output = outContent.toString();
        assertEquals("110\n", output);
    }

    @Test
    void testPrints20() throws Exception {
        MethodDeclaration doStuff = compiler.getCompilationUnit()
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("doStuff")).orElseThrow();

        Arithmetic arithmetic = new Arithmetic();
        AntikytheraRunTime.push(new Variable(arithmetic));

        evaluator.executeMethod(doStuff);

        String output = outContent.toString();
        assertTrue(output.contains("20"));
    }


    @Test
    void testSimpleAddition() throws Exception {
        MethodDeclaration doStuff = compiler.getCompilationUnit()
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("simpleAddition")).orElseThrow();

        Arithmetic arithmetic = new Arithmetic();
        AntikytheraRunTime.push(new Variable(arithmetic));

        evaluator.executeMethod(doStuff);

        String output = outContent.toString();
        assertTrue(output.contains("30"));
    }


    @Test
    void testAdditionViaStrings() throws Exception {
        MethodDeclaration doStuff = compiler.getCompilationUnit()
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("additionViaStrings")).orElseThrow();

        Arithmetic arithmetic = new Arithmetic();
        AntikytheraRunTime.push(new Variable(arithmetic));

        evaluator.executeMethod(doStuff);

        String output = outContent.toString();
        assertTrue(output.contains("30"));
    }
}
