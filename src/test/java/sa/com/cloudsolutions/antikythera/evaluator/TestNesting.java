package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TestNesting extends TestHelper{
    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @Test
    void testInnerClasses() {
        assertNotNull(AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.antikythera.evaluator.Bunches"));
        assertNotNull(AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.antikythera.evaluator.Bunches.DTO"));
        assertNotNull(AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.antikythera.evaluator.Bunches.Person"));

        assertNotNull(AntikytheraRunTime.getTypeDeclaration("sa.com.cloudsolutions.antikythera.evaluator.Bunches"));
        assertNotNull(AntikytheraRunTime.getTypeDeclaration("sa.com.cloudsolutions.antikythera.evaluator.Bunches.DTO"));
        assertNotNull(AntikytheraRunTime.getTypeDeclaration("sa.com.cloudsolutions.antikythera.evaluator.Bunches.Person"));

        assertNotNull(AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.antikythera.evaluator.Nesting"));
        assertNotNull(AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.antikythera.evaluator.Nesting.Inner"));
        assertNotNull(AntikytheraRunTime.getTypeDeclaration("sa.com.cloudsolutions.antikythera.evaluator.Nesting"));
        assertNotNull(AntikytheraRunTime.getTypeDeclaration("sa.com.cloudsolutions.antikythera.evaluator.Nesting.Inner"));

    }

    @ParameterizedTest
    @CsvSource({"t1, Hello World", "t2, from outer method"})
    void testNesting(String name, String output) throws ReflectiveOperationException {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.antikythera.evaluator.Nesting");
        evaluator = EvaluatorFactory.create("sa.com.cloudsolutions.antikythera.evaluator.Nesting", Evaluator.class);
        System.setOut(new PrintStream(outContent));

        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals(name)).orElseThrow();
        evaluator.executeMethod(method);
        assertEquals(output + "\n", outContent.toString());
    }
}
