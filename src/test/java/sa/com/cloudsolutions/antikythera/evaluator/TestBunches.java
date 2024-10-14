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

public class TestBunches  {
    Evaluator evaluator;
    AbstractCompiler eval;

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
    public void each() throws AntikytheraException, IOException {
        eval = new TestBunches.CollectionEvaluator();
        System.setOut(new PrintStream(outContent));
    }

    @Test
    void testList() throws AntikytheraException, ReflectiveOperationException {
        CompilationUnit cu = eval.getCompilationUnit();
        MethodDeclaration printList = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("printList")).orElseThrow();
        evaluator.executeMethod(printList);
        assertTrue(outContent.toString().contains("[one, two]"));
    }

    @Test
    void testMap() throws AntikytheraException, ReflectiveOperationException {
        CompilationUnit cu = eval.getCompilationUnit();
        MethodDeclaration printMap = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("printMap")).orElseThrow();
        evaluator.executeMethod(printMap);
        assertTrue(outContent.toString().contains("{one=1, two=2}"));
    }

    @Test
    void testWithDTO() throws AntikytheraException, ReflectiveOperationException {
        CompilationUnit cu = eval.getCompilationUnit();
        MethodDeclaration withDTO = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("withDTO")).orElseThrow();
        evaluator.executeMethod(withDTO);
        assertTrue(outContent.toString().contains("[Biggles 10"));
    }


    @Test
    void testDTOConstructor() throws AntikytheraException, ReflectiveOperationException {
        CompilationUnit cu = eval.getCompilationUnit();
        MethodDeclaration withDTO = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("withDTOConstructor")).orElseThrow();
        evaluator.executeMethod(withDTO);
        assertTrue(outContent.toString().contains("[Bertie 10"));
    }


    class CollectionEvaluator extends AbstractCompiler {
        protected CollectionEvaluator() throws IOException, EvaluatorException {
            evaluator = new Evaluator("");
            File file = new File("src/test/java/sa/com/cloudsolutions/antikythera/evaluator/Bunches.java");
            cu = getJavaParser().parse(file).getResult().get();
            evaluator.setupFields(cu);

        }
    }
}
