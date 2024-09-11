package com.cloud.api.generator;

import com.cloud.api.configurations.Settings;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static com.cloud.api.generator.ClassProcessor.*;
import static org.junit.jupiter.api.Assertions.*;

class RestControllerParserTest {

    private RestControllerParser parser;
    private Path path;
    private static String basePath;
    private static String outputPath;

    @BeforeEach
    void setUp() throws IOException {
        Settings.loadConfigMap();
        basePath = Settings.getProperty("BASE_PATH");
        String controllers = Settings.getProperty("CONTROLLERS");
        outputPath = Settings.getProperty("OUTPUT_PATH");
        String s = Settings.getProperty("CONTROLLERS");
        if (s.endsWith(SUFFIX)) {
            path = Paths.get(basePath, controllers.replace(".", "/").replace("/java", SUFFIX));
        } else {
            path = Paths.get(basePath, controllers.replace(".", "/"));
        }

        parser = new RestControllerParser(path.toFile());
    }


    @Test
    void start_processesRestControllerSuccessfully() throws IOException {
        parser.start();

        File srcDirectory = new File(outputPath + "/src/main/java/");
        File testDirectory = new File(outputPath + "/src/test/java/");
        assertTrue(srcDirectory.exists() && srcDirectory.isDirectory());
        assertTrue(testDirectory.exists() && testDirectory.isDirectory());
    }

    @Test
    void start_throwsIOExceptionWhenProcessingFails()  {
        System.out.println(basePath);
        Path invalidPath = Paths.get(basePath, "invalid/path", path.toString());
        RestControllerParser invalidParser = new RestControllerParser(invalidPath.toFile());
        assertThrows(IOException.class, invalidParser::start);
    }

//    Tests for getFields() method
    @Test
    void getFields_returnsEmptyMapWhenNoFieldsPresent() throws IOException {
        // Create a temporary Java file with no fields
        Path tempFilePath = Files.createTempFile("TempClass", ".java");
        Files.write(tempFilePath, """
            public class TempClass {
            }
        """.getBytes());

        // Parse the temporary file
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        combinedTypeSolver.add(new JavaParserTypeSolver(tempFilePath.getParent()));
        JavaSymbolSolver symbolResolver = new JavaSymbolSolver(combinedTypeSolver);
        ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(symbolResolver);
        JavaParser javaParser = new JavaParser(parserConfiguration);

        FileInputStream in = new FileInputStream(tempFilePath.toFile());
        CompilationUnit cu = javaParser.parse(in).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));

        // Use the parsed CompilationUnit
        Map<String, Type> fields = parser.getFields(cu);
        assertTrue(fields.isEmpty());

        // Clean up the temporary file
        Files.delete(tempFilePath);
    }

    @Test
    void getFields_returnsMapWithFieldNamesAndTypes() throws FileNotFoundException {
        Path testFilePath = Paths.get("src/test/resources/test-java-files/Test.java");

        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        combinedTypeSolver.add(new JavaParserTypeSolver(basePath));
        JavaSymbolSolver symbolResolver = new JavaSymbolSolver(combinedTypeSolver);
        ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(symbolResolver);
        JavaParser javaParser = new JavaParser(parserConfiguration);

        FileInputStream in = new FileInputStream(testFilePath.toFile());
        CompilationUnit cu = javaParser.parse(in).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));

        Map<String, Type> fields = parser.getFields(cu);
        assertFalse(fields.isEmpty());
        assertEquals(fields.size(), 3);
        assertTrue(fields.containsKey("id"));
        assertEquals(fields.get("id").asString(), "Long");
        assertTrue(fields.containsKey("name"));
        assertEquals(fields.get("name").asString(), "String");
        assertTrue(fields.containsKey("description"));
        assertEquals(fields.get("description").asString(), "String");
    }
}
