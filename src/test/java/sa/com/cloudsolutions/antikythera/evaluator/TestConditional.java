package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestConditional extends TestHelper {


    public static final String SAMPLE_CLASS = "sa.com.cloudsolutions.antikythera.evaluator.Conditional";
    CompilationUnit cu;

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void each() {
        cu = AntikytheraRunTime.getCompilationUnit(SAMPLE_CLASS);
        evaluator = new SpringEvaluator(SAMPLE_CLASS);
        System.setOut(new PrintStream(outContent));
    }

    @Test
    void testExecuteMethod() throws ReflectiveOperationException {
        Person p = new Person("Hello");
        AntikytheraRunTime.push(new Variable(p));

        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("testMethod")).orElseThrow();
        evaluator.executeMethod(method);
        assertEquals("Hello\n", outContent.toString());
    }

    @Test
    void testVisit() throws ReflectiveOperationException {
        ((SpringEvaluator)evaluator).setArgumentGenerator(new DummyArgumentGenerator());

        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("testMethod")).orElseThrow();
        evaluator.visit(method);
        assertEquals("Hello\n", outContent.toString());
    }

}
