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

public class TestAnon extends TestHelper{

    @BeforeAll
    public static void setup() throws IOException, ReflectiveOperationException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    public void each() throws Exception {
        compiler = new TestAnonCompiler();
        System.setOut(new PrintStream(outContent));
    }

    @Test
    void testAnonPerson() throws AntikytheraException, ReflectiveOperationException {
        CompilationUnit cu = compiler.getCompilationUnit();
        MethodDeclaration ts = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("anonPerson")).orElseThrow();
        evaluator.executeMethod(ts);
        assertEquals("Bush\n", outContent.toString() );
    }

    class TestAnonCompiler extends ClassProcessor {
        protected TestAnonCompiler() throws IOException, AntikytheraException {
            preProcess();
            parse(classToPath("sa.com.cloudsolutions.antikythera.evaluator.Anon.java"));
            compileDependencies();
            evaluator = new Evaluator("sa.com.cloudsolutions.antikythera.evaluator.Anon");
            evaluator.setupFields(cu);
        }
    }
}
