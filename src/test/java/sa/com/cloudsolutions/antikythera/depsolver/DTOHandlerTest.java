package sa.com.cloudsolutions.antikythera.depsolver;

import sa.com.cloudsolutions.antikythera.configuration.Settings;
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
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
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
import java.util.NoSuchElementException;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        Settings.loadConfigMap(new File("src/test/resources/generator.yml"));
        basePath = Settings.getProperty(Settings.BASE_PATH).toString();
        controllers = Settings.getProperty(Settings.CONTROLLERS).toString();
        outputPath = Settings.getProperty(Settings.OUTPUT_PATH).toString();

        classProcessor = new ClassProcessor();
        handler = new DTOHandler();
        typeCollector = handler.new TypeCollector();
        handler.setCompilationUnit(new CompilationUnit());
    }

    @Test
    void cleanUpAnnotationsAddsAllAnnotationsForSmallClass() throws IOException {
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

        handler.cleanUpAnnotations(classDecl);

        // this class didn't have lomboks so they should not be there now
        assertFalse(annotations.stream().anyMatch(a -> a.getNameAsString().equals("Getter")));
        assertFalse(annotations.stream().anyMatch(a -> a.getNameAsString().equals("NoArgsConstructor")));
        assertFalse(annotations.stream().anyMatch(a -> a.getNameAsString().equals("AllArgsConstructor")));
        assertFalse(annotations.stream().anyMatch(a -> a.getNameAsString().equals("Setter")));
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

        handler.cleanUpAnnotations(classDecl);

        assertTrue(annotations.isEmpty());
    }

    @Test
    void cleanUpAnnotationsAddsAnnotationsForNonStaticNonFinalFields() throws IOException {

        // Create a temporary Java file with no fields
        Path tempFilePath = Files.createTempFile("TempClass", ".java");
        Files.write(tempFilePath, """
            @Getter
            @Setter
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

        handler.cleanUpAnnotations(classDecl);
        NodeList<AnnotationExpr> annotations = cu.getType(0).getAnnotations();
        assertTrue(annotations.stream().anyMatch(a -> a.getNameAsString().equals("Getter")));
        assertFalse(annotations.stream().anyMatch(a -> a.getNameAsString().equals("NoArgsConstructor")));
        assertFalse(annotations.stream().anyMatch(a -> a.getNameAsString().equals("AllArgsConstructor")));
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

    @Test
    void solveTypesRemovesConstructorsAndMethods() throws IOException {
        handler.setCompilationUnit(StaticJavaParser.parse("public class TempClass {}"));

        ClassOrInterfaceDeclaration classDecl = new ClassOrInterfaceDeclaration();
        classDecl.setName("TempClass");
        classDecl.addConstructor();
        classDecl.addMethod("someMethod");

        handler.getCompilationUnit().addType(classDecl);
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

        handler.getCompilationUnit().addType(classDecl);
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

        handler.getCompilationUnit().addType(enumDecl);
        handler.removeUnwanted();

        assertTrue(handler.getCompilationUnit().getImports().stream().anyMatch(i -> i.getNameAsString().equals("lombok.AllArgsConstructor")));
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
        ClassOrInterfaceDeclaration classDeclaration = handler.getCompilationUnit().addClass("TestClass");

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "String"), "name"));
        field.setParentNode(classDeclaration);

        classDeclaration.addMember(field);

        typeCollector.generateGetter(field, "getName");

        MethodDeclaration getter = (MethodDeclaration) handler.getCompilationUnit().getType(0).getMembers().stream()
                .filter(member -> member instanceof MethodDeclaration)
                .filter(member -> ((MethodDeclaration) member).getNameAsString().equals("getName"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Getter method not found"));

        assertTrue(getter.isPublic(), "Generated getter should be public.");
    }

    @Test
    void generateGetterReturnsFieldValue() {
        ClassOrInterfaceDeclaration classDeclaration = handler.getCompilationUnit().addClass("TestClass");

        FieldDeclaration field = new FieldDeclaration();
        field.addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, "String"), "name"));
        field.setParentNode(classDeclaration);

        classDeclaration.addMember(field);

        typeCollector.generateGetter(field, "getName");

        MethodDeclaration getter = (MethodDeclaration) handler.getCompilationUnit().getType(0).getMembers().stream()
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
        ClassOrInterfaceDeclaration classDeclaration = handler.getCompilationUnit().addClass("TestClass");

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
