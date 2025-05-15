package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;


import static org.junit.jupiter.api.Assertions.assertEquals;

class TestLocals extends TestHelper {
    private static final String SAMPLE_CLASS = "sa.com.cloudsolutions.antikythera.evaluator.Locals";
    CompilationUnit cu;

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void each() throws AntikytheraException {
        cu = AntikytheraRunTime.getCompilationUnit(SAMPLE_CLASS);
        evaluator = EvaluatorFactory.create(SAMPLE_CLASS, Evaluator.class);
        System.setOut(new PrintStream(outContent));
    }

    @Test
    void testScope() throws AntikytheraException, ReflectiveOperationException {
        MethodDeclaration doStuff = cu
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("doStuff")).orElseThrow();
        evaluator.executeMethod(doStuff);
        assertEquals("10,20,100\n20,30,200\n", outContent.toString());
    }


    @ParameterizedTest
    @CsvSource({"mce, []", "arrayAccess, HELLOWORLD9.1", "people, Bertie and Biggles"})
    void testVarious(String name, String output) throws ReflectiveOperationException {
        MethodDeclaration mce = cu
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals(name)).orElseThrow();
        evaluator.executeMethod(mce);
        assertEquals(output, outContent.toString().strip());
    }
}
