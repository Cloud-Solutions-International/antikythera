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
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.NullArgumentGenerator;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
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
        Settings.loadConfigMap(new File("src/test/resources/generator.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void setUp() {
        cu = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.service.Service");
        assertNotNull(cu);
        classUnderTest = cu.getType(0).asClassOrInterfaceDeclaration();
        methodUnderTest = classUnderTest.findFirst(MethodDeclaration.class, md -> md.getNameAsString().equals("queries2")).get();
        unitTestGenerator = new UnitTestGenerator(cu);
        unitTestGenerator.setArgumentGenerator(new NullArgumentGenerator());
        unitTestGenerator.setPreConditions(new HashSet<>());
        unitTestGenerator.setAsserter(new JunitAsserter());
    }

    @Test
    void testMockFields() {
        classUnderTest.addAnnotation("Service");
        unitTestGenerator.mockFields();
        CompilationUnit testCu = unitTestGenerator.getCompilationUnit();
        TypeDeclaration<?> testClass = testCu.getType(0);

        Optional<FieldDeclaration> mockedField = testClass.getFieldByName("personRepository");
        assertTrue(mockedField.isPresent());
        assertTrue(mockedField.get().getAnnotationByName("Mock").isPresent(), "The field 'dummyRepository' should be annotated with @Mock.");

        assertTrue(testCu.getImports().stream().anyMatch(i -> i.getNameAsString().equals("org.mockito.Mock")),
                "The import for @Mock should be present.");
        assertTrue(testCu.getImports().stream().anyMatch(i -> i.getNameAsString().equals("org.mockito.Mockito")),
                "The import for Mockito should be present.");
    }

    @Test
    void testCreateInstanceA() {
        unitTestGenerator.createTests(methodUnderTest, new MethodResponse());
        assertTrue(unitTestGenerator.getCompilationUnit().toString().contains("queries2Test"));
    }

    @Test
    void testCreateInstanceC() {
        ConstructorDeclaration constructor = classUnderTest.addConstructor();
        constructor.addParameter("String", "param");

        unitTestGenerator.createTests(methodUnderTest, new MethodResponse());
        assertTrue(unitTestGenerator.getCompilationUnit().toString().contains("queries2Test"));
    }

    @Test
    void testLoadExisting() throws IOException {
        // Get the actual FactoryTest.java from source directory
        File testFile = new File("src/test/java/sa/com/cloudsolutions/antikythera/generator/FactoryTest.java");
        assertTrue(testFile.exists(), testFile.getAbsolutePath() + " does not exist");

        // Execute loadExisting
        unitTestGenerator.loadExisting(testFile);
        assertNotNull(unitTestGenerator.gen);
        assertFalse(unitTestGenerator.gen.toString().contains("Author : Antikythera"));

    }
}

class UnitTestGeneratorMoreTest {

    @BeforeAll
    static void beforeClass() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    /**
     * The base class should be added to the class under test.
     */
    @Test
    void testAddingBaseClassToTestClass() {
        CompilationUnit base = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.antikythera.generator.DummyBase");
        assertNotNull(base);
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.antikythera.evaluator.Overlord");
        assertNotNull(cu);

        ClassOrInterfaceDeclaration classUnderTest = cu.getType(0).asClassOrInterfaceDeclaration();
        UnitTestGenerator unitTestGenerator = new UnitTestGenerator(cu);

        assertTrue(classUnderTest.getExtendedTypes().isEmpty());
        CompilationUnit testCu = unitTestGenerator.getCompilationUnit();
        assertNotNull(testCu);
        TypeDeclaration<?> publicType = AbstractCompiler.getPublicType(testCu);
        assertNotNull(publicType);
        assertEquals("OverlordAKTest", publicType.getNameAsString());
        assertTrue(publicType.asClassOrInterfaceDeclaration().getExtendedTypes()
                .stream()
                .anyMatch(t -> t.asString().equals("sa.com.cloudsolutions.antikythera.generator.DummyBase")));
    }
}
