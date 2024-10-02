package sa.com.cloudsolutions.antikythera.evaluator;

import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;

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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test that basic arithmetic works.
 */
public class TestArithmetic {
    Evaluator evaluator = new Evaluator();
    private final PrintStream standardOut = System.out;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();

    @BeforeAll
    public static void setup() throws IOException {
        Settings.loadConfigMap();
    }

    @BeforeEach
    public void each() {
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    public void tearDown() {
        System.setOut(standardOut);
    }

    class ArithmeticEvaluator  extends AbstractCompiler {
        protected ArithmeticEvaluator() throws IOException {
        }

        void doStuff() throws IOException, EvaluatorException {
            File file = new File("src/test/java/sa/com/cloudsolutions/antikythera/evaluator/Arithmetic.java");
            CompilationUnit cu = javaParser.parse(file).getResult().get();
            evaluator.setupFields(cu);

            MethodDeclaration doStuffMethod = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("doStuff")).orElseThrow();
            evaluator.setScope("arithmetic");

            evaluator.executeMethod(doStuffMethod);
        }
    }

    @Test
    void testPrints20() throws Exception {
        ArithmeticEvaluator arithmeticEvaluator = new ArithmeticEvaluator();
        arithmeticEvaluator.doStuff();

        String output = outContent.toString();
        assertTrue(output.contains("20"));
    }
}
