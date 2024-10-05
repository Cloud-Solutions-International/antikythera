package sa.com.cloudsolutions.antikythera.parser;

import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.constants.Constants;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;

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
import java.util.NoSuchElementException;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DTOHandlerTest {
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
//
//    @Test
//    void extractCompleTypes() {
//
//        assertTrue(classProcessor.dependencies.isEmpty());
//        Optional<CompilationUnit> result = handler.javaParser.parse("class Test{}").getResult();
//        if (result.isPresent()) {
//            CompilationUnit cu = result.get();
//            FieldDeclaration field = StaticJavaParser
//                    .parseBodyDeclaration("Map<String, SimpleDTO> someList;")
//                    .asFieldDeclaration();
//            cu.getTypes().get(0).addMember(field);
//            cu.addImport("com.csi.dto.SimpleDTO");
//            handler.setCompilationUnit(cu);
//
//            classProcessor.solveTypeDependencies(field.getElementType(), cu);
//            assertEquals(classProcessor.dependencies.size(), 1);
//
//            // calling the same thing again should not change anything.
//            classProcessor.solveTypeDependencies(field.getElementType(), cu);
//            assertEquals(classProcessor.dependencies.size(), 1);
//        }
//    }

    // -------------------- solveTypes -------------------- //
    @Test
    void solveTypesAddsLombokAnnotationsToClass() throws IOException {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TempClass {}"));

        ClassOrInterfaceDeclaration classDecl = new ClassOrInterfaceDeclaration();
        classDecl.setName("TempClass");
        classDecl.addField("String", "field");

        handler.cu.addType(classDecl);
        handler.removeUnwanted();

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
        handler.removeUnwanted();

        assertTrue(classDecl.getConstructors().isEmpty());
        assertTrue(classDecl.getMethods().isEmpty());
    }

    @Test
    void solveTypesClearsImplementedInterfaces() {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TempClass implements SomeInterface {}"));

        ClassOrInterfaceDeclaration classDecl = new ClassOrInterfaceDeclaration();
        classDecl.setName("TempClass");
        classDecl.addImplementedType("SomeInterface");

        handler.cu.addType(classDecl);
        handler.removeUnwanted();

        assertTrue(classDecl.getImplementedTypes().isEmpty());
    }

    @Test
    void solveTypesHandlesUnresolvedParentClass()  {
        CompilationUnit cu = StaticJavaParser.parse("public class TempClass extends UnresolvedClass {}");
        handler.setCompilationUnit(cu);

        ClassOrInterfaceDeclaration classDecl = cu.getClassByName("TempClass").orElseThrow(() -> new IllegalStateException("Class not found"));
        classDecl.addExtendedType("UnresolvedClass");

        assertThrows(RuntimeException.class, () -> handler.removeUnwanted());
    }

    @Test
    void solveTypesAddsLombokAnnotationsToEnum() {
        handler.setCompilationUnit(StaticJavaParser.parse("public enum TempEnum { VALUE1, VALUE2 }"));

        EnumDeclaration enumDecl = new EnumDeclaration();
        enumDecl.setName("TempEnum");
        enumDecl.addEnumConstant("VALUE1");
        enumDecl.addEnumConstant("VALUE2");
        enumDecl.addAnnotation("AllArgsConstructor");

        handler.cu.addType(enumDecl);
        handler.removeUnwanted();

        assertTrue(handler.cu.getImports().stream().anyMatch(i -> i.getNameAsString().equals("lombok.AllArgsConstructor")));
    }

    @Test
    void solveTypesAddsGetterAnnotationToEnum()  {
        CompilationUnit cu = StaticJavaParser.parse("public enum TempEnum { VALUE1, VALUE2 }");
        handler.setCompilationUnit(cu);

        EnumDeclaration enumDecl = cu.getEnumByName("TempEnum").orElseThrow(() -> new IllegalStateException("Enum not found"));
        enumDecl.addAnnotation("Getter");

        handler.removeUnwanted();

        assertTrue(cu.getImports().stream().anyMatch(importDecl -> importDecl.getNameAsString().equals("lombok.Getter")));
    }

    @Test
    void solveTypesDoesNotAddGetterAnnotationIfAlreadyPresent()  {
        CompilationUnit cu = StaticJavaParser.parse("import lombok.Getter; public enum TempEnum { VALUE1, VALUE2 }");
        handler.setCompilationUnit(cu);

        EnumDeclaration enumDecl = cu.getEnumByName("TempEnum").orElseThrow(() -> new IllegalStateException("Enum not found"));
        enumDecl.addAnnotation("Getter");

        handler.removeUnwanted();

        long getterImports = cu.getImports().stream().filter(importDecl -> importDecl.getNameAsString().equals("lombok.Getter")).count();
        assertEquals(1, getterImports);
    }

    @Test
    void solveTypesHandlesEnumWithoutAnnotations() {
        CompilationUnit cu = StaticJavaParser.parse("public enum TempEnum { VALUE1, VALUE2 }");
        handler.setCompilationUnit(cu);

        handler.removeUnwanted();

        assertFalse(cu.getImports().stream().anyMatch(importDecl -> importDecl.getNameAsString().equals("lombok.Getter")));
    }


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

    @Test
    void copyDTOHandlesInvalidPath() {
        String relativePath = "invalid/path/NonExistentDTO.java";
        assertThrows(FileNotFoundException.class, () -> handler.copyDTO(relativePath));
    }
}
