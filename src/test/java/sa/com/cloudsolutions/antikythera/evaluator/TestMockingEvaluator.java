package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.VoidType;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.parser.Callable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import sa.com.cloudsolutions.antikythera.configuration.Settings;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TestMockingEvaluator {

    private MockingEvaluator mockingEvaluator;
    private MethodDeclaration voidMethod;
    private MethodDeclaration intMethod;
    private CompilationUnit cu;

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void setUp() {
        mockingEvaluator = EvaluatorFactory.create("", MockingEvaluator.class);
        mockingEvaluator.setVariableName("Bada");
        cu = new CompilationUnit();
        voidMethod = new MethodDeclaration();
        voidMethod.setType(new VoidType());
        intMethod = new MethodDeclaration();
        intMethod.setType(PrimitiveType.intType());
    }

    @Test
    void executeMethodReturnsNullForVoidType() throws ReflectiveOperationException {
        assertNull(mockingEvaluator.executeMethod(voidMethod));
    }

    @Test
    void executeMethodReturnsDefaultForPrimitiveType() throws ReflectiveOperationException {
        Variable result = mockingEvaluator.executeMethod(intMethod);
        assertNotNull(result);
        assertEquals(1, result.getValue());
    }

    @Test
    void executeMethodReturnsDefaultForObjectType() throws ReflectiveOperationException {
        MethodDeclaration stringMethod = new MethodDeclaration();
        stringMethod.setType("String");
        stringMethod.setParentNode(cu);

        Variable result = mockingEvaluator.executeMethod(stringMethod);
        assertNotNull(result);
        assertEquals("Antikythera", result.getValue());
    }

    @Test
    void executeMethodReturnsNullForUnknownType() throws ReflectiveOperationException {
        MethodDeclaration unknownMethod = new MethodDeclaration();
        unknownMethod.setType("UnknownType");
        unknownMethod.setParentNode(cu);

        Variable result = mockingEvaluator.executeMethod(unknownMethod);
        assertNull(result.getValue());
    }

    @ParameterizedTest
    @CsvSource({
        "List,          java.util.ArrayList",
        "ArrayList,     java.util.ArrayList",
        "Map,           java.util.HashMap",
        "HashMap,       java.util.HashMap",
        "TreeMap,       java.util.TreeMap",
        "Set,           java.util.HashSet",
        "HashSet,       java.util.HashSet",
        "TreeSet,       java.util.TreeSet",
        "Optional,      java.util.Optional",
        "String,        java.lang.String",
        "Integer,       java.lang.Integer",
        "Long,          java.lang.Long",
        "Double,        java.lang.Double",
        "Boolean,       java.lang.Boolean"
    })
    void mockReturnFromCompilationUnitHandlesDifferentTypes(String typeName, String expectedType) {
        // Setup
        MethodDeclaration methodDecl = new MethodDeclaration();
        CompilationUnit compUnit = new CompilationUnit();
        if (expectedType.contains("util")) {
            compUnit.addImport("java.util." + typeName);
        }
        methodDecl.setParentNode(compUnit);

        ClassOrInterfaceType returnType = new ClassOrInterfaceType()
                .setName(typeName);
        methodDecl.setType(returnType);

        // Execute
        Variable result = mockingEvaluator.mockReturnFromCompilationUnit(methodDecl, methodDecl, returnType);

        // Verify
        assertNotNull(result);
        assertNotNull(result.getValue(), "Value should not be null for type: " + typeName);
        assertTrue(result.getValue().getClass().getName().contains(expectedType),
                "Expected " + expectedType + " but got " + result.getValue().getClass().getName());
    }

    @ParameterizedTest
    @CsvSource({
        "List<String>,          java.util.List",
        "Map<String$Integer>,   java.util.Map",  // Using $ as separator
        "Set<Long>,             java.util.Set",
        "Optional<Double>,      java.util.Optional"
    })
    void mockReturnFromCompilationUnitHandlesGenericTypes(String genericType, String importName) {
        // Setup
        MethodDeclaration methodDecl = new MethodDeclaration();
        CompilationUnit compUnit = new CompilationUnit();
        compUnit.addImport(importName);
        methodDecl.setParentNode(compUnit);

        ClassOrInterfaceType returnType = parseGenericType(genericType);
        methodDecl.setType(returnType);

        // Execute
        Variable result = mockingEvaluator.mockReturnFromCompilationUnit(methodDecl, methodDecl, returnType);

        // Verify
        assertNotNull(result);
    }

    private ClassOrInterfaceType parseGenericType(String genericType) {
        // Simple parser for generic types like "List<String>"
        String[] parts = genericType.split("[<,>]");
        ClassOrInterfaceType type = new ClassOrInterfaceType().setName(parts[0]);

        if (parts.length > 1) {
            NodeList<Type> typeArgs = new NodeList<>();
            for (int i = 1; i < parts.length; i++) {
                if (!parts[i].trim().isEmpty()) {
                    typeArgs.add(new ClassOrInterfaceType().setName(parts[i].trim()));
                }
            }
            type.setTypeArguments(typeArgs);
        }
        return type;
    }

    @Test
    void executeMethodHandlesReflectiveMethod() throws NoSuchMethodException {
        // Setup
        class TestClass {
            public String testMethod(int param1, String param2) {
                return param1 + param2;
            }
        }
        Method method = TestClass.class.getMethod("testMethod", int.class, String.class);

        // Execute
        Variable result = mockingEvaluator.executeMethod(method);

        // Verify
        assertNotNull(result);
        assertEquals("Antikythera", result.getValue());
    }

    @Test
    void testRepositorySaveMethodCreatesEvaluator() throws ReflectiveOperationException, IOException {
        MockingRegistry.markAsMocked("sa.com.cloudsolutions.antikythera.evaluator.FakeRepository");
        AbstractCompiler.preProcess();
        SpringEvaluator eval = EvaluatorFactory.create(
                "sa.com.cloudsolutions.antikythera.evaluator.FakeService", SpringEvaluator.class);

        MethodDeclaration md = eval.getCompilationUnit().findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals("saveFakeData")).orElseThrow();

        Variable v = eval.executeMethod(md);
        assertNotNull(v);
        assertNotNull(v.getValue());
        assertInstanceOf(Evaluator.class, v.getValue());
    }
}
