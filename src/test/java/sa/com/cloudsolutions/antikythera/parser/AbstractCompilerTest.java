package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.constants.Constants;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

import java.io.FileNotFoundException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

class AbstractCompilerTest {

    private final String BASE_PATH = (String) Settings.getProperty(Constants.BASE_PATH);

    @BeforeAll
    static void setUp() throws IOException, ReflectiveOperationException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
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

    //    Tests for getFields() method
    @Test
    void getFieldsReturnsEmptyMapWhenClassNotFound() throws IOException {
        Path tempFilePath = Files.createTempFile("TempClass", ".java");
        Files.write(tempFilePath, """
                public class TempClass {
                }
            """.getBytes());

        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        combinedTypeSolver.add(new JavaParserTypeSolver(tempFilePath.getParent()));
        JavaSymbolSolver symbolResolver = new JavaSymbolSolver(combinedTypeSolver);
        ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(symbolResolver);
        JavaParser javaParser = new JavaParser(parserConfiguration);

        try (FileInputStream in = new FileInputStream(tempFilePath.toFile())) {
            CompilationUnit cu = javaParser.parse(in).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));

            AbstractCompiler compiler = new AbstractCompiler();
            Map<String, FieldDeclaration> fields = compiler.getFields(cu, "NonExistentClass");
            assertTrue(fields.isEmpty());
        } finally {
            Files.delete(tempFilePath);
        }
    }

    @Test
    void getFieldsReturnsMapWithFieldNamesAndTypes() throws IOException {
        Path tempFilePath = Files.createTempFile("TempClass", ".java");
        Files.write(tempFilePath, """
                public class TempClass {
                    private Long id;
                    private String name;
                    private String description;
                }
            """.getBytes());

        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        combinedTypeSolver.add(new JavaParserTypeSolver(tempFilePath.getParent()));
        JavaSymbolSolver symbolResolver = new JavaSymbolSolver(combinedTypeSolver);
        ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(symbolResolver);
        JavaParser javaParser = new JavaParser(parserConfiguration);

        try (FileInputStream in = new FileInputStream(tempFilePath.toFile())) {
            CompilationUnit cu = javaParser.parse(in).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));

            AbstractCompiler compiler = new AbstractCompiler();
            Map<String, FieldDeclaration> fields = compiler.getFields(cu, "TempClass");
            assertFalse(fields.isEmpty());
            assertEquals(fields.size(), 3);
            assertTrue(fields.containsKey("id"));
            assertEquals(fields.get("id").toString(), "private Long id;");
            assertTrue(fields.containsKey("name"));
            assertEquals(fields.get("name").toString(), "private String name;");
            assertTrue(fields.containsKey("description"));
            assertEquals(fields.get("description").toString(), "private String description;");
        } finally {
            Files.delete(tempFilePath);
        }
    }

    @Test
    void getFieldsReturnsEmptyMapForClassWithoutFields() throws IOException {
        Path tempFilePath = Files.createTempFile("TempClass", ".java");
        Files.write(tempFilePath, """
                public class TempClass {
                }
            """.getBytes());

        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        combinedTypeSolver.add(new JavaParserTypeSolver(tempFilePath.getParent()));
        JavaSymbolSolver symbolResolver = new JavaSymbolSolver(combinedTypeSolver);
        ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(symbolResolver);
        JavaParser javaParser = new JavaParser(parserConfiguration);

        try (FileInputStream in = new FileInputStream(tempFilePath.toFile())) {
            CompilationUnit cu = javaParser.parse(in).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));

            AbstractCompiler compiler = new AbstractCompiler();
            Map<String, FieldDeclaration> fields = compiler.getFields(cu, "TempClass");
            assertTrue(fields.isEmpty());
        } finally {
            Files.delete(tempFilePath);
        }
    }

    // Tests for compile() method
    @Test
    void compileThrowsFileNotFoundExceptionWhenFileDoesNotExist() throws IOException {
        String nonExistentPath = "non/existent/Path.java";
        AbstractCompiler compiler = new AbstractCompiler();
        assertThrows(FileNotFoundException.class, () -> compiler.compile(nonExistentPath));
    }

    @Test
    void compileParsesControllerFileWhenDtoFileDoesNotExist() throws IOException {
        Path tempFilePath = Files.createTempFile(Path.of(BASE_PATH), "TempController", ".java");
        Files.write(tempFilePath, """
            public class TempController {
                public class TempDto {
                }
            }
        """.getBytes());

        try {
            AbstractCompiler compiler = new AbstractCompiler();
            compiler.compile(tempFilePath.toString().replace(BASE_PATH + "/", ""));

            assertNotNull(compiler.cu);
            assertTrue(compiler.cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .anyMatch(cls -> cls.getNameAsString().endsWith("Dto")));
        } finally {
            Files.delete(tempFilePath);
        }
    }

    @Test
    void compileCachesParsedCompilationUnit() throws IOException {
        Path tempFilePath = Files.createTempFile(Path.of(BASE_PATH), "TempClass", ".java");
        Files.write(tempFilePath, """
            public class TempClass {
            }
        """.getBytes());

        try {
            AbstractCompiler compiler = new AbstractCompiler();
            compiler.compile(tempFilePath.toString().replace(BASE_PATH + "/", ""));
            CompilationUnit firstParse = compiler.cu;

            compiler.compile(tempFilePath.toString().replace(BASE_PATH + "/", ""));
            CompilationUnit secondParse = compiler.cu;

            assertSame(firstParse, secondParse);
        } finally {
            Files.delete(tempFilePath);
        }
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

        result = AbstractCompiler.findFullyQualifiedName(cu, "DTOHandlerTest");
        assertEquals("sa.com.cloudsolutions.antikythera.parser.DTOHandlerTest", result);

    }
}
