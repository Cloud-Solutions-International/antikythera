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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class OptEvaluatorTest {

    private static Evaluator evaluator;

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
        evaluator = EvaluatorFactory.create("sa.com.cloudsolutions.antikythera.evaluator.Opt", Evaluator.class);
    }

    @ParameterizedTest
    @CsvSource({
            "getById, 1, true, 1",
            "getById, 0, false, 0"
    })
    void testGetById(String methodName, int id, boolean isPresent, Integer value) throws ReflectiveOperationException {
        MethodDeclaration method = evaluator.getCompilationUnit().findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals(methodName)).orElseThrow();
        AntikytheraRunTime.push(new Variable(id));
        Variable result = evaluator.executeMethod(method);

        assertInstanceOf(Optional.class, result.getValue(), "Result should be an Optional");
        Optional<?> optionalResult = (Optional<?>) result.getValue();

        assertEquals(isPresent, optionalResult.isPresent());

        if (isPresent) {
            assertEquals(value, optionalResult.get(), "Value should be: " + value);
        }
    }

    @Test
    void testGetOrNullNotNull() throws ReflectiveOperationException {
        MethodDeclaration method = evaluator.getCompilationUnit().findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals("getOrNull")).orElseThrow();

        AntikytheraRunTime.push(new Variable(1));
        Variable result = evaluator.executeMethod(method);
        assertNotNull(result, "Result should not be null");
        assertEquals(1,result.getValue());
    }

    @Test
    void testGetOrNull() throws ReflectiveOperationException {
        MethodDeclaration method = evaluator.getCompilationUnit().findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals("getOrNull")).orElseThrow();

        AntikytheraRunTime.push(new Variable(0));
        Variable result = evaluator.executeMethod(method);
        assertNull(result.getValue());
    }

    @Test
    void getWithoutThrow() throws ReflectiveOperationException {
        MethodDeclaration method = evaluator.getCompilationUnit().findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals("getOrThrow")).orElseThrow();

        AntikytheraRunTime.push(new Variable(1));
        Variable result = evaluator.executeMethod(method);

        assertEquals(1, result.getValue());
    }

    @Test
    void getThrowsException() {
        MethodDeclaration method = evaluator.getCompilationUnit().findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals("getOrThrow")).orElseThrow();

        assertThrows(IllegalArgumentException.class, () -> {
            AntikytheraRunTime.push(new Variable(0));
            evaluator.executeMethod(method);
        });
    }
}

