package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;


class TestAKBuddy extends TestHelper {
    public static final String SAMPLE_CLASS = "sa.com.cloudsolutions.antikythera.evaluator.Employee";
    CompilationUnit cu;

    @BeforeAll
    static void beforeClass() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        MockingRegistry.reset();
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void before() {
        System.setOut(new PrintStream(outContent));
        cu = AntikytheraRunTime.getCompilationUnit(SAMPLE_CLASS);
    }

    @Test
    void createDyncamicClass() throws ReflectiveOperationException {
        evaluator = EvaluatorFactory.create(SAMPLE_CLASS, Evaluator.class);
        TypeDeclaration<?> cdecl = AbstractCompiler.getMatchingType(evaluator.getCompilationUnit(), "Employee").orElseThrow();
        MethodInterceptor interceptor = new MethodInterceptor(evaluator);
        Class<?> clazz = AKBuddy.createDynamicClass(interceptor);
        assertNotNull(clazz);
        Object instance = AKBuddy.createInstance(clazz, interceptor);
        assertNotNull(instance);

        for(FieldDeclaration fd : cdecl.getFields()) {
            String name = fd.getVariable(0).getNameAsString();
            assertNotNull(instance.getClass().getDeclaredField(name));
        }
    }

    @Test
    void createComplexDynamicClass() throws ReflectiveOperationException {
        evaluator = EvaluatorFactory.create("sa.com.cloudsolutions.antikythera.evaluator.FakeService", SpringEvaluator.class);

        TypeDeclaration<?> cdecl = AbstractCompiler.getMatchingType(evaluator.getCompilationUnit(), "FakeService").orElseThrow();
        MethodInterceptor interceptor = new MethodInterceptor(evaluator);
        Class<?> clazz = AKBuddy.createDynamicClass(interceptor);
        Object instance = AKBuddy.createInstance(clazz, interceptor);
        assertNotNull(instance);

        for(FieldDeclaration fd : cdecl.getFields()) {
            String name = fd.getVariable(0).getNameAsString();
            assertNotNull(instance.getClass().getDeclaredField(name));
        }
    }

    @Test
    void swapInterceptor() throws ReflectiveOperationException {
        Evaluator evaluator1 = EvaluatorFactory.create(SAMPLE_CLASS, Evaluator.class);
        Evaluator evaluator2 = EvaluatorFactory.create(SAMPLE_CLASS, Evaluator.class);
        MethodInterceptor interceptor1 = new MethodInterceptor(evaluator1);
        MethodInterceptor interceptor2 = new MethodInterceptor(evaluator2);

        Class<?> clazz = AKBuddy.createDynamicClass(interceptor1);
        Object emp1 = AKBuddy.createInstance(clazz, interceptor1);
        assertNotNull(emp1, "interceptor on first instance setup correctly");

        Object emp2 = AKBuddy.createInstance(clazz, interceptor2);
        assertNotNull(emp2, "interceptor on second instance setup correctly");

        Field f = emp1.getClass().getDeclaredField("p");
        f.setAccessible(true);

        Method m1 = emp1.getClass().getDeclaredMethod("publicAccess");
        Method m2 = emp2.getClass().getDeclaredMethod("publicAccess");

        assertNotNull(m1);
        assertNotNull(m2, "publicAccess method defined in Employee sources has been copied to the dynamic class");

        Object fieldValue = f.get(emp1); // get the value of field 'p' from emp1
        assertEquals("sa.com.cloudsolutions.antikythera.evaluator.Person",
                fieldValue.getClass().getName(), "We have an accessible Person field in Employee");


        Method setName = fieldValue.getClass().getMethod("setName", String.class);
        setName.invoke(fieldValue, "Horatio");

        m1.invoke(emp1);
        m2.invoke(emp2);

        assertEquals("Horatio\nHornblower\n", outContent.toString());
    }

    @Test
    void mockWithAKBuddy() throws ReflectiveOperationException, SQLException {
        Class<?> clazz = AKBuddy.createDynamicClass(new MethodInterceptor(Statement.class));
        Statement instance = (Statement) clazz.getDeclaredConstructor().newInstance();

        assertNotNull(instance);

        assertEquals("0", instance.enquoteLiteral("aaa"));
        assertNull(instance.executeBatch());
        assertDoesNotThrow(instance::close);
    }

    @Test
    void testConvert() throws ReflectiveOperationException {
        evaluator = EvaluatorFactory.create("sa.com.cloudsolutions.antikythera.evaluator.ConvertValue", SpringEvaluator.class);
        cu = evaluator.getCompilationUnit();
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("convert")).orElseThrow();
        evaluator.executeMethod(method);
        assertEquals("Horatio\nHornblower\n", outContent.toString());
    }

    @ParameterizedTest
    @CsvSource({"createPerson1, Person created: Horatio"})
    void createPersion(String name, String value) throws ReflectiveOperationException {
        evaluator = EvaluatorFactory.create("sa.com.cloudsolutions.antikythera.evaluator.Reflective", SpringEvaluator.class);
        cu = evaluator.getCompilationUnit();
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals(name)).orElseThrow();
        evaluator.executeMethod(method);
        assertEquals(value, outContent.toString().strip());
    }
}
