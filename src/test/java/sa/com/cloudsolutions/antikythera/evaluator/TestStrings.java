package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestStrings extends TestHelper{
    CompilationUnit cu;
    public static final String SAMPLE_CLASS = "sa.com.cloudsolutions.antikythera.testhelper.evaluator.Hello";

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void each()  {
        cu = AntikytheraRunTime.getCompilationUnit(SAMPLE_CLASS);
        evaluator = EvaluatorFactory.create(SAMPLE_CLASS, Evaluator.class);
        System.setOut(new PrintStream(outContent));
    }

    @Test
    void testUpperCase() throws AntikytheraException, ReflectiveOperationException {
        Variable u = new Variable("upper cased");
        AntikytheraRunTime.push(u);
        MethodDeclaration helloUpper = cu
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("helloUpper")).orElseThrow();
        evaluator.executeMethod(helloUpper);

        assertTrue(outContent.toString().contains("Hello, UPPER CASED"));
    }

    @Test
    void testHello() throws AntikytheraException, ReflectiveOperationException {
        MethodDeclaration helloWorld = cu
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("helloWorld")).orElseThrow();
        evaluator.executeMethod(helloWorld);

        assertTrue(outContent.toString().contains("Hello, Antikythera"));
    }

    @ParameterizedTest
    @CsvSource({"longChain, his is a field", "helloEnum1, OPEN", "helloEnum2, CLOSED"})
    void testLongChain(String name, String value) throws AntikytheraException, ReflectiveOperationException {
        MethodDeclaration helloWorld = cu
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals(name)).orElseThrow();
        evaluator.executeMethod(helloWorld);

        assertTrue(outContent.toString().contains(value));
    }

    @Test
    void testHelloArgs() throws AntikytheraException, ReflectiveOperationException {
        MethodDeclaration helloName = cu
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("helloName")).orElseThrow();
        Variable v = new Variable("Cloud Solutions");
        AntikytheraRunTime.push(v);
        evaluator.executeMethod(helloName);
        assertTrue(outContent.toString().contains("Hello, Cloud Solutions"));
    }

    @Test
    void testChained() throws AntikytheraException, ReflectiveOperationException {
        Variable v = new Variable("World");
        AntikytheraRunTime.push(v);
        MethodDeclaration helloChained = cu
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("helloChained")).orElseThrow();
        evaluator.executeMethod(helloChained);

        assertTrue(outContent.toString().contains("Hello, ORLD"));
    }
}
