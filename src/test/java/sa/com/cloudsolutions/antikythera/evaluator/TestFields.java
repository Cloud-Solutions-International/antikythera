package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.depsolver.ClassProcessor;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestFields extends TestHelper {

    @BeforeAll
    public static void setup() throws IOException, ReflectiveOperationException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    public void each() throws Exception {
        compiler = new TestFieldsCompiler();
        System.setOut(new PrintStream(outContent));
    }

    @Test
    void testPrintNumberField() throws  AntikytheraException, ReflectiveOperationException {
        CompilationUnit cu = compiler.getCompilationUnit();
        MethodDeclaration ts = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("toString")).orElseThrow();
        Variable v = evaluator.executeMethod(ts);
        assertTrue(v.getValue().toString().contains("Hornblower"));
    }

    @Test
    void testAccessor() throws  AntikytheraException, ReflectiveOperationException {
        CompilationUnit cu = compiler.getCompilationUnit();
        MethodDeclaration ts = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("simpleAccess")).orElseThrow();
        evaluator.executeMethod(ts);
        assertEquals("Hornblower\nnull\nColombo\n", outContent.toString() );
    }

    @Test
    void testChains() throws AntikytheraException, ReflectiveOperationException {
        CompilationUnit cu = compiler.getCompilationUnit();
        MethodDeclaration ts = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("chained")).orElseThrow();
        Variable v = evaluator.executeMethod(ts);
        assertNull(v.getValue());
        assertEquals("false\n", outContent.toString() );

    }

    class TestFieldsCompiler extends ClassProcessor {
        protected TestFieldsCompiler() throws IOException, AntikytheraException {
            parse(classToPath("sa.com.cloudsolutions.antikythera.evaluator.Employee.java"));
            compileDependencies();
            evaluator = new Evaluator("sa.com.cloudsolutions.antikythera.evaluator.Employee");
            evaluator.setupFields();
        }
    }
}
