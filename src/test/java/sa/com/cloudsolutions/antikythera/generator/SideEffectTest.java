package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.AfterEach;
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

    @AfterEach
    void restoreSettings() {
        Settings.setProperty(Settings.GENERATE_CONSTRUCTOR_TESTS, null);
        Settings.setProperty(Settings.SKIP_VOID_NO_SIDE_EFFECTS, null);
    }

    @Test
    void testSideEffectsDefault() throws ReflectiveOperationException {
        // Default behavior should be skipping
        Settings.setProperty(Settings.SKIP_VOID_NO_SIDE_EFFECTS, null);
        
        String genSource = runGenerator();

        // hasSideEffect should have a test
        assertTrue(genSource.contains("hasSideEffectTest"), "hasSideEffect should have a generated test");
        
        // noSideEffect should NOT have a test
        assertFalse(genSource.contains("noSideEffectTest"), "noSideEffect should NOT have a generated test");

        // returnsValue should have a test
        assertTrue(genSource.contains("returnsValueTest"), "returnsValue should have a generated test");
    }

    @Test
    void testSideEffectsDisabled() throws ReflectiveOperationException {
        // When skipping is disabled, no-op void methods should get tests
        Settings.setProperty(Settings.SKIP_VOID_NO_SIDE_EFFECTS, false);
        
        String genSource = runGenerator();

        assertTrue(genSource.contains("hasSideEffectTest"), "hasSideEffect should have a generated test");
        assertTrue(genSource.contains("noSideEffectTest"), "noSideEffect should have a test when skipping is disabled");
        assertTrue(genSource.contains("returnsValueTest"), "returnsValue should have a generated test");
    }

    @Test
    void testConstructorSideEffects() throws ReflectiveOperationException {
        Settings.setProperty(Settings.GENERATE_CONSTRUCTOR_TESTS, true);
        Settings.setProperty(Settings.LOG_APPENDER, "sa.com.cloudsolutions.LogAppender");

        String genSource = runGenerator("sa.com.cloudsolutions.antikythera.testhelper.evaluator.ConstructorSideEffect");

        assertTrue(genSource.contains("DefaultConstructorTest"), "Should have a test for the constructor");
        assertTrue(genSource.contains("assertEquals(\"Constructor side effect!\", outputStream.toString().trim())"), 
                "Should assert console output");
        assertTrue(genSource.contains("LogAppender.hasMessage"), "Should assert logging output");
    }

    private String runGenerator() throws ReflectiveOperationException {
        return runGenerator("sa.com.cloudsolutions.antikythera.testhelper.evaluator.VoidNoOp");
    }

    private String runGenerator(String cls) throws ReflectiveOperationException {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(cls);
        assertNotNull(cu, "CompilationUnit for " + cls + " should not be null");

        UnitTestGenerator generator = new UnitTestGenerator(cu);
        generator.setArgumentGenerator(new DummyArgumentGenerator());
        generator.setAsserter(new JunitAsserter());

        SpringEvaluator evaluator = EvaluatorFactory.create(cls, SpringEvaluator.class);
        evaluator.setOnTest(true);
        evaluator.addGenerator(generator);
        evaluator.setArgumentGenerator(new DummyArgumentGenerator());

        // Process the class
        for (CallableDeclaration<?> cd : cu.findAll(CallableDeclaration.class)) {
            if (cd instanceof MethodDeclaration md) {
                evaluator.visit(md);
            } else if (cd instanceof ConstructorDeclaration constructorDeclaration) {
                evaluator.visit(constructorDeclaration);
            }
        }

        CompilationUnit gen = generator.getCompilationUnit();
        assertNotNull(gen);
        return gen.toString();
    }
}
