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

public class TestReturnValue {
    Evaluator evaluator;
    TestReturnValue.ReturnValueEval eval;

    private final PrintStream standardOut = System.out;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();

    @AfterEach
    public void tearDown() {
        System.setOut(standardOut);
        AntikytheraRunTime.reset();
    }

    @BeforeAll
    public static void setup() throws IOException {
        Settings.loadConfigMap();
    }

    @BeforeEach
    public void each() throws EvaluatorException, IOException {
        eval = new TestReturnValue.ReturnValueEval();
        System.setOut(new PrintStream(outContent));
    }
    @Test
    void testPrintName() throws EvaluatorException {
        CompilationUnit cu = eval.getComplationUnit();

        MethodDeclaration printName = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("printName")).orElseThrow();
        evaluator.executeMethod(printName);

        assertTrue(outContent.toString().contains("John"));
    }

    @Test
    void testPrintNumberField() throws  EvaluatorException {
        CompilationUnit cu = eval.getComplationUnit();
        MethodDeclaration printNumber = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("printNumberField")).orElseThrow();
        evaluator.executeMethod(printNumber);
        assertTrue(outContent.toString().contains("10"));
    }

    class ReturnValueEval extends AbstractCompiler {

        protected ReturnValueEval() throws IOException, EvaluatorException {
            cu = javaParser.parse(new File("src/test/java/sa/com/cloudsolutions/antikythera/evaluator/ReturnValue.java")).getResult().get();
            evaluator = new Evaluator();
            evaluator.setupFields(cu);
            evaluator.setScope("returnValue");
        }

        public CompilationUnit getComplationUnit() {
            return cu;
        }
    }
}
