package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.Name;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AntikytheraRunTime type storage and retrieval.
 * 
 * Tests cover:
 * - addType() and getTypeDeclaration()
 * - getResolvedTypes()
 * - Type lookup by FQN
 * - resetAll() functionality
 */
class AntikytheraRunTimeTest {

    /**
     * Helper method to create a PackageDeclaration with a qualified name.
     */
    private static PackageDeclaration createPackageDeclaration(String packageName) {
        String[] parts = packageName.split("\\.");
        if (parts.length == 0) {
            return new PackageDeclaration();
        }
        com.github.javaparser.ast.expr.Name name = new com.github.javaparser.ast.expr.Name(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            name = new com.github.javaparser.ast.expr.Name(name, parts[i]);
        }
        return new PackageDeclaration(name);
    }

    @BeforeEach
    void setUp() {
        AntikytheraRunTime.resetAll();
    }

    @AfterEach
    void tearDown() {
        AntikytheraRunTime.resetAll();
    }

    @Test
    void testAddTypeAndGetTypeDeclaration() {
        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(createPackageDeclaration("test.package"));
        ClassOrInterfaceDeclaration clazz = new ClassOrInterfaceDeclaration();
        clazz.setName("TestClass");
        cu.addType(clazz);
        
        String fqn = "test.package.TestClass";
        TypeWrapper wrapper = new TypeWrapper(clazz);
        wrapper.setService(true);
        
        AntikytheraRunTime.addType(fqn, wrapper);
        AntikytheraRunTime.addCompilationUnit(fqn, cu);
        
        Optional<com.github.javaparser.ast.body.TypeDeclaration<?>> found = 
                AntikytheraRunTime.getTypeDeclaration(fqn);
        
        assertTrue(found.isPresent(), "Should find type by FQN");
        assertEquals("TestClass", found.get().getNameAsString());
    }

    @Test
    void testGetTypeDeclaration_NonExistentType() {
        Optional<com.github.javaparser.ast.body.TypeDeclaration<?>> found = 
                AntikytheraRunTime.getTypeDeclaration("non.existent.Type");
        
        assertFalse(found.isPresent(), "Should return empty for non-existent type");
    }

    @Test
    void testGetTypeDeclaration_ShortName() {
        // This test documents the current behavior: getTypeDeclaration requires FQN
        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(createPackageDeclaration("test.package"));
        ClassOrInterfaceDeclaration clazz = new ClassOrInterfaceDeclaration();
        clazz.setName("TestClass");
        cu.addType(clazz);
        
        String fqn = "test.package.TestClass";
        TypeWrapper wrapper = new TypeWrapper(clazz);
        
        AntikytheraRunTime.addType(fqn, wrapper);
        AntikytheraRunTime.addCompilationUnit(fqn, cu);
        
        // Try with short name - should fail
        Optional<com.github.javaparser.ast.body.TypeDeclaration<?>> found = 
                AntikytheraRunTime.getTypeDeclaration("TestClass");
        
        assertFalse(found.isPresent(), "getTypeDeclaration requires FQN, not short name");
    }

    @Test
    void testGetResolvedTypes() {
        CompilationUnit cu1 = new CompilationUnit();
        cu1.setPackageDeclaration(createPackageDeclaration("test.package"));
        ClassOrInterfaceDeclaration clazz1 = new ClassOrInterfaceDeclaration();
        clazz1.setName("Class1");
        cu1.addType(clazz1);
        
        CompilationUnit cu2 = new CompilationUnit();
        cu2.setPackageDeclaration(createPackageDeclaration("test.package"));
        ClassOrInterfaceDeclaration clazz2 = new ClassOrInterfaceDeclaration();
        clazz2.setName("Class2");
        cu2.addType(clazz2);
        
        String fqn1 = "test.package.Class1";
        String fqn2 = "test.package.Class2";
        
        AntikytheraRunTime.addType(fqn1, new TypeWrapper(clazz1));
        AntikytheraRunTime.addType(fqn2, new TypeWrapper(clazz2));
        
        Map<String, TypeWrapper> resolvedTypes = AntikytheraRunTime.getResolvedTypes();
        
        assertTrue(resolvedTypes.containsKey(fqn1), "Should contain Class1");
        assertTrue(resolvedTypes.containsKey(fqn2), "Should contain Class2");
        assertEquals(2, resolvedTypes.size());
    }

    @Test
    void testResetAll() {
        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(createPackageDeclaration("test.package"));
        ClassOrInterfaceDeclaration clazz = new ClassOrInterfaceDeclaration();
        clazz.setName("TestClass");
        cu.addType(clazz);
        
        String fqn = "test.package.TestClass";
        AntikytheraRunTime.addType(fqn, new TypeWrapper(clazz));
        AntikytheraRunTime.addCompilationUnit(fqn, cu);
        
        assertTrue(AntikytheraRunTime.getTypeDeclaration(fqn).isPresent());
        
        AntikytheraRunTime.resetAll();
        
        assertFalse(AntikytheraRunTime.getTypeDeclaration(fqn).isPresent(), 
                "Should clear all types after resetAll");
        assertTrue(AntikytheraRunTime.getResolvedTypes().isEmpty(), 
                "Should clear resolvedTypes after resetAll");
    }

    @Test
    void testIsServiceClass() {
        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(createPackageDeclaration("test.package"));
        ClassOrInterfaceDeclaration clazz = new ClassOrInterfaceDeclaration();
        clazz.setName("ServiceClass");
        cu.addType(clazz);
        
        String fqn = "test.package.ServiceClass";
        TypeWrapper wrapper = new TypeWrapper(clazz);
        wrapper.setService(true);
        
        AntikytheraRunTime.addType(fqn, wrapper);
        
        assertTrue(AntikytheraRunTime.isServiceClass(fqn), "Should identify service class");
        assertFalse(AntikytheraRunTime.isServiceClass("non.existent.Class"), 
                "Should return false for non-existent class");
    }

    @Test
    void testGetCompilationUnit() {
        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(createPackageDeclaration("test.package"));
        ClassOrInterfaceDeclaration clazz = new ClassOrInterfaceDeclaration();
        clazz.setName("TestClass");
        cu.addType(clazz);
        
        String fqn = "test.package.TestClass";
        AntikytheraRunTime.addCompilationUnit(fqn, cu);
        
        CompilationUnit retrieved = AntikytheraRunTime.getCompilationUnit(fqn);
        assertSame(cu, retrieved, "Should return the same compilation unit");
    }
}

