package com.cloud.api.generator;

import com.cloud.api.generator.ClassProcessor;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.Printer;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class ClassProcessorTest {

    private NodeList<ImportDeclaration> imports;
    private ClassProcessor classProcessor;
    private DTOHandler handler;

    @BeforeEach
    void setUp(){
        imports = new NodeList<>();
        imports.add(new ImportDeclaration("com.example.SomeClass", false, false));
        imports.add(new ImportDeclaration("java.util.List", false, false));
        imports.add(new ImportDeclaration("org.springframework.data.domain.Page", false, false));
        imports.add(new ImportDeclaration("com.otherpackage.OtherClass", false, false));
//        ClassProcessor.loadConfigMap(); // Ensure properties are loaded
        classProcessor = new ClassProcessor();
        handler = mock(DTOHandler.class);
    }


    @Test
    void removeUnwantedImports_removesNonMatchingImports() {
        ClassProcessor.basePackage = "com.example";
        ClassProcessor.removeUnwantedImports(imports);
        assertEquals(3, imports.size());
        assertEquals("com.example.SomeClass", imports.get(0).getNameAsString());
        assertEquals("java.util.List", imports.get(1).getNameAsString());
        assertEquals("org.springframework.data.domain.Page", imports.get(2).getNameAsString());
    }

    @Test
    void removeUnwantedImports_keepsOnlyMatchingImports() {
        ClassProcessor.basePackage = "com.example";
        imports.add(new ImportDeclaration("java.util.ArrayList", false, false));
        ClassProcessor.removeUnwantedImports(imports);
        assertEquals(4, imports.size());
        assertEquals("com.example.SomeClass", imports.get(0).getNameAsString());
        assertEquals("java.util.List", imports.get(1).getNameAsString());
        assertEquals("org.springframework.data.domain.Page", imports.get(2).getNameAsString());
        assertEquals("java.util.ArrayList", imports.get(3).getNameAsString());
    }

    @Test
    void removeUnwantedImports_removesAllWhenNoMatch() {
        ClassProcessor.basePackage = "com.nonexistent";
        ClassProcessor.removeUnwantedImports(imports);
        assertEquals(2, imports.size());
        assertEquals("java.util.List", imports.get(0).getNameAsString());
        assertEquals("org.springframework.data.domain.Page", imports.get(1).getNameAsString());
    }

    @Test
    void copyDependencies_doesNotCopySpringDataDomain() throws IOException {
        ClassProcessor.resolved.clear();
        classProcessor.copyDependencies("org.springframework.data.domain.Page");
        assertFalse(ClassProcessor.resolved.contains("org.springframework.data.domain.Page"));
        verify(handler, never()).copyDTO(anyString());
    }

    @Test
    void copyDependencies_doesNotCopyAlreadyResolvedDependency() throws IOException {
        ClassProcessor.resolved.clear();
        ClassProcessor.basePackage = "com.example";
        ClassProcessor.resolved.add("com.example.NewClass");
        classProcessor.copyDependencies("com.example.NewClass");
        verify(handler, never()).copyDTO(anyString());
    }

    @Test
    void copyDependencies_doesNotCopyNonBasePackageDependency() throws IOException {
        ClassProcessor.resolved.clear();
        ClassProcessor.basePackage = "com.example";
        classProcessor.copyDependencies("com.otherpackage.OtherClass");
        assertFalse(ClassProcessor.resolved.contains("com.otherpackage.OtherClass"));
        verify(handler, never()).copyDTO(anyString());
    }


    @Test
    void findImport_findsMatchingImport() {
        CompilationUnit cu = mock(CompilationUnit.class);
        ImportDeclaration importDeclaration = mock(ImportDeclaration.class);
        NodeList<ImportDeclaration> imports = new NodeList<>();
        imports.add(importDeclaration);

        when(cu.getImports()).thenReturn(imports);
        when(importDeclaration.getNameAsString()).thenReturn("com.example.SomeClass");

        boolean result = classProcessor.findImport(cu, "SomeClass");

        assertTrue(result);
        assertTrue(classProcessor.dependencies.contains("com.example.SomeClass"));
    }

    @Test
    void findImport_doesNotFindNonMatchingImport() {
        CompilationUnit cu = mock(CompilationUnit.class);
        ImportDeclaration importDeclaration = mock(ImportDeclaration.class);
        NodeList<ImportDeclaration> imports = new NodeList<>();
        imports.add(importDeclaration);

        when(cu.getImports()).thenReturn(imports);
        when(importDeclaration.getNameAsString()).thenReturn("com.example.OtherClass");

        boolean result = classProcessor.findImport(cu, "SomeClass");

        assertFalse(result);
        assertFalse(classProcessor.dependencies.contains("com.example.OtherClass"));
    }

    @Test
    void findImport_handlesEmptyImports() {
        CompilationUnit cu = mock(CompilationUnit.class);
        NodeList<ImportDeclaration> imports = new NodeList<>();

        when(cu.getImports()).thenReturn(imports);

        boolean result = classProcessor.findImport(cu, "SomeClass");

        assertFalse(result);
    }





}