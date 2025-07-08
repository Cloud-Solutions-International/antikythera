package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.DummyArgumentGenerator;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ServicesParserTest {

    @BeforeEach
    void setUp() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AntikytheraRunTime.reset();
    }

    @Test
    void constructorShouldThrowExceptionForNonExistentClass() {
        assertThrows(AntikytheraException.class, () ->
            new ServicesParser("nonexistent.Class")
        );
    }

    @Test
    void evaluateMethodShouldProcessPublicMethod() {
        // Create a simple test class
        CompilationUnit cu = StaticJavaParser.parse("""
            public class TestService {
                public void testMethod() {
                    int x = 1;
                }
            }
        """);
        AntikytheraRunTime.addCompilationUnit("TestService", cu);

        ServicesParser parser = new ServicesParser("TestService");
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class).get();

        // Should not throw any exception
        assertDoesNotThrow(() ->
            parser.evaluateMethod(method)
        );
    }

    @Test
    void startShouldProcessAllPublicMethods() {
        CompilationUnit cu = StaticJavaParser.parse("""
            public class TestService {
                public void method1() {}
                private void method2() {}
                public void method3() {}
            }
        """);
        AntikytheraRunTime.addCompilationUnit("TestService", cu);

        ServicesParser parser = new ServicesParser("TestService");
        assertDoesNotThrow(() -> parser.start());
    }

    @Test
    void startWithMethodShouldProcessSpecificMethod()  {
        CompilationUnit cu = StaticJavaParser.parse("""
            public class TestService {
                public void targetMethod() {}
                public void otherMethod() {}
            }
        """);
        AntikytheraRunTime.addCompilationUnit("TestService", cu);

        ServicesParser parser = new ServicesParser("TestService");
        assertDoesNotThrow(() -> parser.start("targetMethod"));
    }
}
