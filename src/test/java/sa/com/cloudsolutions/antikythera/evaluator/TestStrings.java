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

public class TestStrings {
    Evaluator evaluator;
    HelloEvaluator eval;

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
        eval = new HelloEvaluator();
        System.setOut(new PrintStream(outContent));
    }

    @Test
    void testUpperCase() throws EvaluatorException {
        Variable u = new Variable("upper cased");
        AntikytheraRunTime.push(u);
        MethodDeclaration helloUpper = eval.getCompilationUnit()
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("helloUpper")).orElseThrow();
        evaluator.setScope("helloUpper");
        evaluator.executeMethod(helloUpper);

        assertTrue(outContent.toString().contains("Hello, UPPER CASED"));
    }

    @Test
    void testHello() throws EvaluatorException {
        MethodDeclaration helloWorld = eval.getCompilationUnit()
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("helloWorld")).orElseThrow();
        evaluator.setScope("helloWorld");
        evaluator.executeMethod(helloWorld);

        assertTrue(outContent.toString().contains("Hello, Antikythera"));
    }

    @Test
    void testHelloArgs() throws EvaluatorException {
        MethodDeclaration helloName = eval.getCompilationUnit()
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("helloName")).orElseThrow();
        evaluator.setScope("helloName");
        Variable v = new Variable("Cloud Solutions");
        AntikytheraRunTime.push(v);
        evaluator.executeMethod(helloName);
        assertTrue(outContent.toString().contains("Hello, Cloud Solutions"));
    }

    @Test
    void testChained() throws EvaluatorException {
        Variable v = new Variable("World");
        AntikytheraRunTime.push(v);
        MethodDeclaration helloChained = eval.getCompilationUnit()
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("helloChained")).orElseThrow();
        evaluator.executeMethod(helloChained);

        assertTrue(outContent.toString().contains("Hello, ORLD"));
    }

    class HelloEvaluator extends AbstractCompiler {

        protected HelloEvaluator() throws IOException, EvaluatorException {
            File file = new File("src/test/java/sa/com/cloudsolutions/antikythera/evaluator/Hello.java");
            cu = javaParser.parse(file).getResult().get();
            evaluator = new Evaluator();
            evaluator.setupFields(cu);
        }

        CompilationUnit getCompilationUnit() {
            return cu;
        }
    }
}
