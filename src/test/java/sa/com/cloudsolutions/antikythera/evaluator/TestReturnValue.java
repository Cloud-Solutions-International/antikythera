package sa.com.cloudsolutions.antikythera.evaluator;

import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
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

class TestReturnValue extends TestHelper {
    Evaluator evaluator;

    @BeforeEach
    public void each() throws AntikytheraException, IOException {
        compiler = new TestReturnValue.ReturnValueEval();
        System.setOut(new PrintStream(outContent));
    }

    @Test
    void testPrintName() throws AntikytheraException, ReflectiveOperationException {
        CompilationUnit cu = compiler.getCompilationUnit();

        MethodDeclaration printName = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("printName")).orElseThrow();
        evaluator.executeMethod(printName);

        assertTrue(outContent.toString().contains("John"));
    }

    @Test
    void testPrintNumberField() throws  AntikytheraException, ReflectiveOperationException {
        CompilationUnit cu = compiler.getCompilationUnit();
        MethodDeclaration printNumber = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("printNumberField")).orElseThrow();
        evaluator.executeMethod(printNumber);
        assertTrue(outContent.toString().contains("10"));
    }

    class ReturnValueEval extends AbstractCompiler {

        protected ReturnValueEval() throws IOException, AntikytheraException {
            cu = getJavaParser().parse(new File("src/test/java/sa/com/cloudsolutions/antikythera/evaluator/ReturnValue.java")).getResult().get();
            evaluator = new Evaluator("");
            evaluator.setupFields(cu);
            evaluator.setScope("returnValue");
        }

    }
}
