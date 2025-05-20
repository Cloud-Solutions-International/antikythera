package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
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
        evaluator = EvaluatorFactory.create(SAMPLE_CLASS, Evaluator.class);
        MethodInterceptor interceptor = new MethodInterceptor(evaluator);

        Class<?> clazz = AKBuddy.createDynamicClass(interceptor);
        Object emp1 = AKBuddy.createInstance(clazz, interceptor);
        assertNotNull(emp1);

        Object emp2 = AKBuddy.createInstance(clazz, interceptor);
        assertNotNull(emp2);

        Field f = emp1.getClass().getDeclaredField("p");
        f.setAccessible(true);

        Object fieldValue = f.get(emp1); // get the value of field 'p' from emp1
        Method setName = fieldValue.getClass().getMethod("setName", String.class);
        setName.invoke(fieldValue, "NewName");

        Method m1 = emp1.getClass().getDeclaredMethod("publicAccess");
        Method m2 = emp1.getClass().getDeclaredMethod("publicAccess");

        m1.invoke(emp1);
        m2.invoke(emp2);

        assertEquals("Hornblower\nHornblower\n", outContent.toString());
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
}
