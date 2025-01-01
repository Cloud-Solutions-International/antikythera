package sa.com.cloudsolutions.antikythera.evaluator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.depsolver.ClassProcessor;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TestSpringEvaluator {
    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap();
    }

    @Test
    void testSimpleController() throws IOException, AntikytheraException, ReflectiveOperationException {
        ClassProcessor cp = new ClassProcessor();
        cp.compile( AbstractCompiler.classToPath("sa.com.cloudsolutions.controller.SimpleController"));

        ClassProcessor cp1 = new ClassProcessor();
        cp1.compile( AbstractCompiler.classToPath("sa.com.cloudsolutions.dto.MediumDTO"));

        ClassProcessor cp2 = new ClassProcessor();
        cp2.compile( AbstractCompiler.classToPath("sa.com.cloudsolutions.dto.Constants"));

        CompilationUnit cu = cp.getCompilationUnit();
        SpringEvaluator eval = new SpringEvaluator("sa.com.cloudsolutions.controller.SimpleController");

        eval.executeMethod(cu.findFirst(MethodDeclaration.class).get());
        assertNull(eval.returnValue);

    }
}
