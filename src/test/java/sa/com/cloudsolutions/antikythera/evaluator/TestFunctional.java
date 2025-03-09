package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.depsolver.ClassProcessor;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TestFunctional extends TestHelper{
    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
    }

    @BeforeEach
    void each() throws Exception {
        compiler = new TestFunctionalCompiler();
        System.setOut(new PrintStream(outContent));
    }

    @ParameterizedTest
    @CsvSource({"greet1, Hello Ashfaloth", "greet2, Hello Ashfaloth", "greet3, Hello Thorin Oakenshield"})
    void testBiFunction(String name, String value) throws ReflectiveOperationException {
        CompilationUnit cu = compiler.getCompilationUnit();
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals(name)).orElseThrow();
        Variable v = evaluator.executeMethod(method);
        assertNull(v.getValue());
        assertEquals(value + "\n", outContent.toString());

    }

    @Test
    void testAscending() throws ReflectiveOperationException {
        CompilationUnit cu = compiler.getCompilationUnit();
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("sorting1")).orElseThrow();
        Variable v = evaluator.executeMethod(method);
        assertNull(v.getValue());
        assertEquals("0123456789\n", outContent.toString());
    }

    @Test
    void testDescending() throws ReflectiveOperationException {
        CompilationUnit cu = compiler.getCompilationUnit();
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("sorting2")).orElseThrow();
        Variable v = evaluator.executeMethod(method);
        assertNull(v.getValue());
        assertEquals("9876543210\n", outContent.toString());
    }

    class TestFunctionalCompiler extends ClassProcessor {
        protected TestFunctionalCompiler() throws IOException, AntikytheraException {
            parse(classToPath("sa.com.cloudsolutions.antikythera.evaluator.Functional.java"));
            compileDependencies();
            evaluator = new Evaluator("sa.com.cloudsolutions.antikythera.evaluator.Functional");
            evaluator.setupFields(cu);
        }
    }
}
