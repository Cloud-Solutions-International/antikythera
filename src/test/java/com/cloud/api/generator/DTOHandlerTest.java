package com.cloud.api.generator;

import com.cloud.api.configurations.Settings;
import com.cloud.api.constants.Constants;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.NoSuchElementException;
import java.util.Optional;

import static com.cloud.api.generator.ClassProcessor.basePackage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        basePath = Settings.getProperty(Constants.BASE_PATH).toString();
        controllers = Settings.getProperty(Constants.CONTROLLERS).toString();
        outputPath = Settings.getProperty(Constants.OUTPUT_PATH).toString();

        classProcessor = new ClassProcessor();
        handler = new DTOHandler();
        typeCollector = handler.new TypeCollector();
        handler.cu = new CompilationUnit();
    }

    // -------------------- handleStaticImports -------------------- //
    @Test
    void handleStaticImportsAddsStaticImportsToDependencies() {
        NodeList<ImportDeclaration> imports = new NodeList<>();
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
        NodeList<ImportDeclaration> imports = new NodeList<>();
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
        NodeList<ImportDeclaration> imports = new NodeList<>();
        String nonStaticImport =  String.format("%s.NonStaticImport", basePackage);
        imports.add(new ImportDeclaration(nonStaticImport, false, false));

        handler.handleStaticImports(imports);

        assertEquals(0, handler.dependencies.size());
        assertFalse(handler.dependencies.contains(nonStaticImport));
    }

    @Test
    void handleStaticImportsIgnoresImportsNotStartingWithBasePackage() {
        NodeList<ImportDeclaration> imports = new NodeList<>();
        String otherPackageStaticImport = "com.otherpackage.StaticImport";
        imports.add(new ImportDeclaration(otherPackageStaticImport, true, false));

        handler.handleStaticImports(imports);

        assertEquals(0, handler.dependencies.size());
        assertFalse(classProcessor.dependencies.contains(otherPackageStaticImport));
    }

    @Test
    void handleStaticImportsAddAllImportsToDependencies() {
        NodeList<ImportDeclaration> imports = new NodeList<>();
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

    // -------------------- addLombok -------------------- //

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
        handler.setCompilationUnit(cu);
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
        handler.setCompilationUnit(cu);
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
        handler.setCompilationUnit(cu);
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
        handler.setCompilationUnit(cu);
        ClassOrInterfaceDeclaration classDecl = cu.getClassByName("TempClass").orElseThrow();
        classDecl.addField("String", "field");

        handler.addLombok(classDecl, annotations);

        assertTrue(annotations.stream().anyMatch(a -> a.getNameAsString().equals("Getter")));
        assertTrue(annotations.stream().anyMatch(a -> a.getNameAsString().equals("NoArgsConstructor")));
        assertTrue(annotations.stream().anyMatch(a -> a.getNameAsString().equals("AllArgsConstructor")));
        assertTrue(annotations.stream().anyMatch(a -> a.getNameAsString().equals("Setter")));
    }

    @Test
    void capitalizeConvertsFirstCharacterToUpperCase() {
        assertEquals("Hello", DTOHandler.capitalize("hello"));
    }

    @Test
    void capitalizeDoesNotChangeAlreadyCapitalizedString() {
        assertEquals("Hello", DTOHandler.capitalize("Hello"));
    }

    @Test
    void capitalizeHandlesEmptyString() {
        assertThrows(StringIndexOutOfBoundsException.class, () -> DTOHandler.capitalize(""));
    }

    // -------------------- generateRandomValue -------------------- //
    @Test
    void generateRandomValueAddSetterForBooleanFieldWithIsPrefix() {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TempClass {}"));

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "boolean"), "isActive"));

        handler.method = new MethodDeclaration();

        MethodCallExpr setter = DTOHandler.generateRandomValue(field, handler.getCompilationUnit());
        assertNotNull(setter);
        assertTrue(setter.toString().contains("setActive(true)"));
    }

    @Test
    void generateRandomValueAddSetterForBooleanField()  {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TempClass {}"));

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "Boolean"), "isActive"));

        handler.method = new MethodDeclaration();

        MethodCallExpr setter = DTOHandler.generateRandomValue(field, handler.getCompilationUnit());
        assertNotNull(setter);
        assertTrue(setter.toString().contains("setIsActive(true)"));
    }

    @Test
    void generateRandomValueAddSetterForCharacterField()  {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TempClass {}"));

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "Character"), "initial"));

        handler.method = new MethodDeclaration();

        MethodCallExpr setter = DTOHandler.generateRandomValue(field, handler.getCompilationUnit());
        assertNotNull(setter);
        assertTrue(setter.toString().contains("setInitial('A')"));
    }

    @Test
    void generateRandomValueAddSetterForDateField() throws IOException {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TempClass {}"));

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "Date"), "createdDate"));

        handler.method = new MethodDeclaration();

        MethodCallExpr setter = DTOHandler.generateRandomValue(field, handler.getCompilationUnit());
        assertNotNull(setter);
        assertTrue(setter.toString().contains("setCreatedDate(new Date())"));
    }

    @Test
    void generateRandomValueAddSetterForIntegerField() throws IOException {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TempClass {}"));

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "Integer"), "count"));

        handler.method = new MethodDeclaration();

        MethodCallExpr setter = DTOHandler.generateRandomValue(field, handler.getCompilationUnit());
        assertNotNull(setter);
        assertTrue(setter.toString().contains("setCount(0)"));
    }

    @Test
    void generateRandomValueAddSetterForDoubleField() throws IOException {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TempClass {}"));

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "Double"), "value"));

        handler.method = new MethodDeclaration();

        MethodCallExpr setter = DTOHandler.generateRandomValue(field, handler.getCompilationUnit());
        assertNotNull(setter);
        assertTrue(setter.toString().contains("setValue(0.0)"));
    }

    @Test
    void generateRandomValueAddSetterForPrimitiveDoubleField() throws IOException {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TempClass {}"));

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "double"), "value"));

        handler.method = new MethodDeclaration();

        MethodCallExpr setter = DTOHandler.generateRandomValue(field, handler.getCompilationUnit());
        assertNotNull(setter);
        assertTrue(setter.toString().contains("setValue(0.0)"));
    }

    @Test
    void generateRandomValueAddSetterForFloatField() throws IOException {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TempClass {}"));

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "Float"), "value"));

        handler.method = new MethodDeclaration();

        MethodCallExpr setter = DTOHandler.generateRandomValue(field, handler.getCompilationUnit());
        assertNotNull(setter);
        assertTrue(setter.toString().contains("setValue(0.0f)"));
    }

    @Test
    void generateRandomValueAddSetterForPrimitiveFloatField() throws IOException {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TempClass {}"));

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "float"), "value"));

        handler.method = new MethodDeclaration();

        MethodCallExpr setter = DTOHandler.generateRandomValue(field, handler.getCompilationUnit());
        assertNotNull(setter);
        assertTrue(setter.toString().contains("setValue(0.0f)"));
    }

    @Test
    void generateRandomValueAddSetterForByteField() throws IOException {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TempClass {}"));

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "Byte"), "value"));

        handler.method = new MethodDeclaration();

        MethodCallExpr setter = DTOHandler.generateRandomValue(field, handler.getCompilationUnit());
        assertNotNull(setter);
        assertTrue(setter.toString().contains("setValue((byte) 0)"));
    }

    @Test
    void generateRandomValueAddSetterForStringField() throws IOException {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TempClass {}"));

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "String"), "message"));

        handler.method = new MethodDeclaration();

        MethodCallExpr setter = DTOHandler.generateRandomValue(field, handler.getCompilationUnit());
        assertNotNull(setter);
        assertTrue(setter.toString().contains("setMessage(\"Hello world\")"));
    }

    @Test
    void generateRandomValueAddSetterForListField() throws IOException {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TempClass {}"));

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "List"), "items"));

        handler.method = new MethodDeclaration();

        MethodCallExpr setter = DTOHandler.generateRandomValue(field, handler.getCompilationUnit());
        assertNotNull(setter);
        assertTrue(setter.toString().contains("setItems(List.of())"));
    }

    @Test
    void generateRandomValueAddSetterForPrimitiveLongField() throws IOException {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TempClass {}"));

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "long"), "value"));

        handler.method = new MethodDeclaration();

        MethodCallExpr setter = DTOHandler.generateRandomValue(field, handler.getCompilationUnit());
        assertNotNull(setter);
        assertTrue(setter.toString().contains("setValue(0L)"));
    }

    @Test
    void generateRandomValueAddSetterForLongField() throws IOException {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TempClass {}"));

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "Long"), "value"));

        handler.method = new MethodDeclaration();

        MethodCallExpr setter = DTOHandler.generateRandomValue(field, handler.getCompilationUnit());
        assertNotNull(setter);
        assertTrue(setter.toString().contains("setValue(0L)"));
    }

    @Test
    void generateRandomValueAddSetterForMapField() throws IOException {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TempClass {}"));

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "Map"), "testMap"));

        handler.method = new MethodDeclaration();

        MethodCallExpr setter = DTOHandler.generateRandomValue(field, handler.getCompilationUnit());
        assertNotNull(setter);
        assertTrue(setter.toString().contains("setTestMap(Map.of())"));
    }

    @Test
    void generateRandomValueAddSetterForSetField() throws IOException {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TempClass {}"));

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "Set"), "testSet"));

        handler.method = new MethodDeclaration();

        MethodCallExpr setter = DTOHandler.generateRandomValue(field, handler.getCompilationUnit());
        assertNotNull(setter);
        assertTrue(setter.toString().contains("setTestSet(Set.of())"));
    }

    @Test
    void generateRandomValueAddSetterForUUIDField(){
        handler.setCompilationUnit(StaticJavaParser.parse("public class TempClass {}"));

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "UUID"), "testUUID"));

        handler.method = new MethodDeclaration();

        MethodCallExpr setter = DTOHandler.generateRandomValue(field, handler.getCompilationUnit());
        assertNotNull(setter);
        assertTrue(setter.toString().contains("setTestUUID(UUID.randomUUID())"));
    }

    @Test
    void generateRandomValueAddSetterForLocalDateField() throws IOException {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TempClass {}"));

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "LocalDate"), "date"));

        handler.method = new MethodDeclaration();

        MethodCallExpr setter = DTOHandler.generateRandomValue(field, handler.getCompilationUnit());
        assertNotNull(setter);
        assertTrue(setter.toString().contains("setDate(LocalDate.now())"));
    }

    @Test
    void generateRandomValueAddSetterForLocalDateTimeField() throws IOException {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TempClass {}"));

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "LocalDateTime"), "dateTime"));

        handler.method = new MethodDeclaration();

        MethodCallExpr setter = DTOHandler.generateRandomValue(field, handler.getCompilationUnit());
        assertNotNull(setter);
        assertTrue(setter.toString().contains("setDateTime(LocalDateTime.now())"));
    }

    @Test
    void generateRandomValueAddSetterForShortField() throws IOException {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TempClass {}"));

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "Short"), "shortValue"));

        handler.method = new MethodDeclaration();

        MethodCallExpr setter = DTOHandler.generateRandomValue(field, handler.getCompilationUnit());
        assertNotNull(setter);
        assertTrue(setter.toString().contains("setShortValue((short) 0)"));
    }

    @Test
    void generateRandomValueReturnsNullForGenericTypeField() throws IOException {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TempClass {}"));

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "T"), "genericField"));

        handler.method = new MethodDeclaration();

        MethodCallExpr setter = DTOHandler.generateRandomValue(field, handler.getCompilationUnit());
        assertNotNull(setter);
        assertTrue(setter.toString().contains("setGenericField(null)"));
    }

    @Test
    void generateRandomValueAddSetterForBigDecimalField() {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TempClass {}"));

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "BigDecimal"), "amount"));

        handler.method = new MethodDeclaration();

        MethodCallExpr setter = DTOHandler.generateRandomValue(field, handler.getCompilationUnit());
        assertNotNull(setter);
        assertTrue(setter.toString().contains("setAmount(BigDecimal.ZERO)"));
    }

    @Test
    void generateRandomValueReturnsNullForEnumTypeField() throws IOException {
        Path tempFilePath = Files.createTempFile("TempClass", ".java");
        Files.write(tempFilePath, """
    public class TempClass {
        enum MyEnum { VALUE1, VALUE2 }
    }
    """.getBytes());

        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        combinedTypeSolver.add(new JavaParserTypeSolver(tempFilePath.getParent()));

        JavaSymbolSolver symbolResolver = new JavaSymbolSolver(combinedTypeSolver);
        ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(symbolResolver);
        JavaParser javaParser = new JavaParser(parserConfiguration);

        FileInputStream in = new FileInputStream(tempFilePath.toFile());
        CompilationUnit cu = javaParser.parse(in).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));
        handler.setCompilationUnit(cu);

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "MyEnum"), "enumField"));
        ClassOrInterfaceDeclaration classDecl = cu.getClassByName("TempClass").orElseThrow();
        classDecl.addMember(field);

        handler.method = new MethodDeclaration();

        MethodCallExpr setter = DTOHandler.generateRandomValue(field, cu);
        assertNull(setter);
    }

    @Test
    void extractCompleTypes() {

        assertTrue(classProcessor.dependencies.isEmpty());
        Optional<CompilationUnit> result = handler.javaParser.parse("class Test{}").getResult();
        if (result.isPresent()) {
            CompilationUnit cu = result.get();
            FieldDeclaration field = StaticJavaParser
                    .parseBodyDeclaration("Map<String, SimpleDTO> someList;")
                    .asFieldDeclaration();
            cu.getTypes().get(0).addMember(field);
            cu.addImport("com.csi.dto.SimpleDTO");
            handler.setCompilationUnit(cu);

            classProcessor.solveTypeDependencies(field.getElementType(), cu);
            assertEquals(classProcessor.dependencies.size(), 1);

            // calling the same thing again should not change anything.
            classProcessor.solveTypeDependencies(field.getElementType(), cu);
            assertEquals(classProcessor.dependencies.size(), 1);
        }
    }
}
