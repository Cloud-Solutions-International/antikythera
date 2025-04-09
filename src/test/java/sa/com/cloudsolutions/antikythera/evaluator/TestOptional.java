package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TestOptional extends TestHelper {

    private static Evaluator evaluator;

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
        evaluator = EvaluatorFactory.create("sa.com.cloudsolutions.antikythera.evaluator.Opt", Evaluator.class);
    }

    @BeforeEach
    void each() throws AntikytheraException {
        System.setOut(new PrintStream(outContent));
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
                m -> m.getNameAsString().equals("getOrNull1")).orElseThrow();

        AntikytheraRunTime.push(new Variable(1));
        Variable result = evaluator.executeMethod(method);
        assertNotNull(result, "Result should not be null");
        assertEquals(1,result.getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"getOrNull1", "getOrNull2", "getOrNull3"})
    void testGetOrNull(String name) throws ReflectiveOperationException {
        MethodDeclaration method = evaluator.getCompilationUnit().findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals(name)).orElseThrow();

        AntikytheraRunTime.push(new Variable(0));
        Variable result = evaluator.executeMethod(method);
        assertNull(result.getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"getOrThrow1", "getOrThrow2"})
    void getWithoutThrow(String name) throws ReflectiveOperationException {
        MethodDeclaration method = evaluator.getCompilationUnit().findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals(name)).orElseThrow();

        AntikytheraRunTime.push(new Variable(1));
        Variable result = evaluator.executeMethod(method);

        assertEquals(1, result.getValue());
    }

    @Test
    void getThrowsException() {
        MethodDeclaration method = evaluator.getCompilationUnit().findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals("getOrThrow1")).orElseThrow();

        AntikytheraRunTime.push(new Variable(0));
        Throwable ex = assertThrows(AntikytheraException.class, () -> evaluator.executeMethod(method));

        assertInstanceOf(NoSuchElementException.class, ex.getCause(), "Cause should be IllegalArgumentException");
    }

    @Test
    void getThrowsIllegalException() {
        MethodDeclaration method = evaluator.getCompilationUnit().findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals("getOrThrowIllegal")).orElseThrow();

        AntikytheraRunTime.push(new Variable(0));
        Throwable ex = assertThrows(AntikytheraException.class, () -> evaluator.executeMethod(method));

        assertInstanceOf(IllegalArgumentException.class, ex.getCause(), "Cause should be IllegalArgumentException");
    }

    @ParameterizedTest
    @CsvSource({"1, 'ID: 1'", "0, ''"})
    void testIfPresent(Integer a, String b) throws ReflectiveOperationException {
        MethodDeclaration method = evaluator.getCompilationUnit().findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals("ifPresent")).orElseThrow();

        AntikytheraRunTime.push(new Variable(a));
        evaluator.executeMethod(method);

        assertEquals(b, outContent.toString());
    }

    @ParameterizedTest
    @CsvSource({"1, 'ID: 1'", "0, ''"})
    void testIfEmpty(Integer a, String b) throws ReflectiveOperationException {
        MethodDeclaration method = evaluator.getCompilationUnit().findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals("ifEmpty")).orElseThrow();

        AntikytheraRunTime.push(new Variable(a));
        evaluator.executeMethod(method);

        assertEquals(b, outContent.toString());
    }
}