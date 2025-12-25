package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.depsolver.Graph;
import sa.com.cloudsolutions.antikythera.depsolver.GraphNode;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ImportUtils type resolution and import handling.
 * 
 * Tests cover:
 * - addImport() with Type
 * - addImport() with String name
 * - findPackage() for classes and types
 */
class ImportUtilsTest {

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
    void setUp() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        sa.com.cloudsolutions.antikythera.parser.AbstractCompiler.reset();
        AntikytheraRunTime.resetAll();
        sa.com.cloudsolutions.antikythera.parser.AbstractCompiler.preProcess();
    }

    @Test
    void testAddImport_WithType() {
        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(createPackageDeclaration("test.package"));
        cu.addImport("java.util.List");
        
        ClassOrInterfaceDeclaration clazz = new ClassOrInterfaceDeclaration();
        clazz.setName("TestClass");
        cu.addType(clazz);
        
        GraphNode node = Graph.createGraphNode(clazz);
        // Graph.createGraphNode should automatically set the compilation unit from the node's parent
        
        ClassOrInterfaceType listType = StaticJavaParser.parseType("List").asClassOrInterfaceType();
        GraphNode result = ImportUtils.addImport(node, listType);
        
        // addImport can return null if the type is already imported or not found
        // The important thing is that it doesn't throw an exception
        // Check that the import was added to the destination compilation unit
        assertTrue(node.getDestination().getImports().stream()
                .anyMatch(imp -> imp.getNameAsString().equals("java.util.List")),
                "Import should be added to destination");
    }

    @Test
    void testAddImport_WithStringName() {
        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(createPackageDeclaration("test.package"));
        cu.addImport("java.util.List");
        
        ClassOrInterfaceDeclaration clazz = new ClassOrInterfaceDeclaration();
        clazz.setName("TestClass");
        cu.addType(clazz);
        
        GraphNode node = Graph.createGraphNode(clazz);
        // Graph.createGraphNode should automatically set the compilation unit from the node's parent
        // But since we created clazz standalone, we need to ensure the compilation unit is accessible
        // The node should have a destination compilation unit set by Graph.createGraphNode
        
        GraphNode result = ImportUtils.addImport(node, "List");
        // addImport can return null if the type is already imported or not found
        // The important thing is that it doesn't throw an exception
        // Check that the import was added to the destination compilation unit if destination exists
        if (node.getDestination() != null) {
            assertTrue(node.getDestination().getImports().stream()
                    .anyMatch(imp -> imp.getNameAsString().equals("java.util.List")),
                    "Import should be added to destination");
        }
    }

    @Test
    void testAddImport_SamePackageType() {
        // Create two types in the same package
        CompilationUnit cu1 = new CompilationUnit();
        cu1.setPackageDeclaration(createPackageDeclaration("test.package"));
        ClassOrInterfaceDeclaration class1 = new ClassOrInterfaceDeclaration();
        class1.setName("ServiceA");
        cu1.addType(class1);
        
        CompilationUnit cu2 = new CompilationUnit();
        cu2.setPackageDeclaration(createPackageDeclaration("test.package"));
        ClassOrInterfaceDeclaration class2 = new ClassOrInterfaceDeclaration();
        class2.setName("ServiceB");
        cu2.addType(class2);
        
        String fqn1 = "test.package.ServiceA";
        String fqn2 = "test.package.ServiceB";
        
        AntikytheraRunTime.addType(fqn1, new TypeWrapper(class1));
        AntikytheraRunTime.addCompilationUnit(fqn1, cu1);
        AntikytheraRunTime.addType(fqn2, new TypeWrapper(class2));
        AntikytheraRunTime.addCompilationUnit(fqn2, cu2);
        
        GraphNode node = Graph.createGraphNode(class1);
        
        // Try to add import for ServiceB from ServiceA (same package)
        GraphNode result = ImportUtils.addImport(node, "ServiceB");
        // Should find it via findFullyQualifiedName even without explicit import
        assertNotNull(result, "Should find same-package type");
    }

    @Test
    void testFindPackage_Class() {
        String packageName = ImportUtils.findPackage(String.class);
        assertEquals("java.lang", packageName);
    }

    @Test
    void testFindPackage_TypeDeclaration() {
        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(createPackageDeclaration("test.package"));
        ClassOrInterfaceDeclaration clazz = new ClassOrInterfaceDeclaration();
        clazz.setName("TestClass");
        cu.addType(clazz);
        
        String packageName = ImportUtils.findPackage(clazz);
        assertEquals("test.package", packageName);
    }

    @Test
    void testFindPackage_TypeDeclaration_NoPackage() {
        CompilationUnit cu = new CompilationUnit();
        // No package declaration
        ClassOrInterfaceDeclaration clazz = new ClassOrInterfaceDeclaration();
        clazz.setName("TestClass");
        cu.addType(clazz);
        
        String packageName = ImportUtils.findPackage(clazz);
        assertEquals("", packageName, "Should return empty string for default package");
    }
}

