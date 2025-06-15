package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.VoidType;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import sa.com.cloudsolutions.antikythera.configuration.Settings;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TestMockingEvaluator {

    public static final String FAKE_ENTITY = "sa.com.cloudsolutions.antikythera.evaluator.FakeEntity";
    public static final String FAKE_REPOSITORY = "sa.com.cloudsolutions.antikythera.evaluator.FakeRepository";
    public static final String FAKE_SERVICE = "sa.com.cloudsolutions.antikythera.evaluator.FakeService";
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
        assertEquals(0, result.getValue());
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
    void testRepositorySaveMethodCreatesEvaluator() throws ReflectiveOperationException {
        MockingRegistry.markAsMocked(FAKE_REPOSITORY);
        SpringEvaluator eval = EvaluatorFactory.create(FAKE_SERVICE, SpringEvaluator.class);

        MethodDeclaration md = eval.getCompilationUnit().findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals("saveFakeData")).orElseThrow();

        Variable v = eval.executeMethod(md);
        assertNotNull(v);
        assertNotNull(v.getValue());
        assertInstanceOf(Evaluator.class, v.getValue());
    }


    @Test
    void testSearchFakeData() throws ReflectiveOperationException {
        MockingRegistry.markAsMocked(FAKE_REPOSITORY);
        SpringEvaluator eval = EvaluatorFactory.create(
                FAKE_SERVICE, SpringEvaluator.class);

        MethodDeclaration md = eval.getCompilationUnit().findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals("searchFakeData")).orElseThrow();

        Variable v = eval.executeMethod(md);
        assertNotNull(v);
        assertNotNull(v.getValue());
        assertInstanceOf(ArrayList.class, v.getValue());
    }

    @Test
    void testResolveExpressionHelperWithFakeEntity() {
        // Get TypeDeclaration for FakeEntity from AntikytheraRunTime
        TypeDeclaration<?> fakeEntityType = AntikytheraRunTime.getTypeDeclaration(
                FAKE_ENTITY).orElseThrow();

        // Create TypeWrapper with the TypeDeclaration
        TypeWrapper wrapper = new TypeWrapper(fakeEntityType);

        // Execute the method under test
        Variable result = mockingEvaluator.resolveExpressionHelper(wrapper);

        // Verify the result
        assertNotNull(result);
        assertNotNull(result.getValue());
        assertInstanceOf(Evaluator.class, result.getValue());
        assertEquals(FAKE_ENTITY,
                ((Evaluator)result.getValue()).getClassName());
    }

    @Test
    void testRepositoryEmptyPath() {
        // Test List type
        Variable listResult = mockingEvaluator.repositoryEmptyPath("java.util.List");
        assertNotNull(listResult);
        assertNotNull(listResult.getValue());
        assertInstanceOf(List.class, listResult.getValue());
        assertEquals(0, ((List<?>) listResult.getValue()).size());

        // Test ArrayList type
        Variable arrayListResult = mockingEvaluator.repositoryEmptyPath("java.util.ArrayList");
        assertNotNull(arrayListResult);
        assertNotNull(arrayListResult.getValue());
        assertInstanceOf(List.class, arrayListResult.getValue());
        assertEquals(0, ((List<?>) arrayListResult.getValue()).size());

        // Test LinkedList type
        Variable linkedListResult = mockingEvaluator.repositoryEmptyPath("java.util.LinkedList");
        assertNotNull(linkedListResult);
        assertNotNull(linkedListResult.getValue());
        assertInstanceOf(List.class, linkedListResult.getValue());
        assertEquals(0, ((List<?>) linkedListResult.getValue()).size());
    }

    @Test
    void testOptionalByteBuddy() throws ReflectiveOperationException {
        cu = AntikytheraRunTime.getCompilationUnit(FAKE_ENTITY);
        assertNotNull(cu);

        mockingEvaluator = EvaluatorFactory.create(FAKE_SERVICE, MockingEvaluator.class);

        Variable result = mockingEvaluator.optionalByteBuddy(FAKE_ENTITY);

        // Verify
        assertNotNull(result);
        assertNotNull(result.getValue());
        assertInstanceOf(Optional.class, result.getValue());
        Optional<?> optional = (Optional<?>) result.getValue();
        assertTrue(optional.isPresent());
        Object value = optional.get();
        assertTrue(value.getClass().getName().contains("FakeEntity"));
        assertFalse(value.getClass().getName().contains("ByteBuddy"));
    }

    @Test
    void testGetIdField() {
        // Test with default mockingEvaluator (should return null)
        Variable result1 = mockingEvaluator.getIdField();
        assertNull(result1);

        // Test with FakeEntity (should return non-null)
        mockingEvaluator = EvaluatorFactory.create(FAKE_ENTITY, MockingEvaluator.class);

        Variable result2 = mockingEvaluator.getIdField();
        assertNotNull(result2);
        assertNotNull(result2.getValue());
    }

}
