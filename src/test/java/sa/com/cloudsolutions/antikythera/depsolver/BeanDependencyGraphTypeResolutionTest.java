package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BeanDependencyGraph type resolution, specifically resolveTypeFqn().
 * 
 * Tests cover:
 * - Parameterized type resolution (e.g., List<String> -> List)
 * - Same-package type resolution
 * - Generic type handling
 * - Fallback logic to AntikytheraRunTime
 */
class BeanDependencyGraphTypeResolutionTest {

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

    @BeforeAll
    static void setUp() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AntikytheraRunTime.resetAll();
        AbstractCompiler.preProcess();
    }

    @Test
    void testResolveTypeFqn_ParameterizedType() throws Exception {
        // Create a compilation unit with a parameterized field type
        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(createPackageDeclaration("test.package"));
        
        ClassOrInterfaceDeclaration clazz = new ClassOrInterfaceDeclaration();
        clazz.setName("TestClass");
        cu.addType(clazz);
        
        // Create a parameterized type: List<String>
        ClassOrInterfaceType listType = StaticJavaParser.parseType("List<String>").asClassOrInterfaceType();
        cu.addImport("java.util.List");
        
        // Use reflection to test private method
        BeanDependencyGraph graph = new BeanDependencyGraph();
        Method resolveMethod = BeanDependencyGraph.class.getDeclaredMethod(
                "resolveTypeFqn", 
                com.github.javaparser.ast.type.Type.class,
                ClassOrInterfaceDeclaration.class,
                CompilationUnit.class);
        resolveMethod.setAccessible(true);
        
        String fqn = (String) resolveMethod.invoke(graph, listType, clazz, cu);
        assertEquals("java.util.List", fqn, "Should extract raw type from parameterized type");
    }

    @Test
    void testResolveTypeFqn_ClassOrInterfaceType() throws Exception {
        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(createPackageDeclaration("test.package"));
        
        ClassOrInterfaceDeclaration clazz = new ClassOrInterfaceDeclaration();
        clazz.setName("TestClass");
        cu.addType(clazz);
        
        ClassOrInterfaceType stringType = StaticJavaParser.parseType("String").asClassOrInterfaceType();
        
        BeanDependencyGraph graph = new BeanDependencyGraph();
        Method resolveMethod = BeanDependencyGraph.class.getDeclaredMethod(
                "resolveTypeFqn",
                com.github.javaparser.ast.type.Type.class,
                ClassOrInterfaceDeclaration.class,
                CompilationUnit.class);
        resolveMethod.setAccessible(true);
        
        String fqn = (String) resolveMethod.invoke(graph, stringType, clazz, cu);
        assertEquals("java.lang.String", fqn);
    }

    @Test
    void testResolveTypeFqn_SamePackageType() throws Exception {
        // Create two classes in the same package
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
        
        // Add both to AntikytheraRunTime
        String fqn1 = "test.package.ServiceA";
        String fqn2 = "test.package.ServiceB";
        TypeWrapper wrapper1 = new TypeWrapper(class1);
        TypeWrapper wrapper2 = new TypeWrapper(class2);
        wrapper1.setService(true);
        wrapper2.setService(true);
        
        AntikytheraRunTime.addType(fqn1, wrapper1);
        AntikytheraRunTime.addCompilationUnit(fqn1, cu1);
        AntikytheraRunTime.addType(fqn2, wrapper2);
        AntikytheraRunTime.addCompilationUnit(fqn2, cu2);
        
        // Try to resolve ServiceB from ServiceA's context
        ClassOrInterfaceType serviceBType = StaticJavaParser.parseType("ServiceB").asClassOrInterfaceType();
        
        BeanDependencyGraph graph = new BeanDependencyGraph();
        Method resolveMethod = BeanDependencyGraph.class.getDeclaredMethod(
                "resolveTypeFqn",
                com.github.javaparser.ast.type.Type.class,
                ClassOrInterfaceDeclaration.class,
                CompilationUnit.class);
        resolveMethod.setAccessible(true);
        
        String fqn = (String) resolveMethod.invoke(graph, serviceBType, class1, cu1);
        assertEquals(fqn2, fqn, "Should resolve same-package type");
    }

    @Test
    void testResolveTypeFqn_GenericType() throws Exception {
        // Test resolution of generic types like TypedService<String>
        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(createPackageDeclaration("test.package"));
        
        ClassOrInterfaceDeclaration clazz = new ClassOrInterfaceDeclaration();
        clazz.setName("TestClass");
        cu.addType(clazz);
        
        // Create a generic type: TypedService<String>
        ClassOrInterfaceType genericType = StaticJavaParser.parseType("TypedService<String>").asClassOrInterfaceType();
        
        // Add TypedService to AntikytheraRunTime
        ClassOrInterfaceDeclaration typedServiceClass = new ClassOrInterfaceDeclaration();
        typedServiceClass.setName("TypedService");
        CompilationUnit typedServiceCu = new CompilationUnit();
        typedServiceCu.setPackageDeclaration(createPackageDeclaration("test.package"));
        typedServiceCu.addType(typedServiceClass);
        
        String typedServiceFqn = "test.package.TypedService";
        TypeWrapper typedServiceWrapper = new TypeWrapper(typedServiceClass);
        AntikytheraRunTime.addType(typedServiceFqn, typedServiceWrapper);
        AntikytheraRunTime.addCompilationUnit(typedServiceFqn, typedServiceCu);
        
        BeanDependencyGraph graph = new BeanDependencyGraph();
        Method resolveMethod = BeanDependencyGraph.class.getDeclaredMethod(
                "resolveTypeFqn",
                com.github.javaparser.ast.type.Type.class,
                ClassOrInterfaceDeclaration.class,
                CompilationUnit.class);
        resolveMethod.setAccessible(true);
        
        String fqn = (String) resolveMethod.invoke(graph, genericType, clazz, cu);
        assertEquals(typedServiceFqn, fqn, "Should resolve generic type to raw type");
    }

    @Test
    void testResolveTypeFqn_ArrayType() throws Exception {
        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(createPackageDeclaration("test.package"));
        
        ClassOrInterfaceDeclaration clazz = new ClassOrInterfaceDeclaration();
        clazz.setName("TestClass");
        cu.addType(clazz);
        
        // Create an array type: String[]
        com.github.javaparser.ast.type.ArrayType arrayType = 
                StaticJavaParser.parseType("String[]").asArrayType();
        
        BeanDependencyGraph graph = new BeanDependencyGraph();
        Method resolveMethod = BeanDependencyGraph.class.getDeclaredMethod(
                "resolveTypeFqn",
                com.github.javaparser.ast.type.Type.class,
                ClassOrInterfaceDeclaration.class,
                CompilationUnit.class);
        resolveMethod.setAccessible(true);
        
        String fqn = (String) resolveMethod.invoke(graph, arrayType, clazz, cu);
        assertEquals("java.lang.String", fqn, "Should extract component type from array");
    }

    @Test
    void testResolveTypeFqn_NullType() throws Exception {
        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(createPackageDeclaration("test.package"));
        
        ClassOrInterfaceDeclaration clazz = new ClassOrInterfaceDeclaration();
        clazz.setName("TestClass");
        cu.addType(clazz);
        
        BeanDependencyGraph graph = new BeanDependencyGraph();
        Method resolveMethod = BeanDependencyGraph.class.getDeclaredMethod(
                "resolveTypeFqn",
                com.github.javaparser.ast.type.Type.class,
                ClassOrInterfaceDeclaration.class,
                CompilationUnit.class);
        resolveMethod.setAccessible(true);
        
        // Test with null - should handle gracefully
        try {
            resolveMethod.invoke(graph, null, clazz, cu);
            // May return null or throw - either is acceptable
        } catch (Exception e) {
            // Exception is acceptable for null input
            assertTrue(e.getCause() instanceof NullPointerException || 
                      e.getCause() instanceof IllegalArgumentException);
        }
    }
}

