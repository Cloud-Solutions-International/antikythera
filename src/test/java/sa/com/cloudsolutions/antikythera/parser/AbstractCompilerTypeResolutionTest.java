package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for AbstractCompiler type resolution methods.
 * 
 * Tests cover:
 * - findType() for various scenarios
 * - findFullyQualifiedName() for different type representations
 * - Same-package type resolution
 * - Generic/parameterized type handling
 * - detectTypeWithClassLoaders() behavior
 */
class AbstractCompilerTypeResolutionTest {

    /**
     * Helper method to create a PackageDeclaration with a qualified name.
     * Works around JavaParser's limitation with parseName() for qualified names.
     * Uses the same approach as AbstractCompilerTest - directly setting the name as String.
     */
    private static PackageDeclaration createPackageDeclaration(String packageName) {
        // The existing test uses setName() with String directly, which should work
        // But if it fails at runtime, we'll catch and handle it
        try {
            PackageDeclaration pd = new PackageDeclaration();
            pd.setName(packageName);
            return pd;
        } catch (Exception e) {
            // Fallback: parse a full compilation unit
            String[] parts = packageName.split("\\.");
            Name name = new Name(parts[0]);
            for (int i = 1; i < parts.length; i++) {
                name = new Name(name, parts[i]);
            }
            return new PackageDeclaration(name);
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AntikytheraRunTime.resetAll();
        AbstractCompiler.preProcess();
    }

    @Test
    void testFindType_SamePackageType() {
        // Create a compilation unit with a type in the same package
        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(createPackageDeclaration("test.package"));
        ClassOrInterfaceDeclaration testClass = new ClassOrInterfaceDeclaration();
        testClass.setName("TestClass");
        cu.addType(testClass);
        
        // Add the type to AntikytheraRunTime (simulating preProcess)
        String fqn = "test.package.TestClass";
        TypeWrapper wrapper = new TypeWrapper(testClass);
        AntikytheraRunTime.addType(fqn, wrapper);
        AntikytheraRunTime.addCompilationUnit(fqn, cu);
        
        // Try to find it by short name from the same package
        TypeWrapper found = AbstractCompiler.findType(cu, "TestClass");
        assertNotNull(found, "Should find same-package type");
        assertEquals("TestClass", found.getType().getNameAsString());
    }

    @Test
    void testFindType_WithImport() {
        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(createPackageDeclaration("test.package"));
        cu.addImport("java.util.List");
        
        TypeWrapper found = AbstractCompiler.findType(cu, "List");
        assertNotNull(found, "Should find type via import");
    }

    @Test
    void testFindType_JavaLangType() {
        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(createPackageDeclaration("test.package"));
        
        TypeWrapper found = AbstractCompiler.findType(cu, "String");
        assertNotNull(found, "Should find java.lang.String");
        assertNotNull(found.getClazz(), "Should resolve to Class");
        assertEquals("java.lang.String", found.getClazz().getName());
    }

    @Test
    void testFindType_GenericTypeName() {
        // Test that findType correctly extracts raw type name from parameterized types
        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(createPackageDeclaration("test.package"));
        
        // Create a parameterized type: List<String>
        ClassOrInterfaceType listType = new ClassOrInterfaceType();
        listType.setName("List");
        listType.setTypeArguments(StaticJavaParser.parseType("String").asClassOrInterfaceType());
        
        // Add List to AntikytheraRunTime
        ClassOrInterfaceDeclaration listClass = new ClassOrInterfaceDeclaration();
        listClass.setName("List");
        CompilationUnit listCu = new CompilationUnit();
        listCu.setPackageDeclaration(createPackageDeclaration("java.util"));
        listCu.addType(listClass);
        
        String listFqn = "java.util.List";
        TypeWrapper listWrapper = new TypeWrapper(listClass);
        AntikytheraRunTime.addType(listFqn, listWrapper);
        AntikytheraRunTime.addCompilationUnit(listFqn, listCu);
        
        cu.addImport("java.util.List");
        
        // findType should extract "List" from "List<String>"
        TypeWrapper found = AbstractCompiler.findType(cu, listType);
        assertNotNull(found, "Should find List type from parameterized type");
    }

    @Test
    void testFindFullyQualifiedName_ClassOrInterfaceType() {
        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(createPackageDeclaration("test.package"));
        cu.addImport("java.util.List");
        
        ClassOrInterfaceType listType = new ClassOrInterfaceType();
        listType.setName("List");
        
        String fqn = AbstractCompiler.findFullyQualifiedName(cu, listType);
        assertEquals("java.util.List", fqn);
    }

    @Test
    void testFindFullyQualifiedName_ParameterizedType() {
        // Test that parameterized types like List<String> resolve to List
        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(createPackageDeclaration("test.package"));
        cu.addImport("java.util.List");
        
        // Create List<String>
        ClassOrInterfaceType listType = StaticJavaParser.parseType("List<String>").asClassOrInterfaceType();
        
        String fqn = AbstractCompiler.findFullyQualifiedName(cu, listType);
        assertEquals("java.util.List", fqn, "Should extract raw type name from parameterized type");
    }

    @Test
    void testFindFullyQualifiedName_SamePackageType() {
        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(createPackageDeclaration("test.package"));
        ClassOrInterfaceDeclaration testClass = new ClassOrInterfaceDeclaration();
        testClass.setName("SamePackageClass");
        cu.addType(testClass);
        
        // Add to AntikytheraRunTime
        String fqn = "test.package.SamePackageClass";
        TypeWrapper wrapper = new TypeWrapper(testClass);
        AntikytheraRunTime.addType(fqn, wrapper);
        AntikytheraRunTime.addCompilationUnit(fqn, cu);
        
        String resolved = AbstractCompiler.findFullyQualifiedName(cu, "SamePackageClass");
        assertEquals(fqn, resolved, "Should resolve same-package type");
    }

    @Test
    void testFindFullyQualifiedName_NullCompilationUnit() {
        String fqn = AbstractCompiler.findFullyQualifiedName(null, "String");
        assertNull(fqn, "Should return null for null compilation unit");
    }

    @Test
    void testFindType_NullCompilationUnit() {
        TypeWrapper found = AbstractCompiler.findType(null, "String");
        assertNotNull(found, "Should find java.lang types even with null CU");
        assertNotNull(found.getClazz());
    }

    @Test
    void testFindType_TypeInCurrentCompilationUnit() {
        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(createPackageDeclaration("test.package"));
        ClassOrInterfaceDeclaration localClass = new ClassOrInterfaceDeclaration();
        localClass.setName("LocalClass");
        cu.addType(localClass);
        
        TypeWrapper found = AbstractCompiler.findType(cu, "LocalClass");
        assertNotNull(found, "Should find type in current compilation unit");
        assertEquals("LocalClass", found.getType().getNameAsString());
    }

    @Test
    void testFindType_WithScope() {
        // Test ClassOrInterfaceType with scope (e.g., com.example.Type)
        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(createPackageDeclaration("test.package"));
        
        // Create a scoped type: com.example.Type
        ClassOrInterfaceType scopedType = StaticJavaParser.parseType("com.example.Type").asClassOrInterfaceType();
        
        // This should try to resolve "com.example.Type"
        TypeWrapper found = AbstractCompiler.findType(cu, scopedType);
        // Result depends on whether the type exists, but should not throw
        // For non-existent types, it may return null, which is acceptable
        // The important thing is it doesn't throw
    }

    @Test
    void testDetectTypeWithClassLoaders_SamePackage() {
        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(createPackageDeclaration("test.package"));
        
        // Add a type to AntikytheraRunTime with same package
        ClassOrInterfaceDeclaration testClass = new ClassOrInterfaceDeclaration();
        testClass.setName("SamePackageType");
        CompilationUnit testCu = new CompilationUnit();
        testCu.setPackageDeclaration(createPackageDeclaration("test.package"));
        testCu.addType(testClass);
        
        String fqn = "test.package.SamePackageType";
        TypeWrapper wrapper = new TypeWrapper(testClass);
        AntikytheraRunTime.addType(fqn, wrapper);
        AntikytheraRunTime.addCompilationUnit(fqn, testCu);
        
        // detectTypeWithClassLoaders should find it
        // Note: This is a private method, so we test it indirectly via findType
        TypeWrapper found = AbstractCompiler.findType(cu, "SamePackageType");
        assertNotNull(found, "Should find same-package type via detectTypeWithClassLoaders");
    }

    @Test
    void testFindType_AntikytheraRunTimeLookup() {
        // Test the check at line 604-606 - this currently fails for short names
        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(createPackageDeclaration("test.package"));
        
        // Add type with FQN to AntikytheraRunTime
        ClassOrInterfaceDeclaration testClass = new ClassOrInterfaceDeclaration();
        testClass.setName("RunTimeType");
        CompilationUnit testCu = new CompilationUnit();
        testCu.setPackageDeclaration(createPackageDeclaration("test.package"));
        testCu.addType(testClass);
        
        String fqn = "test.package.RunTimeType";
        TypeWrapper wrapper = new TypeWrapper(testClass);
        AntikytheraRunTime.addType(fqn, wrapper);
        AntikytheraRunTime.addCompilationUnit(fqn, testCu);
        
        // Current implementation: getTypeDeclaration("RunTimeType") will fail
        // because it expects FQN, not short name
        // This test documents the current (broken) behavior
        TypeWrapper found = AbstractCompiler.findType(cu, "RunTimeType");
        // Should find it via detectTypeWithClassLoaders, not via getTypeDeclaration check
        assertNotNull(found, "Should find type via detectTypeWithClassLoaders fallback");
    }

    @Test
    void testFindFullyQualifiedName_GenericTypeParameter() {
        // Test handling of generic type parameters in field declarations
        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(createPackageDeclaration("test.package"));
        cu.addImport("java.util.List");
        
        // Simulate a field type: List<String>
        ClassOrInterfaceType listType = StaticJavaParser.parseType("List<String>").asClassOrInterfaceType();
        
        String fqn = AbstractCompiler.findFullyQualifiedName(cu, listType);
        assertEquals("java.util.List", fqn, "Should extract List from List<String>");
    }

    @Test
    void testFindType_NonExistentType() {
        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(createPackageDeclaration("test.package"));
        
        TypeWrapper found = AbstractCompiler.findType(cu, "NonExistentType12345");
        assertNull(found, "Should return null for non-existent type");
    }

    @Test
    void testFindFullyQualifiedName_NonExistentType() {
        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(createPackageDeclaration("test.package"));
        
        String fqn = AbstractCompiler.findFullyQualifiedName(cu, "NonExistentType12345");
        assertNull(fqn, "Should return null for non-existent type");
    }
}

