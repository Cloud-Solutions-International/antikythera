package com.cloud.api.generator;

import com.cloud.api.configurations.Settings;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.cloud.api.generator.ClassProcessor.*;
import static org.junit.jupiter.api.Assertions.*;

public class DTOHandlerTest {
    private ClassProcessor classProcessor;
    private DTOHandler handler;
    private DTOHandler.TypeCollector typeCollector;

    private static String basePath;
    private static String controllers;
    private static String outputPath;

    @BeforeEach
    void loadConfigMapBeforeEach() throws IOException {
        Settings.loadConfigMap();
        basePath = Settings.getProperty("BASE_PATH");
        controllers = Settings.getProperty("CONTROLLERS");
        outputPath = Settings.getProperty("OUTPUT_PATH");

        classProcessor = new ClassProcessor();
        handler = new DTOHandler();
        typeCollector = handler.new TypeCollector();
    }

    // -------- addLombok -------- //

    @Test
    void addLombokAddsAllAnnotationsForSmallClass() throws IOException {
        NodeList<AnnotationExpr> annotations = new NodeList<>();
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
        ClassOrInterfaceDeclaration classDecl = cu.getClassByName("TempClass").orElseThrow();

        for (int i = 0; i < 255; i++) {
            classDecl.addField("String", "field" + i);
        }

        handler.addLombok(classDecl, annotations);

        assertTrue(annotations.stream().anyMatch(a -> a.getNameAsString().equals("Getter")));
        assertTrue(annotations.stream().anyMatch(a -> a.getNameAsString().equals("NoArgsConstructor")));
        assertTrue(annotations.stream().anyMatch(a -> a.getNameAsString().equals("AllArgsConstructor")));
        assertTrue(annotations.stream().anyMatch(a -> a.getNameAsString().equals("Setter")));
    }

    @Test
    void addLombokAddsLimitedAnnotationsForLargeClass() throws IOException {
        NodeList<AnnotationExpr> annotations = new NodeList<>();
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
        ClassOrInterfaceDeclaration classDecl = cu.getClassByName("TempClass").orElseThrow();

        for (int i = 0; i < 256; i++) {
            classDecl.addField("String", "field" + i);
        }

        handler.addLombok(classDecl, annotations);

        assertTrue(annotations.stream().anyMatch(a -> a.getNameAsString().equals("Getter")));
        assertTrue(annotations.stream().anyMatch(a -> a.getNameAsString().equals("NoArgsConstructor")));
        assertFalse(annotations.stream().anyMatch(a -> a.getNameAsString().equals("AllArgsConstructor")));
        assertTrue(annotations.stream().anyMatch(a -> a.getNameAsString().equals("Setter")));
    }

    @Test
    void addLombokDoesNotAddAnnotationsForStaticFinalFieldsOnly() throws IOException {
        NodeList<AnnotationExpr> annotations = new NodeList<>();
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
        ClassOrInterfaceDeclaration classDecl = cu.getClassByName("TempClass").orElseThrow();
        classDecl.addField("String", "field", Modifier.Keyword.STATIC, Modifier.Keyword.FINAL);

        handler.addLombok(classDecl, annotations);

        assertTrue(annotations.isEmpty());
    }

    @Test
    void addLombokAddsAnnotationsForNonStaticNonFinalFields() throws IOException {
        NodeList<AnnotationExpr> annotations = new NodeList<>();
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
        ClassOrInterfaceDeclaration classDecl = cu.getClassByName("TempClass").orElseThrow();
        classDecl.addField("String", "field");

        handler.addLombok(classDecl, annotations);

        assertTrue(annotations.stream().anyMatch(a -> a.getNameAsString().equals("Getter")));
        assertTrue(annotations.stream().anyMatch(a -> a.getNameAsString().equals("NoArgsConstructor")));
        assertTrue(annotations.stream().anyMatch(a -> a.getNameAsString().equals("AllArgsConstructor")));
        assertTrue(annotations.stream().anyMatch(a -> a.getNameAsString().equals("Setter")));
    }

    // ======== TypeCollector ======== //

    // -------- capitalize -------- //
    @Test
    void capitalizeConvertsFirstCharacterToUpperCase() {
        assertEquals("Hello", typeCollector.capitalize("hello"));
    }

    @Test
    void capitalizeDoesNotChangeAlreadyCapitalizedString() {
        assertEquals("Hello", typeCollector.capitalize("Hello"));
    }

    @Test
    void capitalizeHandlesEmptyString() {
        assertThrows(StringIndexOutOfBoundsException.class, () -> typeCollector.capitalize(""));
    }

}
