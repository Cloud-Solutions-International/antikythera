package com.cloud.api.generator;

import com.cloud.api.configurations.Settings;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;

import static com.cloud.api.generator.ClassProcessor.*;
import static org.junit.jupiter.api.Assertions.*;

public class DTOHandlerTest {
    private ClassProcessor classProcessor;
    private DTOHandler handler;
    private static String basePath;
    private static String controllers;
    private static String outputPath;
    private NodeList<ImportDeclaration> imports;

    @BeforeEach
    void loadConfigMapBeforeEach() throws IOException {
        Settings.loadConfigMap();
        basePath = Settings.getProperty("BASE_PATH");
        controllers = Settings.getProperty("CONTROLLERS");
        outputPath = Settings.getProperty("OUTPUT_PATH");

        classProcessor = new ClassProcessor();
        handler = new DTOHandler();
        imports = new NodeList<>();
    }

    @Test
    void handleStaticImportsAddsStaticImportsToDependencies() {
        String util = String.format("%s.util", basePackage);
        String staticImport = String.format("%s.staticImport", util);
        imports.add(new ImportDeclaration(staticImport, true, false));

        handler.handleStaticImports(imports);

        assertEquals(1, handler.dependencies.size());
        for (String dependency : handler.dependencies) {
            assertTrue(dependency.startsWith(basePackage));
            assertEquals(util, dependency);
        }
    }

    @Test
    void handleStaticImportsAddsWildcardStaticImportsToDependencies() {
        String wildcardStaticImport = String.format("%s.staticImport", basePackage);
        imports.add(new ImportDeclaration(wildcardStaticImport, true, true));

        handler.handleStaticImports(imports);

        assertEquals(1, handler.dependencies.size());
        for (String dependency : handler.dependencies) {
            assertTrue(dependency.startsWith(basePackage));
            assertEquals(wildcardStaticImport, dependency);
        }
    }

    @Test
    void handleStaticImportsIgnoresNonStaticImports() {
        String nonStaticImport =  String.format("%s.NonStaticImport", basePackage);
        imports.add(new ImportDeclaration(nonStaticImport, false, false));

        handler.handleStaticImports(imports);

        assertEquals(0, handler.dependencies.size());
        assertFalse(handler.dependencies.contains(nonStaticImport));
    }

    @Test
    void handleStaticImportsIgnoresImportsNotStartingWithBasePackage() {
        String otherPackageStaticImport = "com.otherpackage.StaticImport";
        imports.add(new ImportDeclaration(otherPackageStaticImport, true, false));

        handler.handleStaticImports(imports);

        assertEquals(0, handler.dependencies.size());
        assertFalse(classProcessor.dependencies.contains(otherPackageStaticImport));
    }

    @Test
    void handleStaticImportsAddAllImportsToDependencies() {
        String util = String.format("%s.util", basePackage);
        String staticImport = String.format("%s.staticImport", util);
        imports.add(new ImportDeclaration(staticImport, true, false));

        String wildcardStaticImport = String.format("%s.staticImport", basePackage);
        imports.add(new ImportDeclaration(wildcardStaticImport, true, true));

        String nonStaticImport =  String.format("%s.NonStaticImport", basePackage);
        imports.add(new ImportDeclaration(nonStaticImport, false, false));

        String otherPackageStaticImport = "com.otherpackage.StaticImport";
        imports.add(new ImportDeclaration(otherPackageStaticImport, true, false));

        handler.handleStaticImports(imports);

        assertEquals(2, handler.dependencies.size());
        for (String dependency : handler.dependencies) {
            assertTrue(dependency.startsWith(basePackage));
            assertTrue(dependency.equals(util) || dependency.equals(wildcardStaticImport));
            assertFalse(dependency.equals(nonStaticImport) || dependency.equals(otherPackageStaticImport));
        }
    }
}
