package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;

import java.io.FileNotFoundException;

import static org.junit.jupiter.api.Assertions.*;

class AbstractCompilerTest {

    @BeforeAll
    static void setUp() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @Test
    void testClassToPath() {
        String className = "com.cloud.api.generator.AbstractCompilerTest";
        assertEquals("com/cloud/api/generator/AbstractCompilerTest.java", AbstractCompiler.classToPath(className));
        assertEquals("com/cloud/api/generator/AbstractCompilerTest.java",
                AbstractCompiler.classToPath(className + ".java"));
    }

    @Test
    void testPathToClass() {
        String path = "com/cloud/api/generator/AbstractCompilerTest.java";
        assertEquals("com.cloud.api.generator.AbstractCompilerTest", AbstractCompiler.pathToClass(path));
        assertEquals("com.cloud.api.generator.AbstractCompilerTest.javaxxx",
                AbstractCompiler.pathToClass(path + "xxx"));
    }

    // Tests for compile() method
    @Test
    void compileThrowsFileNotFoundExceptionWhenFileDoesNotExist() throws IOException {
        String nonExistentPath = "non/existent/Path.java";
        AbstractCompiler compiler = new AbstractCompiler();
        assertThrows(FileNotFoundException.class, () -> compiler.compile(nonExistentPath));
    }

    @Test
    void compileCachesParsedCompilationUnit() throws IOException {
        CompilationUnit first = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.antikythera.parser.Empty");
        AbstractCompiler.preProcess();
        CompilationUnit second = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.antikythera.parser.Empty");
        assertSame(first, second);
    }

    @Test
    void testGetPublicClass() {
        CompilationUnit outer = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.antikythera.parser.Nested");
        assertNotNull(outer);
        assertEquals("Nested", AbstractCompiler.getPublicType(outer).getNameAsString());
        CompilationUnit inner = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.antikythera.parser.Nested.Inner");
        assertSame(outer, inner);
    }

    @Test
    void testGetPublicEnum() {
        CompilationUnit cu = StaticJavaParser.parse("public class TempController {}\n");
        TypeDeclaration<?> result = AbstractCompiler.getPublicType(cu);
        assertNotNull(result);
    }

    @Test
    void testGetPublicNull() {
        CompilationUnit cu = StaticJavaParser.parse("class TempController {}\n");
        TypeDeclaration<?> result = AbstractCompiler.getPublicType(cu);
        assertNull(result);
    }

    @Test
    void testWildCardImport()  {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.antikythera.parser.Nested");
        assertNotNull(cu);
        ImportWrapper w = AbstractCompiler.findWildcardImport(cu, "List");
        assertNotNull(w);
        assertNotNull(w.getSimplified());
        assertEquals("java.util.List", w.getSimplified().getNameAsString());
        assertNotNull(AbstractCompiler.findWildcardImport(cu, "Loops"));
    }

    @Test
    void testFindFullyQualifiedName() {
        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(new PackageDeclaration().setName("sa.com.cloudsolutions.antikythera.parser"));
        cu.addImport(new ImportDeclaration("java.util.List", false, false));
        cu.addImport(new ImportDeclaration("sa.com.cloudsolutions.antikythera.SomeClass", false, false));
        cu.addType(new ClassOrInterfaceDeclaration().setName("TestClass"));

        String result = AbstractCompiler.findFullyQualifiedName(cu, "SomeClass");
        assertEquals("sa.com.cloudsolutions.antikythera.SomeClass", result);

        result = AbstractCompiler.findFullyQualifiedName(cu, "List");
        assertEquals("java.util.List", result);

        result = AbstractCompiler.findFullyQualifiedName(cu, "Integer");
        assertEquals("java.lang.Integer", result);

        result = AbstractCompiler.findFullyQualifiedName(cu, "ClassProcessorTest");
        assertEquals("sa.com.cloudsolutions.antikythera.parser.ClassProcessorTest", result);
    }
}
