package sa.com.cloudsolutions.antikythera.evaluator;

import com.cloud.api.configurations.Settings;
import com.cloud.api.generator.AbstractCompiler;
import com.cloud.api.generator.EvaluatorException;
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

public class TestBunches  {
    Evaluator evaluator;
    TestBunches.CollectionEvaluator eval;

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
        eval = new TestBunches.CollectionEvaluator();
        System.setOut(new PrintStream(outContent));
    }

    @Test
    void testList() throws EvaluatorException {
        CompilationUnit cu = eval.getCompilationUnit();
        MethodDeclaration printList = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("printList")).orElseThrow();
        evaluator.executeMethod(printList);
        assertTrue(outContent.toString().contains("[one, two]"));
    }

    @Test
    void testMap() throws EvaluatorException {
        CompilationUnit cu = eval.getCompilationUnit();
        MethodDeclaration printMap = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("printMap")).orElseThrow();
        evaluator.executeMethod(printMap);
        assertTrue(outContent.toString().contains("{one=1, two=2}"));
    }

    class CollectionEvaluator extends AbstractCompiler {
        protected CollectionEvaluator() throws IOException, EvaluatorException {
            evaluator = new Evaluator();
            File file = new File("src/test/java/sa/com/cloudsolutions/antikythera/evaluator/Bunches.java");
            cu = javaParser.parse(file).getResult().get();
            evaluator.setupFields(cu);

        }

        CompilationUnit getCompilationUnit() {
            return cu;
        }
    }
}
