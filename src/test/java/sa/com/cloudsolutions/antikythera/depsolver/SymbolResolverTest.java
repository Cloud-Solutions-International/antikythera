package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.ImportWrapper;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SymbolResolver.resolveScopedNameThroughImport() method.
 *
 * This tests the core logic of resolving imports to their type declarations,
 * which is critical for dependency resolution and type inference.
 */
class SymbolResolverTest {

    @BeforeAll
    static void setUpClass() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AntikytheraRunTime.resetAll();
        AbstractCompiler.preProcess();
    }

    /**
     * Test resolution of a real internal import from the preprocessed codebase.
     * This tests the path: non-asterisk → getCompilationUnit → getPublicType
     */
    @Test
    void testResolveScopedNameThroughImport_RealInternalClass() {
        // Use a real class from the test helpers that we know exists after
        // preprocessing
        String className = "sa.com.cloudsolutions.antikythera.testhelper.evaluator.ReturnValue";
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(className);
        assertNotNull(cu, "ReturnValue should be preprocessed");

        // Create an import for this class
        ImportDeclaration imp = StaticJavaParser.parseImport("import " + className + ";");
        ImportWrapper wrapper = new ImportWrapper(imp);

        // Resolve it
        Optional<Resolver.ScopedResolution> result = SymbolResolver.resolveScopedNameThroughImport(wrapper);

        assertTrue(result.isPresent(), "Should resolve internal import");
        assertNotNull(result.get().importWrapper(), "Should have import wrapper");
        assertNull(result.get().type(), "Type field should be null for import resolution");
    }

    /**
     * Test resolution of an asterisk import.
     * This tests the path: asterisk → imp.getType()
     */
    @Test
    void testResolveScopedNameThroughImport_AsteriskImport() {
        // Create an asterisk import
        ImportDeclaration imp = StaticJavaParser
                .parseImport("import sa.com.cloudsolutions.antikythera.testhelper.evaluator.*;");

        // For asterisk imports, we need to set the type manually as AbstractCompiler
        // would
        String className = "sa.com.cloudsolutions.antikythera.testhelper.evaluator.ReturnValue";
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(className);
        TypeDeclaration<?> typeDecl = cu.findFirst(ClassOrInterfaceDeclaration.class).orElse(null);

        ImportWrapper wrapper = new ImportWrapper(imp);
        wrapper.setType(typeDecl);

        Optional<Resolver.ScopedResolution> result = SymbolResolver.resolveScopedNameThroughImport(wrapper);

        assertTrue(result.isPresent(), "Should resolve asterisk import");
        assertNotNull(result.get().importWrapper(), "Should have import wrapper");
    }

    /**
     * Test resolution of an external import (from JDK or external library).
     * This tests the path: isExternal() → return early with just importWrapper
     */
    @Test
    void testResolveScopedNameThroughImport_ExternalImport() {
        // Create an external import (java.util.List)
        ImportDeclaration imp = StaticJavaParser.parseImport("import java.util.List;");

        // Mark it as external by providing the Class
        ImportWrapper wrapper = new ImportWrapper(imp, java.util.List.class);

        Optional<Resolver.ScopedResolution> result = SymbolResolver.resolveScopedNameThroughImport(wrapper);

        assertTrue(result.isPresent(), "Should resolve external import");
        assertNotNull(result.get().importWrapper(), "Should have import wrapper");
        assertNull(result.get().type(), "Type field should be null");
        assertTrue(result.get().importWrapper().isExternal(), "Should be marked as external");
    }

    /**
     * Test resolution of a static field import.
     * This tests the path: getField() != null → return import with null type
     */
    @Test
    void testResolveScopedNameThroughImport_StaticFieldImport() {
        // Create a static field import
        ImportDeclaration imp = StaticJavaParser.parseImport("import static java.lang.Math.PI;");
        ImportWrapper wrapper = new ImportWrapper(imp);

        // Simulate what AbstractCompiler does for field imports
        // (In real code, the field would be set during import resolution)
        wrapper.setField(StaticJavaParser.parseBodyDeclaration("public static final double PI = 3.14159;")
                .asFieldDeclaration());

        Optional<Resolver.ScopedResolution> result = SymbolResolver.resolveScopedNameThroughImport(wrapper);

        assertTrue(result.isPresent(), "Should resolve field import");
        assertNotNull(result.get().importWrapper(), "Should have import wrapper");
        assertNull(result.get().type(), "Type field should be null");
    }

    /**
     * Test resolution when the imported type cannot be found in AntikytheraRunTime.
     * This tests the fallback path: getCompilationUnit returns null → return import
     * with null type
     */
    @Test
    void testResolveScopedNameThroughImport_UnresolvableInternalImport() {
        // Create an import for a class that doesn't exist in runtime
        ImportDeclaration imp = StaticJavaParser.parseImport("import com.example.NonExistent;");
        ImportWrapper wrapper = new ImportWrapper(imp);

        Optional<Resolver.ScopedResolution> result = SymbolResolver.resolveScopedNameThroughImport(wrapper);

        assertTrue(result.isPresent(), "Should still return a result even if unresolvable");
        assertNotNull(result.get().importWrapper(), "Should have import wrapper");
        assertNull(result.get().type(), "Type field should be null");
    }
}
