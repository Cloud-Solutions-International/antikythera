package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.ClassProcessor;

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

    @Test
    void testForLoop() throws AntikytheraException, ReflectiveOperationException {
        CompilationUnit cu = compiler.getCompilationUnit();
        MethodDeclaration forLoop = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("forLoop")).orElseThrow();
        Variable v = evaluator.executeMethod(forLoop);
        assertNull(v.getValue());
        assertEquals("0\n1\n2\n3\n4\n5\n6\n7\n8\n9\n", outContent.toString() );
    }

    @Test
    void testWhileLoop() throws AntikytheraException, ReflectiveOperationException {
        CompilationUnit cu = compiler.getCompilationUnit();
        MethodDeclaration whileLoop = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("whileLoop")).orElseThrow();
        Variable v = evaluator.executeMethod(whileLoop);
        assertNull(v.getValue());
        assertEquals("0\n1\n2\n3\n4\n5\n6\n7\n8\n9\n", outContent.toString() );
    }

    @Test
    void testWhileWithBreak() throws AntikytheraException, ReflectiveOperationException {
        CompilationUnit cu = compiler.getCompilationUnit();
        MethodDeclaration whileLoop = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("whileLoopWithBreak")).orElseThrow();
        Variable v = evaluator.executeMethod(whileLoop);
        assertNull(v.getValue());
        assertEquals("0\n1\n2\n3\n4\n5\n6\n7\n8\n9\n", outContent.toString() );
    }

    @Test
    void testForEachLoop() throws AntikytheraException, ReflectiveOperationException {
        CompilationUnit cu = compiler.getCompilationUnit();
        MethodDeclaration forLoop = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("forEachLoop")).orElseThrow();
        Variable v = evaluator.executeMethod(forLoop);
        assertNull(v.getValue());
        assertEquals("false\n", outContent.toString() );

    }

    class TestLoopsCompiler extends ClassProcessor {
        protected TestLoopsCompiler() throws IOException, AntikytheraException {
            parse(classToPath("sa.com.cloudsolutions.antikythera.evaluator.Loops.java"));
            compileDependencies();
            evaluator = new Evaluator("");
            evaluator.setupFields(cu);
        }
    }

}
