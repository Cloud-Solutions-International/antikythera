package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.DummyArgumentGenerator;
import sa.com.cloudsolutions.antikythera.evaluator.EvaluatorFactory;
import sa.com.cloudsolutions.antikythera.evaluator.SpringEvaluator;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class SideEffectTest {

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @Test
    void testVoidMethodsSideEffects() throws ReflectiveOperationException {
        String cls = "sa.com.cloudsolutions.antikythera.testhelper.evaluator.VoidNoOp";
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(cls);
        assertNotNull(cu);

        UnitTestGenerator generator = new UnitTestGenerator(cu);
        generator.setArgumentGenerator(new DummyArgumentGenerator());
        generator.setAsserter(new JunitAsserter());

        SpringEvaluator evaluator = EvaluatorFactory.create(cls, SpringEvaluator.class);
        evaluator.setOnTest(true);
        evaluator.addGenerator(generator);
        evaluator.setArgumentGenerator(new DummyArgumentGenerator());

        // Process the class
        for (MethodDeclaration md : cu.findAll(MethodDeclaration.class)) {
            evaluator.visit(md);
        }

        CompilationUnit gen = generator.getCompilationUnit();
        assertNotNull(gen);
        String genSource = gen.toString();

        // hasSideEffect should have a test
        assertTrue(genSource.contains("hasSideEffectTest"), "hasSideEffect should have a generated test");
        
        // noSideEffect should NOT have a test
        assertFalse(genSource.contains("noSideEffectTest"), "noSideEffect should NOT have a generated test");

        // returnsValue should have a test
        assertTrue(genSource.contains("returnsValueTest"), "returnsValue should have a generated test");
    }
}
