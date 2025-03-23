package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                md -> md.getNameAsString().equals("conditional1")).orElseThrow();
        evaluator.executeMethod(method);
        assertEquals("Hello", outContent.toString());
    }


    @ParameterizedTest
    @CsvSource({"conditional1, The name is nullT", "conditional2, The name is nullT",
            "conditional3, ZERO!1",
    })
    void testVisit(String name, String value) throws ReflectiveOperationException {
        ((SpringEvaluator)evaluator).setArgumentGenerator(new DummyArgumentGenerator());

        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals(name)).orElseThrow();
        evaluator.visit(method);
        assertEquals(value, outContent.toString());
    }

    @ParameterizedTest
    @CsvSource({"conditional4, ZERO!Negative!Positive!", "conditional5, ZERO!One!Two!Three!",
            "conditional6, ZERO!One!Two!Three!","conditional7, ZERO!One!Two!Three!"
    })
    void testConditionals(String name, String value) throws ReflectiveOperationException {
        ((SpringEvaluator)evaluator).setArgumentGenerator(new DummyArgumentGenerator());

        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals(name)).orElseThrow();
        evaluator.visit(method);
        String s = outContent.toString();
        assertEquals(value.length(), s.length());
        String[] parts = value.split("!");
        for (String part : parts) {
            assertTrue(s.contains(part));
        }
    }
}
