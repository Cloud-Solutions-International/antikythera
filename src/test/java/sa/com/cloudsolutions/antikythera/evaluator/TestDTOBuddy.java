package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;


class TestDTOBuddy extends TestHelper {
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
        cu = AntikytheraRunTime.getCompilationUnit(SAMPLE_CLASS);
    }

    @Test
    void createDynamicDto() throws ReflectiveOperationException {
        ClassOrInterfaceDeclaration cdecl = cu.getTypes().get(0).asClassOrInterfaceDeclaration();
        Class<?> clazz = DTOBuddy.createDynamicClass(cdecl);
        Object instance = clazz.getDeclaredConstructor().newInstance();
        assertNotNull(instance);

        for(FieldDeclaration fd : cdecl.getFields()) {
            String name = fd.getVariable(0).getNameAsString();
            assertNotNull(instance.getClass().getDeclaredField(name));
        }
    }
}
