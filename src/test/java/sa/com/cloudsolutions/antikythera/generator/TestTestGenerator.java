package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestTestGenerator {

    private TestGenerator testGenerator;

    @BeforeEach
    void setUp() {
        CompilationUnit testCompilationUnit = new CompilationUnit();
        testCompilationUnit.addClass("TestClass");

        // Create an anonymous concrete implementation of TestGenerator for testing
        testGenerator = new TestGenerator(null) {
            @Override
            public void createTests(com.github.javaparser.ast.body.CallableDeclaration<?> md, MethodResponse response) {
                // Not needed for this test
            }

            @Override
            public void setCommonPath(String commonPath) {
                // Not needed for this test
            }

            @Override
            public void addBeforeClass() {
                // Not needed for this test
            }
        };
        testGenerator.gen = testCompilationUnit; // Set the protected gen field
    }

    @Test
    void testRemoveDuplicateTests() {
        TypeDeclaration<?> testClass = testGenerator.gen.getType(0).asTypeDeclaration();

        // Add unique method
        MethodDeclaration method1 = new MethodDeclaration();
        method1.setName("testMethod1");
        method1.addAnnotation("Test");
        method1.setBody(new BlockStmt());
        method1.getBody().get().addStatement(StaticJavaParser.parseStatement("int x = 1;"));
        testClass.addMember(method1);

        // Add a duplicate of method1
        MethodDeclaration method1Duplicate = method1.clone();
        method1Duplicate.setName("testMethod1Duplicate"); // Name will be changed to DUMMY for comparison
        testClass.addMember(method1Duplicate);

        // Add another unique method
        MethodDeclaration method2 = new MethodDeclaration();
        method2.setName("testMethod2");
        method2.addAnnotation("Test");
        method2.setBody(new BlockStmt());
        method2.getBody().get().addStatement(StaticJavaParser.parseStatement("int y = 2;"));
        testClass.addMember(method2);

        // Add a duplicate of method2
        MethodDeclaration method2Duplicate = method2.clone();
        method2Duplicate.setName("testMethod2Duplicate"); // Name will be changed to DUMMY for comparison
        testClass.addMember(method2Duplicate);

        // Add a non-test method (should not be affected)
        MethodDeclaration nonTestMethod = new MethodDeclaration();
        nonTestMethod.setName("helperMethod");
        nonTestMethod.setBody(new BlockStmt());
        testClass.addMember(nonTestMethod);

        assertEquals(5, testClass.getMethods().size(), "Initial number of methods should be 5");

        boolean removed = testGenerator.removeDuplicateTests();

        assertTrue(removed, "Should have removed duplicate tests");
        assertEquals(3, testClass.getMethods().size(), "Should have 3 methods after removing duplicates");

        List<MethodDeclaration> remainingMethods = testClass.getMethods();
        assertTrue(remainingMethods.stream().anyMatch(m -> m.getNameAsString().equals("testMethod1")), "testMethod1 should remain");
        assertTrue(remainingMethods.stream().anyMatch(m -> m.getNameAsString().equals("testMethod2")), "testMethod2 should remain");
        assertTrue(remainingMethods.stream().anyMatch(m -> m.getNameAsString().equals("helperMethod")), "helperMethod should remain");
        assertFalse(remainingMethods.stream().anyMatch(m -> m.getNameAsString().equals("testMethod1Duplicate")), "testMethod1Duplicate should be removed");
        assertFalse(remainingMethods.stream().anyMatch(m -> m.getNameAsString().equals("testMethod2Duplicate")), "testMethod2Duplicate should be removed");
    }

    @Test
    void testRemoveDuplicateTests_noDuplicates() {
        TypeDeclaration<?> testClass = testGenerator.gen.getType(0).asTypeDeclaration();

        MethodDeclaration method1 = new MethodDeclaration();
        method1.setName("testMethod1");
        method1.addAnnotation("Test");
        method1.setBody(new BlockStmt());
        method1.getBody().get().addStatement(StaticJavaParser.parseStatement("int x = 1;"));
        testClass.addMember(method1);

        MethodDeclaration method2 = new MethodDeclaration();
        method2.setName("testMethod2");
        method2.addAnnotation("Test");
        method2.setBody(new BlockStmt());
        method2.getBody().get().addStatement(StaticJavaParser.parseStatement("int y = 2;"));
        testClass.addMember(method2);

        assertEquals(2, testClass.getMethods().size(), "Initial number of methods should be 2");

        boolean removed = testGenerator.removeDuplicateTests();

        assertFalse(removed, "Should not have removed any tests");
        assertEquals(2, testClass.getMethods().size(), "Number of methods should remain 2");
    }

    @Test
    void testRemoveDuplicateTests_emptyCompilationUnit() {
        testGenerator.gen = new CompilationUnit(); // Empty CU
        assertFalse(testGenerator.removeDuplicateTests(), "Should return false for empty compilation unit");
    }

    @Test
    void testRemoveDuplicateTests_noTestMethods() {
        TypeDeclaration<?> testClass = testGenerator.gen.getType(0).asTypeDeclaration();
        MethodDeclaration nonTestMethod = new MethodDeclaration();
        nonTestMethod.setName("helperMethod");
        nonTestMethod.setBody(new BlockStmt());
        testClass.addMember(nonTestMethod);

        assertEquals(1, testClass.getMethods().size());
        assertFalse(testGenerator.removeDuplicateTests(), "Should return false if no test methods are present");
        assertEquals(1, testClass.getMethods().size());
    }
}
