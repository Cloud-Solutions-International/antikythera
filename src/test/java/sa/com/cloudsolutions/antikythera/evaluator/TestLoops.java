package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.depsolver.ClassProcessor;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestLoops extends  TestHelper {

    @BeforeAll
    public static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
    }

    @BeforeEach
    public void each() throws Exception {
        compiler = new TestLoopsCompiler();
        System.setOut(new PrintStream(outContent));
    }


    @ParameterizedTest
    @ValueSource(strings = {"forLoop", "forLoopWithBreak", "whileLoop", "doWhileLoop", "forEach","forEach2",
            "whileLoopWithBreak","forEachLoop","forEachLoopWithBreak", "forLoopWithReturn", "forEach3"})
    void testLoops(String methodName) throws AntikytheraException, ReflectiveOperationException {
        CompilationUnit cu = compiler.getCompilationUnit();
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals(methodName)).orElseThrow();
        Variable v = evaluator.executeMethod(method);
        if(methodName.equals("forLoopWithReturn")) {
            assertEquals("Hello world", v.getValue());
        } else {
            assertNull(v.getValue());
        }
        assertEquals("0\n1\n2\n3\n4\n5\n6\n7\n8\n9\n", outContent.toString());
    }

    class TestLoopsCompiler extends ClassProcessor {
        protected TestLoopsCompiler() throws IOException, AntikytheraException {
            parse(classToPath("sa.com.cloudsolutions.antikythera.evaluator.Loops.java"));
            compileDependencies();
            evaluator = new Evaluator("sa.com.cloudsolutions.antikythera.evaluator.Loops");
            evaluator.setupFields();
        }
    }

}
