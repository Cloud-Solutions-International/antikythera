package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;


import static org.junit.jupiter.api.Assertions.assertEquals;

class TestLocals extends TestHelper {
    private final String SAMPLE_CLASS = "sa.com.cloudsolutions.antikythera.evaluator.Locals";
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
        evaluator = new Evaluator(SAMPLE_CLASS);

        System.setOut(new PrintStream(outContent));
    }

    @Test
    void testScope() throws AntikytheraException, ReflectiveOperationException {
        MethodDeclaration doStuff = cu
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("doStuff")).orElseThrow();
        evaluator.executeMethod(doStuff);
        assertEquals("10,20,100\n20,30,200\n", outContent.toString());
    }

}
