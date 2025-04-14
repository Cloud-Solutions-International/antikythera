package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.VoidType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import sa.com.cloudsolutions.antikythera.configuration.Settings;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class TestMockingEvaluator {

    private MockingEvaluator mockingEvaluator;
    private MethodDeclaration voidMethod;
    private MethodDeclaration intMethod;
    private CompilationUnit cu;

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
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
        assertEquals(0, result.getValue());
    }

    @Test
    void executeMethodReturnsDefaultForObjectType() throws ReflectiveOperationException {
        MethodDeclaration stringMethod = new MethodDeclaration();
        stringMethod.setType("String");
        stringMethod.setParentNode(cu);

        Variable result = mockingEvaluator.executeMethod(stringMethod);
        assertNotNull(result);
        assertEquals("Ibuprofen", result.getValue());
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
        CompilationUnit cu = new CompilationUnit();
        if (expectedType.contains("util")) {
            cu.addImport("java.util." + typeName);
        }
        methodDecl.setParentNode(cu);

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
        CompilationUnit cu = new CompilationUnit();
        cu.addImport(importName);
        methodDecl.setParentNode(cu);

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

    @ParameterizedTest
    @CsvSource({
        "String,     anyString",
        "int,        anyInt",
        "Integer,    anyInt",
        "long,       anyLong",
        "Long,       anyLong",
        "double,     anyDouble",
        "Double,     anyDouble",
        "boolean,    anyBoolean",
        "Boolean,    anyBoolean",
        "Object,     any"
    })
    void fakeArgumentsCreatesCorrectMatchers(String parameterType, String expectedMatcher) {
        // Setup
        MethodDeclaration methodDecl = new MethodDeclaration();
        Parameter parameter = new Parameter()
                .setType(parameterType)
                .setName("param");
        methodDecl.addParameter(parameter);

        // Execute
        NodeList<Expression> args = MockingEvaluator.fakeArguments(methodDecl);

        // Verify
        assertEquals(1, args.size());
        MethodCallExpr matcher = (MethodCallExpr) args.get(0);
        assertEquals("Mockito", matcher.getScope().get().toString());
        assertEquals(expectedMatcher, matcher.getNameAsString());
    }
}
