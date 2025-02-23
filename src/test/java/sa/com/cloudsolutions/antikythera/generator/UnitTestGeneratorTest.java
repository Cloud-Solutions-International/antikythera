package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.NullArgumentGenerator;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import org.mockito.Mockito;

import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UnitTestGeneratorTest {

    private UnitTestGenerator unitTestGenerator;
    private CompilationUnit cu;
    private ClassOrInterfaceDeclaration classUnderTest;
    private MethodDeclaration methodUnderTest;

    @BeforeAll
    static void beforeClass() throws IOException {
        Settings.loadConfigMap();
        AbstractCompiler.reset();
    }


    @BeforeEach
    void setUp() {
        cu = new CompilationUnit();
        cu.setPackageDeclaration("sa.com.cloudsolutions.antikythera.generator");
        classUnderTest = cu.addClass("DummyService");
        classUnderTest.addField("DummyRepository", "dummyRepository").addAnnotation("Autowired");

        unitTestGenerator = new UnitTestGenerator(cu);

        methodUnderTest = new MethodDeclaration();
        methodUnderTest.setName("dummyMethod");
        classUnderTest.addMember(methodUnderTest);
        unitTestGenerator.setArgumentGenerator(new NullArgumentGenerator());
        unitTestGenerator.setPreConditions(new HashSet<>());
        unitTestGenerator.setAsserter(new JunitAsserter());
    }

    @Test
    void testMockFields() throws IOException {
        classUnderTest.addAnnotation("Service");
        unitTestGenerator.mockFields();
        CompilationUnit testCu = unitTestGenerator.getCompilationUnit();
        TypeDeclaration<?> testClass = testCu.getType(0);

        Optional<FieldDeclaration> mockedField = testClass.getFieldByName("dummyRepository");
        assertTrue(mockedField.isPresent(), "The field 'dummyRepository' should be present in the test class.");
        assertTrue(mockedField.get().getAnnotationByName("MockBean").isPresent(), "The field 'dummyRepository' should be annotated with @MockBean.");

        assertTrue(testCu.getImports().stream().anyMatch(i -> i.getNameAsString().equals("org.springframework.boot.test.mock.mockito.MockBean")),
                "The import for @MockBean should be present.");
        assertTrue(testCu.getImports().stream().anyMatch(i -> i.getNameAsString().equals("org.mockito.Mockito")),
                "The import for Mockito should be present.");
    }

    @Test
    void testCreateInstanceA() {
        classUnderTest.addAnnotation("Service");
        unitTestGenerator.createTests(methodUnderTest, new MethodResponse());
        assertTrue(unitTestGenerator.getCompilationUnit().toString().contains("dummyMethodTest"));
    }

    @Test
    void testCreateInstanceB() {
        unitTestGenerator.createTests(methodUnderTest, new MethodResponse());
        assertTrue(unitTestGenerator.getCompilationUnit().toString().contains("dummyMethodTest"));
    }

    @Test
    void testCreateInstanceC() {
        ConstructorDeclaration constructor = classUnderTest.addConstructor();
        constructor.addParameter("String", "param");

        unitTestGenerator.createTests(methodUnderTest, new MethodResponse());
        assertTrue(unitTestGenerator.getCompilationUnit().toString().contains("dummyMethodTest"));
    }
}
