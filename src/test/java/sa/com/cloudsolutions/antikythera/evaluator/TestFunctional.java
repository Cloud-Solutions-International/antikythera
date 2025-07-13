package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TestFunctional extends TestHelper{

    public static final String SAMPLE_CLASS = "sa.com.cloudsolutions.antikythera.evaluator.Functional";
    CompilationUnit cu;

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void each() {
        AntikytheraRunTime.reset();
        cu = AntikytheraRunTime.getCompilationUnit(SAMPLE_CLASS);
        evaluator = EvaluatorFactory.create(SAMPLE_CLASS, Evaluator.class);
        System.setOut(new PrintStream(outContent));
    }

    @ParameterizedTest
    @CsvSource(value = {"greet1; Hello Ashfaloth", "greet2; Hello Ashfaloth", "greet3; Hello Thorin Oakenshield",
            "sorting1; 0123456789", "sorting2; 9876543210", "people4; [A]", "people5; A", "people6; A",
            "people7; Tom Bombadil", "nestedStream; 1AB2AB", "valueOf; 1",
            "staticMethodReference1; 234", "staticMethodReference2; 234", "collectAgain; 1 2",
            "peopleArray1; [A, B]", "array0; [1, 2, 3, 4, 5]", "arraySort1; 345679",
            "arraySort2; 345679", "peopleArray2; AB"}, delimiter = ';'
    )
    void testBiFunction(String name, String value) throws ReflectiveOperationException {
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals(name)).orElseThrow();
        evaluator = EvaluatorFactory.create(SAMPLE_CLASS, Evaluator.class);
        Variable v = evaluator.executeMethod(method);
        assertNull(v.getValue());
        assertEquals(value + "\n", outContent.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"people1","people2","people3"})
    void testPeople(String name) throws ReflectiveOperationException {
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals(name)).orElseThrow();
        Variable v = evaluator.executeMethod(method);
        assertNull(v.getValue());
        assertEquals("[A, B]\n", outContent.toString());
    }

    @Test
    void testMaps() throws ReflectiveOperationException {
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("maps1")).orElseThrow();
        Variable v = evaluator.executeMethod(method);
        assertNull(v.getValue());
        assertEquals("{25=A, 30=B}\n", outContent.toString());
    }
}
