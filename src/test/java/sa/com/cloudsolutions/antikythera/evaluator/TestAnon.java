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

class TestAnon extends TestHelper{

    public static final String CLASS_UNDER_TEST = "sa.com.cloudsolutions.antikythera.evaluator.Anon";

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void each() {
        evaluator = new Evaluator(CLASS_UNDER_TEST);
        System.setOut(new PrintStream(outContent));
    }

    @Test
    void testAnonPerson() throws AntikytheraException, ReflectiveOperationException {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(CLASS_UNDER_TEST);
        MethodDeclaration ts = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("anonPerson")).orElseThrow();
        evaluator.executeMethod(ts);
        assertEquals("Bush\n", outContent.toString() );
    }
}
