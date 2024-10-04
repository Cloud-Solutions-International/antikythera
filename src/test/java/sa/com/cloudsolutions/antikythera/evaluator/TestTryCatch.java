package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestTryCatch extends TestHelper {
    @BeforeEach
    public void each() throws EvaluatorException, IOException {
        eval = new TryCatchEvaluator();
        System.setOut(new PrintStream(outContent));
    }

    @Test
    void testChained() throws EvaluatorException {

        MethodDeclaration doStuff = eval.getCompilationUnit()
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("doStuff")).orElseThrow();
        evaluator.executeMethod(doStuff);

        assertTrue(outContent.toString().contains("Hello, ORLD"));
    }

    class TryCatchEvaluator extends AbstractCompiler {

        protected TryCatchEvaluator() throws IOException, EvaluatorException {
            File file = new File("src/test/java/sa/com/cloudsolutions/antikythera/evaluator/TryCatch.java");
            cu = javaParser.parse(file).getResult().get();
            evaluator = new Evaluator();
            evaluator.setupFields(cu);
        }
    }
}
