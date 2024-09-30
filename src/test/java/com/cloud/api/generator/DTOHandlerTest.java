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

    // -------------------- capitalize -------------------- //
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

    // -------------------- solveTypes -------------------- //
    @Test
    void solveTypesAddsLombokAnnotationsToClass() throws IOException {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TempClass {}"));

        ClassOrInterfaceDeclaration classDecl = new ClassOrInterfaceDeclaration();
        classDecl.setName("TempClass");
        classDecl.addField("String", "field");

        handler.cu.addType(classDecl);
        handler.solveTypes();

        assertTrue(classDecl.getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals("Getter")));
        assertTrue(classDecl.getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals("Setter")));
    }

    @Test
    void solveTypesRemovesConstructorsAndMethods() throws IOException {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TempClass {}"));

        ClassOrInterfaceDeclaration classDecl = new ClassOrInterfaceDeclaration();
        classDecl.setName("TempClass");
        classDecl.addConstructor();
        classDecl.addMethod("someMethod");

        handler.cu.addType(classDecl);
        handler.solveTypes();

        assertTrue(classDecl.getConstructors().isEmpty());
        assertTrue(classDecl.getMethods().isEmpty());
    }

    @Test
    void solveTypesAddsParentClassToDependencies() throws IOException {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(Paths.get("src/main/java")));

        ParserConfiguration parserConfiguration = new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(typeSolver));
        StaticJavaParser.setConfiguration(parserConfiguration);

        CompilationUnit cu = StaticJavaParser.parse("package com.cloud.api.generator;\n" +
                "public class ParentClass {}\n" +
                "public class TempClass extends ParentClass {}");

        handler.setCompilationUnit(cu);
        handler.solveTypes();

        assertTrue(handler.dependencies.contains("com.cloud.api.generator.ParentClass"));
    }

    @Test
    void solveTypesIgnoresJavaLangParentClass() throws IOException {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(Paths.get("src/main/java")));

        ParserConfiguration parserConfiguration = new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(typeSolver));
        StaticJavaParser.setConfiguration(parserConfiguration);

        CompilationUnit cu = StaticJavaParser.parse("package com.cloud.api.generator;\n" +
                "public class TempClass extends java.lang.Object {}");


        ClassOrInterfaceDeclaration classDecl = new ClassOrInterfaceDeclaration();
        classDecl.setName("TempClass");
        classDecl.addExtendedType("java.lang.Object");

        handler.setCompilationUnit(cu);
        handler.solveTypes();

        assertFalse(handler.dependencies.contains("java.lang.Object"));
    }

    @Test
    void solveTypesClearsImplementedInterfaces() throws IOException {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TempClass implements SomeInterface {}"));

        ClassOrInterfaceDeclaration classDecl = new ClassOrInterfaceDeclaration();
        classDecl.setName("TempClass");
        classDecl.addImplementedType("SomeInterface");

        handler.cu.addType(classDecl);
        handler.solveTypes();

        assertTrue(classDecl.getImplementedTypes().isEmpty());
    }

    @Test
    void solveTypesHandlesUnresolvedParentClass() throws IOException {
        CompilationUnit cu = StaticJavaParser.parse("public class TempClass extends UnresolvedClass {}");
        handler.setCompilationUnit(cu);

        ClassOrInterfaceDeclaration classDecl = cu.getClassByName("TempClass").orElseThrow(() -> new IllegalStateException("Class not found"));
        classDecl.addExtendedType("UnresolvedClass");

        assertThrows(RuntimeException.class, () -> handler.solveTypes());
    }

    @Test
    void solveTypesAddsLombokAnnotationsToEnum() throws IOException {
        handler.setCompilationUnit(StaticJavaParser.parse("public enum TempEnum { VALUE1, VALUE2 }"));

        EnumDeclaration enumDecl = new EnumDeclaration();
        enumDecl.setName("TempEnum");
        enumDecl.addEnumConstant("VALUE1");
        enumDecl.addEnumConstant("VALUE2");
        enumDecl.addAnnotation("AllArgsConstructor");

        handler.cu.addType(enumDecl);
        handler.solveTypes();

        assertTrue(handler.cu.getImports().stream().anyMatch(i -> i.getNameAsString().equals("lombok.AllArgsConstructor")));
    }

    @Test
    void solveTypesAddsGetterAnnotationToEnum() throws IOException {
        CompilationUnit cu = StaticJavaParser.parse("public enum TempEnum { VALUE1, VALUE2 }");
        handler.setCompilationUnit(cu);

        EnumDeclaration enumDecl = cu.getEnumByName("TempEnum").orElseThrow(() -> new IllegalStateException("Enum not found"));
        enumDecl.addAnnotation("Getter");

        handler.solveTypes();

        assertTrue(cu.getImports().stream().anyMatch(importDecl -> importDecl.getNameAsString().equals("lombok.Getter")));
    }

    @Test
    void solveTypesDoesNotAddGetterAnnotationIfAlreadyPresent() throws IOException {
        CompilationUnit cu = StaticJavaParser.parse("import lombok.Getter; public enum TempEnum { VALUE1, VALUE2 }");
        handler.setCompilationUnit(cu);

        EnumDeclaration enumDecl = cu.getEnumByName("TempEnum").orElseThrow(() -> new IllegalStateException("Enum not found"));
        enumDecl.addAnnotation("Getter");

        handler.solveTypes();

        long getterImports = cu.getImports().stream().filter(importDecl -> importDecl.getNameAsString().equals("lombok.Getter")).count();
        assertEquals(1, getterImports);
    }

    @Test
    void solveTypesHandlesEnumWithoutAnnotations() throws IOException {
        CompilationUnit cu = StaticJavaParser.parse("public enum TempEnum { VALUE1, VALUE2 }");
        handler.setCompilationUnit(cu);

        handler.solveTypes();

        assertFalse(cu.getImports().stream().anyMatch(importDecl -> importDecl.getNameAsString().equals("lombok.Getter")));
    }

    // -------------------- generateGetter -------------------- //
    @Test
    void generateGetterCreatesPublicMethod() {
        ClassOrInterfaceDeclaration classDeclaration = handler.cu.addClass("TestClass");

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "String"), "name"));
        field.setParentNode(classDeclaration);

        classDeclaration.addMember(field);

        typeCollector.generateGetter(field, "getName");

        MethodDeclaration getter = (MethodDeclaration) handler.cu.getType(0).getMembers().stream()
                .filter(member -> member instanceof MethodDeclaration)
                .filter(member -> ((MethodDeclaration) member).getNameAsString().equals("getName"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Getter method not found"));

        assertTrue(getter.isPublic(), "Generated getter should be public.");
    }

    @Test
    void generateGetterReturnsFieldValue() {
        ClassOrInterfaceDeclaration classDeclaration = handler.cu.addClass("TestClass");

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "String"), "name"));
        field.setParentNode(classDeclaration);

        classDeclaration.addMember(field);

        typeCollector.generateGetter(field, "getName");

        MethodDeclaration getter = (MethodDeclaration) handler.cu.getType(0).getMembers().stream()
                .filter(member -> member instanceof MethodDeclaration)
                .filter(member -> ((MethodDeclaration) member).getNameAsString().equals("getName"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Getter method not found"));

        assertTrue(getter.getBody().isPresent(), "Getter should have a method body.");
        assertTrue(getter.getBody().get().getStatements().stream()
                        .anyMatch(stmt -> stmt.toString().contains("return name")),
                "Getter should return the field 'name'.");
    }

    @Test
    void generateGetterHandlesEmptyFieldName() {
        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "String"), "testString"));

        handler.method = new MethodDeclaration();
        assertThrows(NoSuchElementException.class, () -> typeCollector.generateGetter(field, "get"));
    }

    @Test
    void generateGetterHandlesNullField() {
        assertThrows(NullPointerException.class, () -> typeCollector.generateGetter(null, "getName"));
    }

    @Test
    void generateGetterHandlesNullGetterName() {
        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "String"), "name"));

        handler.method = new MethodDeclaration();
        assertThrows(AssertionError.class, () -> typeCollector.generateGetter(field, null));
    }

    // -------------------- generateSetter -------------------- //
    @Test
    void generateSetterCreatesPublicMethod() {
        ClassOrInterfaceDeclaration classDeclaration = handler.cu.addClass("TestClass");

        FieldDeclaration field = StaticJavaParser.parseBodyDeclaration("private int value;").asFieldDeclaration();
        field.setParentNode(classDeclaration);
        classDeclaration.addMember(field);

        typeCollector.generateSetter(field, "setValue");

        MethodDeclaration setter = classDeclaration.getMethodsByName("setValue").get(0);
        assertNotNull(setter, "Setter method should be created.");
        assertTrue(setter.isPublic(), "Setter should be public.");
    }

    @Test
    void generateSetterAssignsFieldValue() {
        ClassOrInterfaceDeclaration classDeclaration = handler.cu.addClass("TestClass");

        FieldDeclaration field = StaticJavaParser.parseBodyDeclaration("private int value;").asFieldDeclaration();
        field.setParentNode(classDeclaration);
        classDeclaration.addMember(field);

        typeCollector.generateSetter(field, "setValue");

        MethodDeclaration setter = classDeclaration.getMethodsByName("setValue").get(0);
        BlockStmt body = setter.getBody().orElseThrow(() -> new AssertionError("Setter body should not be empty"));
        AssignExpr assignExpr = (AssignExpr) body.getStatement(0).asExpressionStmt().getExpression();

        assertEquals("this.value", assignExpr.getTarget().toString(), "Setter should assign to 'this.value'.");
        assertEquals("value", assignExpr.getValue().toString(), "Setter should assign the parameter 'value'.");
    }

    @Test
    void generateSetterHandlesNullField() {
        assertThrows(NullPointerException.class, () -> typeCollector.generateSetter(null, "setValue"));
    }

    @Test
    void generateSetterHandlesNullSetterName() {
        FieldDeclaration field = StaticJavaParser.parseBodyDeclaration("private int value;").asFieldDeclaration();
        assertThrows(AssertionError.class, () -> typeCollector.generateSetter(field, null));
    }

    @Test
    void generateSetterHandlesEmptySetterName() {
        FieldDeclaration field = StaticJavaParser.parseBodyDeclaration("private int value;").asFieldDeclaration();
        assertThrows(AssertionError.class, () -> typeCollector.generateSetter(field, ""));
    }

    // -------------------- createFactory -------------------- //
    @Test
    void createFactoryCreatesMethodForValidDTOClass() {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TestDTO {}"));
        handler.createFactory();

        MethodDeclaration method = handler.method;
        assertNotNull(method, "Factory method should be created.");
        assertEquals("createTestDTO", method.getNameAsString(), "Factory method name should be 'createTestDTO'.");
        assertEquals("TestDTO", method.getTypeAsString(), "Factory method return type should be 'TestDTO'.");
        assertTrue(method.isPublic(), "Factory method should be public.");
        assertTrue(method.isStatic(), "Factory method should be static.");
    }

    @Test
    void createFactoryDoesNotCreateMethodForNonDTOClass() {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TestClass {}"));
        handler.createFactory();

        assertNull(handler.method, "Factory method should not be created for non-DTO class.");
    }

    @Test
    void createFactoryDoesNotCreateMethodForInterface() {
        handler.setCompilationUnit(StaticJavaParser.parse("public interface TestDTO {}"));
        handler.createFactory();

        assertNull(handler.method, "Factory method should not be created for interface.");
    }

    @Test
    void createFactoryDoesNotCreateMethodForAbstractClass() {
        handler.setCompilationUnit(StaticJavaParser.parse("public abstract class TestDTO {}"));
        handler.createFactory();

        assertNull(handler.method, "Factory method should not be created for abstract class.");
    }

    @Test
    void createFactoryCreatesMethodWithCorrectBody() {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TestDTO {}"));
        handler.createFactory();

        MethodDeclaration method = handler.method;
        BlockStmt body = method.getBody().orElseThrow(() -> new AssertionError("Factory method body should not be empty"));
        assertEquals(1, body.getStatements().size(), "Factory method body should have one statement.");
        AssignExpr assignExpr = (AssignExpr) body.getStatement(0).asExpressionStmt().getExpression();
        assertEquals("TestDTO testDTO", assignExpr.getTarget().toString(), "Factory method should declare and assign 'TestDTO testDTO'.");
        assertEquals("new TestDTO()", assignExpr.getValue().toString(), "Factory method should instantiate 'new TestDTO()'.");
    }
}
