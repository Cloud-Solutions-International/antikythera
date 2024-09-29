package com.cloud.api.generator;

import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import com.github.javaparser.ast.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ClassProcessorTest {

    private NodeList<ImportDeclaration> imports;
    private ClassProcessor classProcessor;
    private DTOHandler handler;

    @BeforeEach
    void setUp() throws IOException {
        Settings.loadConfigMap();
        imports = new NodeList<>();
        imports.add(new ImportDeclaration("com.example.SomeClass", false, false));
        imports.add(new ImportDeclaration("java.util.List", false, false));
        imports.add(new ImportDeclaration("org.springframework.data.domain.Page", false, false));
        imports.add(new ImportDeclaration("com.otherpackage.OtherClass", false, false));
        classProcessor = new ClassProcessor();
        handler = mock(DTOHandler.class);
        AntikytheraRunTime.reset();
    }


    @Test
    void copyDependencies_doesNotCopySpringDataDomain() throws IOException {
        classProcessor.copyDependencies("org.springframework.data.domain.Page");
        assertNotNull(AntikytheraRunTime.getCompilationUnit("org.springframework.data.domain.Page"));
        verify(handler, never()).copyDTO(anyString());
    }

    @Test
    void copyDependencies_doesNotCopyAlreadyResolvedDependency() throws IOException {
        ClassProcessor.basePackage = "com.example";
        ClassProcessor.copied.add("com.example.NewClass");

        classProcessor.copyDependencies("com.example.NewClass");
        verify(handler, never()).copyDTO(anyString());
    }

    @Test
    void copyDependencies_doesNotCopyExternals() throws IOException {
        ClassProcessor.basePackage = "com.example";
        classProcessor.externalDependencies.add("com.example.NewClass");
        classProcessor.copyDependencies("com.example.NewClass");
        verify(handler, never()).copyDTO(anyString());
    }

    @Test
    void copyDependencies_doesNotCopyNonBasePackageDependency() throws IOException {
        ClassProcessor.basePackage = "com.example";
        classProcessor.copyDependencies("com.otherpackage.OtherClass");
        assertNotNull(AntikytheraRunTime.getCompilationUnit("com.otherpackage.OtherClass"));
        verify(handler, never()).copyDTO(anyString());
    }


    @Test
    void findImport_findsMatchingImport() {
        CompilationUnit cu = mock(CompilationUnit.class);
        ImportDeclaration importDeclaration = mock(ImportDeclaration.class);
        NodeList<ImportDeclaration> imp = new NodeList<>();
        imp.add(importDeclaration);

        when(cu.getImports()).thenReturn(imp);
        when(importDeclaration.getNameAsString()).thenReturn("com.example.SomeClass");

        boolean result = classProcessor.findImport(cu, "SomeClass");

        assertTrue(result);
        assertTrue(classProcessor.dependencies.contains("com.example.SomeClass"));
    }

    @Test
    void findImport_doesNotFindNonMatchingImport() {
        CompilationUnit cu = mock(CompilationUnit.class);
        ImportDeclaration importDeclaration = mock(ImportDeclaration.class);
        NodeList<ImportDeclaration> imp = new NodeList<>();
        imp.add(importDeclaration);

        when(cu.getImports()).thenReturn(imp);
        when(importDeclaration.getNameAsString()).thenReturn("com.example.OtherClass");

        boolean result = classProcessor.findImport(cu, "SomeClass");

        assertFalse(result);
        assertFalse(classProcessor.dependencies.contains("com.example.OtherClass"));
    }

    @Test
    void findImport_handlesEmptyImports() {
        CompilationUnit cu = mock(CompilationUnit.class);
        NodeList<ImportDeclaration> imp = new NodeList<>();

        when(cu.getImports()).thenReturn(imp);

        boolean result = classProcessor.findImport(cu, "SomeClass");

        assertFalse(result);
    }

}
